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
package org.thingsboard.mqtt.broker.standalone.pipeline;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.stats.DefaultStatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.service.stats.DefaultPublishMsgConsumerStats;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class StandaloneDispatchStatsTest {

    private static final String CONSUMER_ID = "dispatch-0";
    private static final String KEY = "incomingPublishMsg.consumed";

    @Test
    void givenSameConsumerId_whenCreated_thenMetersMatchTheClusterOnesByNameAndTagsMinusTheKafkaOnlyOnes() {
        SimpleMeterRegistry standaloneRegistry = new SimpleMeterRegistry();
        new StandaloneDispatchStats(CONSUMER_ID, new DefaultStatsFactory(standaloneRegistry));
        SimpleMeterRegistry clusterRegistry = new SimpleMeterRegistry();
        new DefaultPublishMsgConsumerStats(CONSUMER_ID, new DefaultStatsFactory(clusterRegistry));

        Set<String> standalone = describe(standaloneRegistry);
        Set<String> cluster = describe(clusterRegistry);

        assertThat(standalone).hasSize(4).isSubsetOf(cluster);
        assertThat(standalone).containsExactlyInAnyOrder(
                KEY + "{consumerId=dispatch-0, statsName=totalMsgs}",
                KEY + "{consumerId=dispatch-0, statsName=successfulMsgs}",
                KEY + "{consumerId=dispatch-0, statsName=failedMsgs}",
                KEY + ".processing.time{consumerId=dispatch-0}");
    }

    @Test
    void givenEvents_whenReported_thenEachMeterMovesOnItsOwn() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StatsFactory statsFactory = new DefaultStatsFactory(registry);
        StandaloneDispatchStats stats = new StandaloneDispatchStats(CONSUMER_ID, statsFactory);

        stats.onDispatched();
        stats.onDispatched();
        stats.onDispatched();
        stats.onSuccess();
        stats.onSuccess();
        stats.onFailure();
        stats.logMsgProcessingTime(5, TimeUnit.MILLISECONDS);

        assertThat(counter(registry, "totalMsgs")).isEqualTo(3);
        assertThat(counter(registry, "successfulMsgs")).isEqualTo(2);
        assertThat(counter(registry, "failedMsgs")).isEqualTo(1);
        assertThat(registry.get(KEY + ".processing.time").tag("consumerId", CONSUMER_ID).timer().count()).isEqualTo(1);
    }

    private static double counter(SimpleMeterRegistry registry, String statsName) {
        return registry.get(KEY).tag("consumerId", CONSUMER_ID).tag("statsName", statsName).counter().count();
    }

    private static Set<String> describe(SimpleMeterRegistry registry) {
        return registry.getMeters().stream()
                .map(Meter::getId)
                .filter(id -> id.getName().startsWith(KEY))
                .map(id -> id.getName() + id.getTags().stream().map(tag -> tag.getKey() + "=" + tag.getValue())
                        .collect(Collectors.joining(", ", "{", "}")))
                .collect(Collectors.toSet());
    }

}
