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

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.queue.TbQueueRebalanceListener;
import org.thingsboard.mqtt.broker.queue.TbTopicPartition;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TbKafkaRebalanceListenerAdapterTest {

    @Test
    void givenKafkaCallbacks_whenInvoked_thenDelegatesWithConvertedPartitionsInOrder() {
        TbQueueRebalanceListener listener = mock(TbQueueRebalanceListener.class);
        ConsumerRebalanceListener kafka = TbKafkaConsumerTemplate.toKafkaListener(listener);

        kafka.onPartitionsAssigned(List.of(new TopicPartition("t", 0), new TopicPartition("t", 2)));
        verify(listener).onPartitionsAssigned(List.of(new TbTopicPartition("t", 0), new TbTopicPartition("t", 2)));

        kafka.onPartitionsRevoked(List.of(new TopicPartition("t", 3)));
        verify(listener).onPartitionsRevoked(List.of(new TbTopicPartition("t", 3)));

        kafka.onPartitionsLost(List.of(new TopicPartition("t", 1))); // Kafka's default delegates to revoked
        verify(listener).onPartitionsRevoked(List.of(new TbTopicPartition("t", 1)));
    }

}
