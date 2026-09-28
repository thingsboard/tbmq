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
package org.thingsboard.mqtt;

import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.netty.handler.codec.mqtt.MqttVersion;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Slf4j
class MqttOrderedAcknowledgementCtxTest {

    MqttOrderedAcknowledgementCtx mqttOrderedAcknowledgementCtx;
    String clientId = "test";
    Channel channel;

    @BeforeEach
    void setUp() {
        channel = mock(Channel.class);
        mqttOrderedAcknowledgementCtx = new MqttOrderedAcknowledgementCtx(clientId, MqttVersion.MQTT_3_1_1, MqttMessageType.PUBREC);
    }

    @ParameterizedTest
    @MethodSource("addMsgIdParameters")
    public void whenAddMsgId_thenQueueHasExpectedSize(int msgId, int size) {
        mqttOrderedAcknowledgementCtx.addMsgId(msgId);

        assertThat(mqttOrderedAcknowledgementCtx.getReceivedMsgQueue().size()).isEqualTo(size);
    }

    private static Stream<Arguments> addMsgIdParameters() {
        return Stream.of(
                Arguments.of(1, 1),
                Arguments.of(-1, 0)
        );
    }

    @ParameterizedTest
    @MethodSource("addMsgIdProcessAckMsgParameters")
    void givenAddedMsgId_whenAckMsgId_thenGetExpectedResult(List<Integer> initialMsgIds, int ackMsgId, int expectedQueueSize, int wantedNumberOfWriteAndFlush) {
        MqttOrderedAcknowledgementCtx.MqttMsgWrapper ackMsgWrapper = null;
        for (Integer id : initialMsgIds) {
            var addedMsgWrapper = mqttOrderedAcknowledgementCtx.addMsgId(id);
            if (id == ackMsgId) {
                ackMsgWrapper = addedMsgWrapper;
            }
        }
        assertThat(ackMsgWrapper).as("wrong test condition: ackMsgId should be in the list of initialMsgIds").isNotNull();

        mqttOrderedAcknowledgementCtx.ack(channel, ackMsgWrapper, MqttReasonCodes.PubComp.SUCCESS.byteValue(), true);

        assertThat(mqttOrderedAcknowledgementCtx.getReceivedMsgQueue().size()).isEqualTo(expectedQueueSize);

        verify(channel, times(wantedNumberOfWriteAndFlush)).writeAndFlush(any());
    }

    private static Stream<Arguments> addMsgIdProcessAckMsgParameters() {
        return Stream.of(
                Arguments.of(List.of(1, 2), 2, 2, 0),
                Arguments.of(List.of(1), 1, 0, 1)
        );
    }

    @ParameterizedTest
    @MethodSource("givenAddedMessages_whenAckMessagesShuffled_thenAllSucceed")
    public void givenAddedMsgs_whenAckMsgsShuffled_thenAllSucceed(List<Integer> addMsgIds) {
        List<MqttOrderedAcknowledgementCtx.MqttMsgWrapper> added = addMsgIds.stream()
                .map(id -> mqttOrderedAcknowledgementCtx.addMsgId(id)).collect(Collectors.toList());
        Collections.shuffle(added);
        log.trace("Shuffled collection: {}", added);
        added.forEach(msgWrapper -> mqttOrderedAcknowledgementCtx.ack(channel, msgWrapper, MqttReasonCodes.PubComp.SUCCESS.byteValue(), true));

        assertThat(mqttOrderedAcknowledgementCtx.getReceivedMsgQueue().size()).isZero();

        verify(channel, times(addMsgIds.size())).writeAndFlush(any());
    }

    private static Stream<Arguments> givenAddedMessages_whenAckMessagesShuffled_thenAllSucceed() {
        return Stream.of(
                Arguments.of(List.of(1, 2, 3, 4, 5)),
                Arguments.of(List.of(1, 2, 3, 4, 5, 6)),
                Arguments.of(List.of(1, 2, 3, 4, 5, 6, 7)),
                Arguments.of(List.of(1, 3, 3, 5, 5, 5, 10000))
        );
    }

}
