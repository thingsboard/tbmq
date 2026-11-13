/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.standalone.limits;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.bucket4j.BucketConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.thingsboard.mqtt.broker.config.ClientsLimitProperties;
import org.thingsboard.mqtt.broker.config.DevicePersistedMsgsRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.config.TotalMsgsRateLimitsConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalRateLimitCacheServiceTest {

    private final ClientsLimitProperties clientsLimit = new ClientsLimitProperties();
    private final DevicePersistedMsgsRateLimitsConfiguration deviceConfig = new DevicePersistedMsgsRateLimitsConfiguration();
    private final TotalMsgsRateLimitsConfiguration totalConfig = new TotalMsgsRateLimitsConfiguration();

    private final Logger logger = (Logger) LoggerFactory.getLogger(LocalRateLimitCacheService.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LocalRateLimitCacheService service;

    @BeforeEach
    void attachAppender() {
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    private LocalRateLimitCacheService startService() {
        service = new LocalRateLimitCacheService(
                deviceConfig.isEnabled() ? deviceConfig.devicePersistedMsgsBucketConfiguration() : null,
                totalConfig.isEnabled() ? totalConfig.totalMsgsBucketConfiguration() : null,
                clientsLimit, deviceConfig, totalConfig);
        service.init();
        return service;
    }

    private void enableDeviceLimit(String config) {
        deviceConfig.setEnabled(true);
        deviceConfig.setConfig(config);
    }

    private void enableTotalLimit(String config) {
        totalConfig.setEnabled(true);
        totalConfig.setConfig(config);
    }

    @Test
    void theSessionCounterStartsFromTheLoadedSessions() {
        clientsLimit.setSessionsLimit(100);
        startService();

        service.initSessionCount(3);
        assertThat(service.incrementSessionCount()).isEqualTo(4);
        service.decrementSessionCount();
        assertThat(service.incrementSessionCount()).isEqualTo(4);
    }

    @Test
    void theApplicationClientsCounterStartsFromTheLoadedClients() {
        clientsLimit.setApplicationClientsLimit(100);
        startService();

        service.initApplicationClientsCount(5);
        assertThat(service.incrementApplicationClientsCount()).isEqualTo(6);
        service.decrementApplicationClientsCount();
        assertThat(service.incrementApplicationClientsCount()).isEqualTo(6);
    }

    @Test
    void decrementDoesNothingWhileTheLimitIsOff() {
        startService();

        service.initSessionCount(3);
        service.decrementSessionCount();
        assertThat(service.incrementSessionCount()).isEqualTo(4);

        service.initApplicationClientsCount(3);
        service.decrementApplicationClientsCount();
        assertThat(service.incrementApplicationClientsCount()).isEqualTo(4);
    }

    @Test
    void theBucketGrantsWhatIsLeftAndThenNothing() {
        enableDeviceLimit("10:60");
        startService();

        assertThat(service.tryConsumeDevicePersistedMsgs(7)).isEqualTo(7);
        assertThat(service.tryConsumeDevicePersistedMsgs(5)).isEqualTo(3);
        assertThat(service.tryConsumeDevicePersistedMsgs(5)).isZero();
    }

    @Test
    void everyBandwidthOfTheConfigurationApplies() {
        enableTotalLimit("10:60,4:1");
        startService();

        assertThat(service.tryConsumeTotalMsgs(10)).isEqualTo(4);
    }

    @Test
    void theTwoBucketsAreIndependent() {
        enableDeviceLimit("10:60");
        enableTotalLimit("10:60");
        startService();

        assertThat(service.tryConsumeDevicePersistedMsgs(10)).isEqualTo(10);
        assertThat(service.tryConsumeTotalMsgs(10)).isEqualTo(10);
    }

    @Test
    void returnedTokensCanBeConsumedAgain() {
        enableTotalLimit("10:60");
        startService();

        service.tryConsumeTotalMsgs(10);
        service.returnTotalMsgs(4);
        assertThat(service.tryConsumeTotalMsgs(10)).isEqualTo(4);
    }

    @Test
    void returningNonPositiveTokensChangesNothing() {
        enableTotalLimit("10:60");
        startService();

        service.tryConsumeTotalMsgs(10);
        service.returnTotalMsgs(0);
        service.returnTotalMsgs(-5);
        assertThat(service.tryConsumeTotalMsgs(10)).isZero();
    }

    @Test
    void returningTokensWhileTheLimitIsOffIsIgnored() {
        startService();

        service.returnTotalMsgs(4);
    }

    @Test
    void anEnabledDeviceLimitWithoutAConfigurationFailsStartup() {
        deviceConfig.setEnabled(true);
        service = new LocalRateLimitCacheService(null, null, clientsLimit, deviceConfig, totalConfig);

        assertThatThrownBy(service::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mqtt.rate-limits.device-persisted-messages.enabled")
                .hasMessageContaining("devicePersistedMsgsBucketConfiguration");
    }

    @Test
    void anEnabledTotalLimitWithoutAConfigurationFailsStartup() {
        totalConfig.setEnabled(true);
        service = new LocalRateLimitCacheService(null, null, clientsLimit, deviceConfig, totalConfig);

        assertThatThrownBy(service::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mqtt.rate-limits.total.enabled")
                .hasMessageContaining("totalMsgsBucketConfiguration");
    }

    @Test
    void aNonPositiveRequestConsumesNothing() {
        enableTotalLimit("10:60");
        enableDeviceLimit("10:60");
        startService();

        assertThat(service.tryConsumeTotalMsgs(0)).isZero();
        assertThat(service.tryConsumeTotalMsgs(-1)).isZero();
        assertThat(service.tryConsumeDevicePersistedMsgs(0)).isZero();
        assertThat(service.tryConsumeDevicePersistedMsgs(-1)).isZero();
        assertThat(service.tryConsumeTotalMsgs(10)).isEqualTo(10);
    }

    @Test
    void theQualifierNamesMatchTheConfigurationBeanMethods() throws NoSuchMethodException {
        assertThat(DevicePersistedMsgsRateLimitsConfiguration.class
                .getMethod(LocalRateLimitCacheService.DEVICE_PERSISTED_MSGS_BUCKET_CONFIGURATION_BEAN).getReturnType())
                .isEqualTo(BucketConfiguration.class);
        assertThat(TotalMsgsRateLimitsConfiguration.class
                .getMethod(LocalRateLimitCacheService.TOTAL_MSGS_BUCKET_CONFIGURATION_BEAN).getReturnType())
                .isEqualTo(BucketConfiguration.class);
    }

    @Test
    void theCounterInitIsLoggedOnlyWhileTheRespectiveLimitIsOn() {
        startService();
        service.initSessionCount(3);
        service.initApplicationClientsCount(5);
        assertThat(initLogLines()).isEmpty();

        clientsLimit.setSessionsLimit(100);
        service.initSessionCount(3);
        assertThat(initLogLines()).singleElement().asString().contains("Client session count");

        clientsLimit.setApplicationClientsLimit(100);
        service.initApplicationClientsCount(5);
        assertThat(initLogLines()).hasSize(2).last().asString().contains("Application clients count");
    }

    private List<String> initLogLines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains("is initialized with")).toList();
    }

}
