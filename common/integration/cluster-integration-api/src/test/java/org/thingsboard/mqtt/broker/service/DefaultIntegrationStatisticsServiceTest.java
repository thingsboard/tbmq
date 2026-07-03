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
package org.thingsboard.mqtt.broker.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.stats.DefaultStatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.INTEGRATION_ID_TAG;

class DefaultIntegrationStatisticsServiceTest {

    private static final String INTEGRATION_PROCESSOR = StatsType.INTEGRATION_PROCESSOR.getPrintName();
    private static final String INTEGRATION_EVENT_PROCESSOR = StatsType.INTEGRATION_EVENT_PROCESSOR.getPrintName();

    private SimpleMeterRegistry meterRegistry;
    private DefaultIntegrationStatisticsService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        StatsFactory statsFactory = new DefaultStatsFactory(meterRegistry);
        service = new DefaultIntegrationStatisticsService(statsFactory);
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    private int counters(String key, UUID integrationId) {
        return meterRegistry.find(key).tag(INTEGRATION_ID_TAG, integrationId.toString()).counters().size();
    }

    @Test
    void givenIntegrationProcessorStats_whenClearedAndPrinted_thenPerIntegrationCountersRemovedFromRegistry() {
        UUID integrationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        service.createIntegrationProcessorStats(integrationId);
        assertThat(counters(INTEGRATION_PROCESSOR, integrationId)).isEqualTo(8);

        service.clearIntegrationProcessorStats(integrationId);
        service.printStats();

        assertThat(counters(INTEGRATION_PROCESSOR, integrationId)).isZero();
    }

    @Test
    void givenIntegrationEventProcessorStats_whenClearedAndPrinted_thenPerIntegrationCountersRemovedFromRegistry() {
        UUID integrationId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        service.createIntegrationEventProcessorStats(integrationId);
        assertThat(counters(INTEGRATION_EVENT_PROCESSOR, integrationId)).isEqualTo(8);

        service.clearIntegrationEventProcessorStats(integrationId);
        service.printStats();

        assertThat(counters(INTEGRATION_EVENT_PROCESSOR, integrationId)).isZero();
    }

    @Test
    void givenActiveIntegrationProcessorStats_whenPrinted_thenCountersRetained() {
        UUID integrationId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        service.createIntegrationProcessorStats(integrationId);

        // No clear -> stats stay active -> printStats resets the local counts but must NOT deregister.
        service.printStats();

        assertThat(counters(INTEGRATION_PROCESSOR, integrationId)).isEqualTo(8);
    }

    @Test
    void givenTwoIntegrations_whenOneCleared_thenOnlyThatIntegrationsCountersRemoved() {
        UUID cleared = UUID.fromString("00000000-0000-0000-0000-000000000004");
        UUID retained = UUID.fromString("00000000-0000-0000-0000-000000000005");
        service.createIntegrationProcessorStats(cleared);
        service.createIntegrationProcessorStats(retained);

        service.clearIntegrationProcessorStats(cleared);
        service.printStats();

        assertThat(counters(INTEGRATION_PROCESSOR, cleared)).isZero();
        assertThat(counters(INTEGRATION_PROCESSOR, retained)).isEqualTo(8);
    }
}
