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
package org.thingsboard.mqtt.broker.actors.client.state;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.exception.FullMsgQueueException;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;

public class OrderedProcessingQueueImplTest {

    OrderedProcessingQueueImpl orderedProcessingQueue;

    @BeforeEach
    public void setUp() {
        orderedProcessingQueue = spy(new OrderedProcessingQueueImpl(10));
    }

    @Test
    public void givenEmptyQueue_whenAddMoreThanLimitAwaiting_thenThrowException() {
        for (int i = 0; i < 10; i++) {
            final int msgId = i;
            assertDoesNotThrow(() -> orderedProcessingQueue.addMsgId(msgId));
        }
        assertThrows(FullMsgQueueException.class, () -> orderedProcessingQueue.addMsgId(10));
    }

    @Test
    public void givenNoMessages_whenAckMsgThatIsNull_thenReturnEmptyList() {
        List<Integer> ack = orderedProcessingQueue.ack(null);
        assertThat(ack).isEmpty();
    }

    @Test
    public void givenNonEmptyQueue_whenAckMessages_thenReturnExpectedResult() {
        MqttMsgWrapper mqttMsgWrapper1 = orderedProcessingQueue.addMsgId(1);
        MqttMsgWrapper mqttMsgWrapper2 = orderedProcessingQueue.addMsgId(2);
        MqttMsgWrapper mqttMsgWrapper3 = orderedProcessingQueue.addMsgId(3);

        List<Integer> finished = orderedProcessingQueue.ack(createMsgWrapper(5));
        assertTrue(CollectionUtils.isEmpty(finished));
        finished = orderedProcessingQueue.ack(createMsgWrapper(4));
        assertTrue(CollectionUtils.isEmpty(finished));
        finished = orderedProcessingQueue.ack(mqttMsgWrapper3);
        assertTrue(CollectionUtils.isEmpty(finished));
        finished = orderedProcessingQueue.ack(mqttMsgWrapper2);
        assertTrue(CollectionUtils.isEmpty(finished));

        finished = orderedProcessingQueue.ack(mqttMsgWrapper1);
        assertEquals(List.of(1, 2, 3), finished);

        assertQueueIsEmpty();
    }

    @Test
    public void givenNonEmptyQueue_whenAckMsgInOrder_thenReturnResult() {
        MqttMsgWrapper mqttMsgWrapper1 = orderedProcessingQueue.addMsgId(1);
        List<Integer> finished = orderedProcessingQueue.ack(mqttMsgWrapper1);
        assertEquals(List.of(1), finished);

        MqttMsgWrapper mqttMsgWrapper2 = orderedProcessingQueue.addMsgId(2);
        finished = orderedProcessingQueue.ack(mqttMsgWrapper2);
        assertEquals(List.of(2), finished);

        MqttMsgWrapper mqttMsgWrapper3 = orderedProcessingQueue.addMsgId(3);
        finished = orderedProcessingQueue.ack(mqttMsgWrapper3);
        assertEquals(List.of(3), finished);

        assertQueueIsEmpty();
    }

    @Test
    public void givenNonEmptyQueue_whenAckSameMessages_thenReturnExpectedResult() {
        MqttMsgWrapper mqttMsgWrapper1_0 = orderedProcessingQueue.addMsgId(1);
        MqttMsgWrapper mqttMsgWrapper1_1 = orderedProcessingQueue.addMsgId(1);
        MqttMsgWrapper mqttMsgWrapper2_0 = orderedProcessingQueue.addMsgId(2);
        MqttMsgWrapper mqttMsgWrapper2_1 = orderedProcessingQueue.addMsgId(2);
        MqttMsgWrapper mqttMsgWrapper3_0 = orderedProcessingQueue.addMsgId(3);
        MqttMsgWrapper mqttMsgWrapper3_1 = orderedProcessingQueue.addMsgId(3);

        List<Integer> finished = orderedProcessingQueue.ack(mqttMsgWrapper3_0);
        assertTrue(CollectionUtils.isEmpty(finished));

        finished = orderedProcessingQueue.ack(mqttMsgWrapper3_1);
        assertTrue(CollectionUtils.isEmpty(finished));

        finished = orderedProcessingQueue.ack(mqttMsgWrapper1_0);
        assertEquals(List.of(1), finished);

        finished = orderedProcessingQueue.ack(mqttMsgWrapper1_1);
        assertEquals(List.of(1), finished);

        finished = orderedProcessingQueue.ack(mqttMsgWrapper2_1);
        assertTrue(CollectionUtils.isEmpty(finished));

        finished = orderedProcessingQueue.ack(mqttMsgWrapper2_0);
        assertEquals(List.of(2, 2, 3, 3), finished);

        assertQueueIsEmpty();
    }

    @ParameterizedTest
    @MethodSource("givenAddedMsgs_whenAckMsgsShuffled_thenAllSucceed")
    public void givenAddedMsgs_whenAckMsgsShuffled_thenAllSucceed(List<Integer> addMsgIds) {
        List<MqttMsgWrapper> added = addMsgIds.stream()
                .map(id -> orderedProcessingQueue.addMsgId(id)).collect(Collectors.toList());
        Collections.shuffle(added);
        added.forEach(msgWrapper -> orderedProcessingQueue.ack(msgWrapper));
        assertQueueIsEmpty();
    }

    private static Stream<Arguments> givenAddedMsgs_whenAckMsgsShuffled_thenAllSucceed() {
        return Stream.of(
                Arguments.of(List.of(1, 2, 3, 4, 5)),
                Arguments.of(List.of(1, 2, 3, 4, 5, 6)),
                Arguments.of(List.of(1, 2, 3, 4, 5, 6, 7)),
                Arguments.of(List.of(1, 3, 3, 5, 5, 5, 10000))
        );
    }

    private void assertQueueIsEmpty() {
        assertThat(orderedProcessingQueue.getQueueSize().get()).isEqualTo(0);
        assertThat(orderedProcessingQueue.getReceivedMsgQueue().size()).isZero();
    }

    private MqttMsgWrapper createMsgWrapper(int msgId) {
        return MqttMsgWrapper.newInstance(msgId);
    }
}
