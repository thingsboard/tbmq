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

import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumerGroupNotFoundException;
import org.thingsboard.mqtt.broker.queue.TbQueueTopicNotFoundException;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class TbKafkaAdminFailureMappingTest {

    @Test
    void givenUnknownTopicInChain_whenMapDeleteTopicFailure_thenTopicNotFound() {
        Throwable failure = new CompletionException(new UnknownTopicOrPartitionException("x"));

        assertThat(TbKafkaAdmin.mapDeleteTopicFailure("t", failure))
                .isInstanceOf(TbQueueTopicNotFoundException.class)
                .hasMessage("Topic not found: t")
                .hasCause(failure);
    }

    @Test
    void givenOtherFailure_whenMapDeleteTopicFailure_thenSameInstance() {
        Throwable other = new TimeoutException();

        assertThat(TbKafkaAdmin.mapDeleteTopicFailure("t", other)).isSameAs(other);
    }

    @Test
    void givenGroupIdNotFound_whenMapDeleteConsumerGroupFailure_thenGroupNotFound() {
        ExecutionException failure = new ExecutionException(new GroupIdNotFoundException("x"));

        assertThat(TbKafkaAdmin.mapDeleteConsumerGroupFailure("g", failure))
                .isInstanceOf(TbQueueConsumerGroupNotFoundException.class)
                .hasMessage("Consumer group not found: g")
                .hasCause(failure);
    }

    @Test
    void givenOtherFailure_whenMapDeleteConsumerGroupFailure_thenSameInstance() {
        ExecutionException timeout = new ExecutionException(new TimeoutException());

        assertThat(TbKafkaAdmin.mapDeleteConsumerGroupFailure("g", timeout)).isSameAs(timeout);
    }

}
