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

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelinePropertiesTest {

    @Test
    void givenYamlKeys_whenBind_thenAllSettingsAreRead() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.ofEntries(
                Map.entry("standalone.dispatch.partitions", "3"),
                Map.entry("standalone.persist.partitions", "5"),
                Map.entry("standalone.persist.batch-size", "64"),
                Map.entry("standalone.persist.partition-queue-capacity", "128"),
                Map.entry("standalone.persist.max-concurrent-replays", "16"),
                Map.entry("standalone.pipeline.max-bytes", "1000"),
                Map.entry("standalone.pipeline.max-messages", "200"),
                Map.entry("standalone.pipeline.pause-percent", "70"),
                Map.entry("standalone.pipeline.resume-percent", "40"),
                Map.entry("standalone.shutdown.drain-timeout-ms", "1500"),
                Map.entry("standalone.service-id", "node")));

        PipelineProperties properties = new Binder(source).bind("standalone", PipelineProperties.class).get();

        assertThat(properties.effectiveDispatchPartitions()).isEqualTo(3);
        assertThat(properties.effectivePersistPartitions()).isEqualTo(5);
        assertThat(properties.getPersist().getBatchSize()).isEqualTo(64);
        assertThat(properties.getPersist().getPartitionQueueCapacity()).isEqualTo(128);
        assertThat(properties.effectiveMaxBytes()).isEqualTo(1000);
        assertThat(properties.getPipeline().getMaxMessages()).isEqualTo(200);
        assertThat(properties.getPipeline().getPausePercent()).isEqualTo(70);
        assertThat(properties.getPipeline().getResumePercent()).isEqualTo(40);
        assertThat(properties.getShutdown().getDrainTimeoutMs()).isEqualTo(1500);
    }

    @Test
    void givenDefaults_whenRead_thenTheyMatchTheYamlDefaults() {
        PipelineProperties properties = new PipelineProperties();

        assertThat(properties.getDispatch().getPartitions()).isZero();
        assertThat(properties.getPersist().getPartitions()).isZero();
        assertThat(properties.getPersist().getBatchSize()).isEqualTo(256);
        assertThat(properties.getPersist().getPartitionQueueCapacity()).isEqualTo(4096);
        assertThat(properties.getPipeline().getMaxBytes()).isZero();
        assertThat(properties.getPipeline().getMaxMessages()).isEqualTo(100_000);
        assertThat(properties.getPipeline().getPausePercent()).isEqualTo(80);
        assertThat(properties.getPipeline().getResumePercent()).isEqualTo(50);
        assertThat(properties.getShutdown().getDrainTimeoutMs()).isEqualTo(4000);
    }

    @Test
    void givenZeroPartitions_whenResolve_thenAvailableProcessorsButAtLeastTwo() {
        PipelineProperties properties = new PipelineProperties();

        int expected = Math.max(2, Runtime.getRuntime().availableProcessors());
        assertThat(properties.effectiveDispatchPartitions()).isEqualTo(expected);
        assertThat(properties.effectivePersistPartitions()).isEqualTo(expected);
    }

    @Test
    void givenNegativePartitions_whenResolve_thenRejected() {
        PipelineProperties properties = new PipelineProperties();
        properties.getDispatch().setPartitions(-1);
        properties.getPersist().setPartitions(-1);

        assertThatThrownBy(properties::effectiveDispatchPartitions).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(properties::effectivePersistPartitions).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenZeroMaxBytes_whenResolve_thenTenPercentOfMaxHeap() {
        PipelineProperties properties = new PipelineProperties();

        long resolved = properties.effectiveMaxBytes();

        long tenPercent = Runtime.getRuntime().maxMemory() / 10;
        assertThat(resolved).isPositive().isBetween(tenPercent - 100, tenPercent);
    }

    @Test
    void givenNegativeMaxBytes_whenResolve_thenRejected() {
        PipelineProperties properties = new PipelineProperties();
        properties.getPipeline().setMaxBytes(-1);

        assertThatThrownBy(properties::effectiveMaxBytes).isInstanceOf(IllegalArgumentException.class);
    }

}
