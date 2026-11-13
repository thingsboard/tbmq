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
package org.thingsboard.mqtt.broker.service.auth;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.stats.DefaultStatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.standalone.auth.StandaloneClientTypePolicy;

import static org.assertj.core.api.Assertions.assertThat;

class ClientTypePolicyTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final StatsFactory statsFactory = new DefaultStatsFactory(registry);

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger(StandaloneClientTypePolicy.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setAdditive(false);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(appender);
        logger.setAdditive(true);
    }

    @Test
    void givenClusterPolicy_whenResolve_thenReturnsAuthenticatedTypeUnchanged() {
        ClientTypePolicy policy = new ClusterClientTypePolicy();

        assertThat(policy.resolve("client", ClientType.APPLICATION)).isEqualTo(ClientType.APPLICATION);
        assertThat(policy.resolve("client", ClientType.DEVICE)).isEqualTo(ClientType.DEVICE);
    }

    @Test
    void givenStandalonePolicy_whenResolveApplication_thenDeviceWithWarnAndCounter() {
        ClientTypePolicy policy = new StandaloneClientTypePolicy(statsFactory);

        assertThat(policy.resolve("client", ClientType.APPLICATION)).isEqualTo(ClientType.DEVICE);

        assertThat(counter()).isEqualTo(1);
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .isEqualTo("[client] APPLICATION clients are not supported in TBMQ Lite; connecting as DEVICE");
        });
    }

    @Test
    void givenStandalonePolicy_whenResolveApplicationTwice_thenCounterCountsEachConnect() {
        ClientTypePolicy policy = new StandaloneClientTypePolicy(statsFactory);

        policy.resolve("a", ClientType.APPLICATION);
        policy.resolve("b", ClientType.APPLICATION);

        assertThat(counter()).isEqualTo(2);
    }

    @Test
    void givenStandalonePolicy_whenResolveDevice_thenDeviceSilentlyAndNoCount() {
        ClientTypePolicy policy = new StandaloneClientTypePolicy(statsFactory);

        assertThat(policy.resolve("client", ClientType.DEVICE)).isEqualTo(ClientType.DEVICE);

        assertThat(counter()).isZero();
        assertThat(appender.list).isEmpty();
    }

    private double counter() {
        return registry.get(StatsType.APPLICATION_CLIENTS_MAPPED_TO_DEVICE.getPrintName()).counter().count();
    }

}
