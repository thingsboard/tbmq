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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.common.stats.DefaultStatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.integration.api.stats.IntegrationProcessorStats;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.INTEGRATION_ID_TAG;

class DefaultIntegrationStatisticsServiceTest {

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

    /**
     * The two per-integration stat streams differ only by which create/clear pair feeds them and the
     * metric name they register under, but both flow through the same {@code printProcessorStats}
     * cleanup — so the behavioural tests below are parameterized over both to keep them in lockstep.
     */
    private enum StatStream {
        MESSAGE(StatsType.INTEGRATION_PROCESSOR.getPrintName()) {
            @Override
            IntegrationProcessorStats create(DefaultIntegrationStatisticsService service, UUID integrationId) {
                return service.createIntegrationProcessorStats(integrationId);
            }

            @Override
            void clear(DefaultIntegrationStatisticsService service, UUID integrationId) {
                service.clearIntegrationProcessorStats(integrationId);
            }
        },
        EVENT(StatsType.INTEGRATION_EVENT_PROCESSOR.getPrintName()) {
            @Override
            IntegrationProcessorStats create(DefaultIntegrationStatisticsService service, UUID integrationId) {
                return service.createIntegrationEventProcessorStats(integrationId);
            }

            @Override
            void clear(DefaultIntegrationStatisticsService service, UUID integrationId) {
                service.clearIntegrationEventProcessorStats(integrationId);
            }
        };

        final String key;

        StatStream(String key) {
            this.key = key;
        }

        abstract IntegrationProcessorStats create(DefaultIntegrationStatisticsService service, UUID integrationId);

        abstract void clear(DefaultIntegrationStatisticsService service, UUID integrationId);
    }

    private int counters(String key, UUID integrationId) {
        return meterRegistry.find(key).tag(INTEGRATION_ID_TAG, integrationId.toString()).counters().size();
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, IntegrationProcessorStats> managedProcessorStats() {
        return (Map<UUID, IntegrationProcessorStats>) ReflectionTestUtils.getField(service, "managedIntegrationProcessorStats");
    }

    @ParameterizedTest
    @EnumSource(StatStream.class)
    void givenProcessorStats_whenClearedAndPrinted_thenPerIntegrationCountersRemovedFromRegistry(StatStream stream) {
        UUID integrationId = UUID.randomUUID();
        IntegrationProcessorStats created = stream.create(service, integrationId);
        int registeredCounters = created.getStatsCounters().size();
        assertThat(counters(stream.key, integrationId)).isEqualTo(registeredCounters);

        stream.clear(service, integrationId);
        service.printStats();

        assertThat(counters(stream.key, integrationId)).isZero();
    }

    @ParameterizedTest
    @EnumSource(StatStream.class)
    void givenActiveProcessorStats_whenPrinted_thenCountersRetained(StatStream stream) {
        UUID integrationId = UUID.randomUUID();
        IntegrationProcessorStats created = stream.create(service, integrationId);
        int registeredCounters = created.getStatsCounters().size();

        // No clear -> stats stay active -> printStats resets the local counts but must NOT deregister.
        service.printStats();

        assertThat(counters(stream.key, integrationId)).isEqualTo(registeredCounters);
    }

    @ParameterizedTest
    @EnumSource(StatStream.class)
    void givenTwoIntegrations_whenOneCleared_thenOnlyThatIntegrationsCountersRemoved(StatStream stream) {
        UUID cleared = UUID.randomUUID();
        UUID retained = UUID.randomUUID();
        stream.create(service, cleared);
        IntegrationProcessorStats retainedStats = stream.create(service, retained);

        stream.clear(service, cleared);
        service.printStats();

        assertThat(counters(stream.key, cleared)).isZero();
        assertThat(counters(stream.key, retained)).isEqualTo(retainedStats.getStatsCounters().size());
    }

    @Test
    void givenSameIdReEnabledBetweenSnapshotAndRemap_whenPrinted_thenCountersRetained() {
        UUID integrationId = UUID.randomUUID();
        // Register the real per-integration counters for this id.
        IntegrationProcessorStats registered = service.createIntegrationProcessorStats(integrationId);
        int registeredCounters = registered.getStatsCounters().size();
        assertThat(counters(StatStream.MESSAGE.key, integrationId)).isEqualTo(registeredCounters);

        // Simulate a concurrent re-enable landing between printStats' values() snapshot and the remap: the
        // entry reports inactive on the snapshot-loop check (so cleanup runs), but by the time the remap
        // re-reads it the entry is active again, so its meters must be kept, not deregistered.
        IntegrationProcessorStats reEnabling = mock(IntegrationProcessorStats.class);
        when(reEnabling.getIntegrationUuid()).thenReturn(integrationId);
        // isActive() is read twice per entry: first the snapshot-loop check in printProcessorStats, then the
        // remap re-read in removeInactiveStats. Returning false then true drives the "inactive at snapshot,
        // active at remap" branch — keep this call count in sync with printProcessorStats.
        when(reEnabling.isActive()).thenReturn(false, true);
        when(reEnabling.getStatsCounters()).thenReturn(registered.getStatsCounters());
        managedProcessorStats().put(integrationId, reEnabling);

        service.printStats();

        assertThat(counters(StatStream.MESSAGE.key, integrationId)).isEqualTo(registeredCounters);
    }
}
