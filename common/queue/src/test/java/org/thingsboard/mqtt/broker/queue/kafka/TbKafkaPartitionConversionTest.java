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
package org.thingsboard.mqtt.broker.queue.kafka;

import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.queue.TbTopicPartition;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TbKafkaPartitionConversionTest {

    @Test
    void givenPartitions_whenToKafkaPartitions_thenKeepsOrderAndValues() {
        List<TopicPartition> result = TbKafkaConsumerTemplate.toKafkaPartitions(
                List.of(new TbTopicPartition("t", 0), new TbTopicPartition("t", 2)));

        assertThat(result).containsExactly(new TopicPartition("t", 0), new TopicPartition("t", 2));
    }

    @Test
    void givenNoPartitions_whenToKafkaPartitions_thenEmpty() {
        assertThat(TbKafkaConsumerTemplate.toKafkaPartitions(List.of())).isEmpty();
    }

    @Test
    void givenKafkaOffsets_whenFromKafkaOffsets_thenLookupByTbTopicPartitionWorks() {
        Map<TbTopicPartition, Long> result = TbKafkaConsumerTemplate.fromKafkaOffsets(
                Map.of(new TopicPartition("t", 0), 0L, new TopicPartition("t", 2), 42L));

        assertThat(result).isEqualTo(Map.of(new TbTopicPartition("t", 0), 0L, new TbTopicPartition("t", 2), 42L));
        assertThat(result.get(new TbTopicPartition("t", 2))).isEqualTo(42L);
    }

}
