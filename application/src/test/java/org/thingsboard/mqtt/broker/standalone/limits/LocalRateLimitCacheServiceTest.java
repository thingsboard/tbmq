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

import io.github.bucket4j.BucketConfiguration;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.config.ClientsLimitProperties;
import org.thingsboard.mqtt.broker.config.DevicePersistedMsgsRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.config.TotalMsgsRateLimitsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalRateLimitCacheServiceTest {

    private final ClientsLimitProperties clientsLimit = new ClientsLimitProperties();
    private final DevicePersistedMsgsRateLimitsConfiguration deviceConfig = new DevicePersistedMsgsRateLimitsConfiguration();
    private final TotalMsgsRateLimitsConfiguration totalConfig = new TotalMsgsRateLimitsConfiguration();

    private LocalRateLimitCacheService service;

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

}
