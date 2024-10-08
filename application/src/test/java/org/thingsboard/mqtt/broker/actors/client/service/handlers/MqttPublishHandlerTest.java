/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.actors.client.service.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.TbActorRef;
import org.thingsboard.mqtt.broker.actors.client.messages.PubAckResponseMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.PubRecResponseMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPublishMsg;
import org.thingsboard.mqtt.broker.actors.client.state.MqttMsgWrapper;
import org.thingsboard.mqtt.broker.actors.client.state.PubResponseProcessingCtx;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgProcessor;
import org.thingsboard.mqtt.broker.service.mqtt.validation.PublishMsgValidationService;
import org.thingsboard.mqtt.broker.service.processing.MsgDispatcherService;
import org.thingsboard.mqtt.broker.session.AwaitingPubRelPacketsCtx;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;
import org.thingsboard.mqtt.broker.session.TopicAliasCtx;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = MqttPublishHandler.class)
public class MqttPublishHandlerTest {

    private static final int MAX_AWAITING_QUEUE_SIZE = 10;

    @MockBean
    MqttMessageGenerator mqttMessageGenerator;
    @MockBean
    MsgDispatcherService msgDispatcherService;
    @MockBean
    ClientMqttActorManager clientMqttActorManager;
    @MockBean
    ClientLogger clientLogger;
    @MockBean
    RetainedMsgProcessor retainedMsgProcessor;
    @MockBean
    PublishMsgValidationService publishMsgValidationService;

    @SpyBean
    MqttPublishHandler mqttPublishHandler;

    ClientSessionCtx ctx;
    TbActorRef actorRef;

    @Before
    public void setUp() {
        ctx = mock(ClientSessionCtx.class);
        actorRef = mock(TbActorRef.class);

        ChannelHandlerContext channelHandlerContext = mock(ChannelHandlerContext.class);
        when(ctx.getChannel()).thenReturn(channelHandlerContext);

        when(ctx.getPubResponseProcessingCtx()).thenReturn(new PubResponseProcessingCtx(MAX_AWAITING_QUEUE_SIZE));
        when(ctx.getAwaitingPubRelPacketsCtx()).thenReturn(new AwaitingPubRelPacketsCtx());
        when(ctx.getTopicAliasCtx()).thenReturn(new TopicAliasCtx(false, 0));
    }

    @Test
    public void givenProcessedQos1Msg_whenProcessPubAckResponse_thenSendPubAckMsg() {
        MqttMsgWrapper mqttMsgWrapper = mqttPublishHandler.processAtLeastOnce(ctx, 1);

        mqttPublishHandler.processPubAckResponse(ctx, new PubAckResponseMsg(UUID.randomUUID(), mqttMsgWrapper));

        verify(mqttMessageGenerator, times(1)).createPubAckMsg(1, null);
        verify(ctx, times(2)).getChannel();
    }

    @Test
    public void givenProcessedQos2Msg_whenProcessPubRecResponse_thenSendPubRecMsg() {
        MqttMsgWrapper mqttMsgWrapper = mqttPublishHandler.processExactlyOnce(ctx, 1);

        mqttPublishHandler.processPubRecResponse(ctx, new PubRecResponseMsg(UUID.randomUUID(), mqttMsgWrapper));

        verify(mqttMessageGenerator, times(1)).createPubRecMsg(1, null);
        verify(ctx, times(2)).getChannel();
    }

    @Test(expected = MqttException.class)
    public void givenUnauthorizedPublishQoS0AndMqttV3_whenValidatePubMsg_thenThrowException() {
        PublishMsg publishMsg = getPublishMsg(1, "test/1", 0);
        when(publishMsgValidationService.validatePubMsg(ctx, publishMsg)).thenReturn(false);

        mqttPublishHandler.validatePubMsg(ctx, publishMsg);
    }

    @Test(expected = MqttException.class)
    public void givenUnauthorizedPublishQoS1AndMqttV3_whenValidatePubMsg_thenThrowException() {
        PublishMsg publishMsg = getPublishMsg(1, "test/1", 1);
        when(publishMsgValidationService.validatePubMsg(ctx, publishMsg)).thenReturn(false);

        mqttPublishHandler.validatePubMsg(ctx, publishMsg);
    }

    @Test(expected = MqttException.class)
    public void givenUnauthorizedPublishQoS2AndMqttV3_whenValidatePubMsg_thenThrowException() {
        PublishMsg publishMsg = getPublishMsg(1, "test/1", 2);
        when(publishMsgValidationService.validatePubMsg(ctx, publishMsg)).thenReturn(false);

        mqttPublishHandler.validatePubMsg(ctx, publishMsg);
    }

    @Test
    public void givenQoS2AndMaxInFlightMessagesReached_whenProcessNewPublishMsg_thenDisconnectClient() {
        when(publishMsgValidationService.validatePubMsg(any(), any())).thenReturn(true);
        when(ctx.getClientId()).thenReturn("clientId");
        when(ctx.getSessionId()).thenReturn(UUID.randomUUID());

        for (int i = 0; i < MAX_AWAITING_QUEUE_SIZE + 1; i++) {
            PublishMsg publishMsg = getPublishMsg(i + 1, "test/1", 2);
            mqttPublishHandler.process(ctx, createMqttPubMsg(publishMsg), actorRef);
        }
        ArgumentCaptor<MqttDisconnectMsg> newMsgCaptor = ArgumentCaptor.forClass(MqttDisconnectMsg.class);
        verify(clientMqttActorManager, times(1)).disconnect(eq("clientId"), newMsgCaptor.capture());

        MqttDisconnectMsg disconnectMsg = newMsgCaptor.getValue();
        assertThat(disconnectMsg).isNotNull();
        assertThat(disconnectMsg.getReason().getType()).isEqualTo(DisconnectReasonType.ON_RECEIVE_MAXIMUM_EXCEEDED);
    }

    @Test
    public void givenQoS1AndMaxInFlightMessagesReached_whenProcessNewPublishMsg_thenDisconnectClient() {
        when(publishMsgValidationService.validatePubMsg(any(), any())).thenReturn(true);
        when(ctx.getClientId()).thenReturn("clientId");
        when(ctx.getSessionId()).thenReturn(UUID.randomUUID());

        for (int i = 0; i < MAX_AWAITING_QUEUE_SIZE + 1; i++) {
            PublishMsg publishMsg = getPublishMsg(i + 1, "test/1", 1);
            mqttPublishHandler.process(ctx, createMqttPubMsg(publishMsg), actorRef);
        }
        ArgumentCaptor<MqttDisconnectMsg> newMsgCaptor = ArgumentCaptor.forClass(MqttDisconnectMsg.class);
        verify(clientMqttActorManager, times(1)).disconnect(eq("clientId"), newMsgCaptor.capture());

        MqttDisconnectMsg disconnectMsg = newMsgCaptor.getValue();
        assertThat(disconnectMsg).isNotNull();
        assertThat(disconnectMsg.getReason().getType()).isEqualTo(DisconnectReasonType.ON_RECEIVE_MAXIMUM_EXCEEDED);
    }

    @Test
    public void givenQoS0AndMaxInFlightMessagesSent_whenProcessNewPublishMsg_thenNoDisconnectionOfClient() {
        when(publishMsgValidationService.validatePubMsg(any(), any())).thenReturn(true);
        when(ctx.getClientId()).thenReturn("clientId");
        when(ctx.getSessionId()).thenReturn(UUID.randomUUID());

        for (int i = 0; i < MAX_AWAITING_QUEUE_SIZE + 1; i++) {
            PublishMsg publishMsg = getPublishMsg(i + 1, "test/1", 0);
            mqttPublishHandler.process(ctx, createMqttPubMsg(publishMsg), actorRef);
        }
        verify(clientMqttActorManager, never()).disconnect(eq("clientId"), any());
    }

    @Test
    public void givenUnauthorizedPublishQoS0AndMqttV5_whenValidatePubMsg_thenDoNotSendPubResponseWithReasonCode() {
        PublishMsg publishMsg = getPublishMsg(2, "test/2", 0);

        when(publishMsgValidationService.validatePubMsg(ctx, publishMsg)).thenReturn(false);
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);

        boolean result = mqttPublishHandler.validatePubMsg(ctx, publishMsg);
        assertThat(result).isFalse();

        verify(mqttMessageGenerator, never()).createPubAckMsg(2, MqttReasonCodes.PubAck.NOT_AUTHORIZED);
        verify(mqttMessageGenerator, never()).createPubRecMsg(2, MqttReasonCodes.PubRec.NOT_AUTHORIZED);
    }

    @Test
    public void givenUnauthorizedPublishQoS1AndMqttV5_whenValidatePubMsg_thenSendPubResponseWithReasonCode() {
        PublishMsg publishMsg = getPublishMsg(2, "test/2", 1);

        when(publishMsgValidationService.validatePubMsg(ctx, publishMsg)).thenReturn(false);
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);

        boolean result = mqttPublishHandler.validatePubMsg(ctx, publishMsg);
        assertThat(result).isFalse();

        verify(mqttMessageGenerator, times(1)).createPubAckMsg(2, MqttReasonCodes.PubAck.NOT_AUTHORIZED);
    }

    @Test
    public void givenUnauthorizedPublishQoS2AndMqttV5_whenValidatePubMsg_thenSendPubResponseWithReasonCode() {
        PublishMsg publishMsg = getPublishMsg(2, "test/2", 2);

        when(publishMsgValidationService.validatePubMsg(ctx, publishMsg)).thenReturn(false);
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);

        boolean result = mqttPublishHandler.validatePubMsg(ctx, publishMsg);
        assertThat(result).isFalse();

        verify(mqttMessageGenerator, times(1)).createPubRecMsg(2, MqttReasonCodes.PubRec.NOT_AUTHORIZED);
    }

    @Test(expected = DataValidationException.class)
    public void givenWrongPublishTopicQoS0AndMqttV3_whenValidatePubMsg_thenThrowException() {
        PublishMsg publishMsg = getPublishMsg(1, "test/+", 0);
        doThrow(DataValidationException.class).when(publishMsgValidationService).validatePubMsg(ctx, publishMsg);
        mqttPublishHandler.validatePubMsg(ctx, publishMsg);
    }

    @Test(expected = DataValidationException.class)
    public void givenWrongPublishTopicQoS1AndMqttV3_whenValidatePubMsg_thenThrowException() {
        PublishMsg publishMsg = getPublishMsg(1, "test/+", 1);
        doThrow(DataValidationException.class).when(publishMsgValidationService).validatePubMsg(ctx, publishMsg);
        mqttPublishHandler.validatePubMsg(ctx, publishMsg);
    }

    @Test(expected = DataValidationException.class)
    public void givenWrongPublishTopicQoS2AndMqttV3_whenValidatePubMsg_thenThrowException() {
        PublishMsg publishMsg = getPublishMsg(1, "test/+", 2);
        doThrow(DataValidationException.class).when(publishMsgValidationService).validatePubMsg(ctx, publishMsg);
        mqttPublishHandler.validatePubMsg(ctx, publishMsg);
    }

    @Test
    public void givenWrongPublishTopicQoS0AndMqttV5_whenValidatePubMsg_thenDoNotSendPubResponseWithReasonCode() {
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);

        PublishMsg publishMsg = getPublishMsg(2, "test/+", 0);
        doThrow(DataValidationException.class).when(publishMsgValidationService).validatePubMsg(ctx, publishMsg);
        boolean result = mqttPublishHandler.validatePubMsg(ctx, publishMsg);
        assertThat(result).isFalse();

        verify(mqttMessageGenerator, never()).createPubAckMsg(2, MqttReasonCodes.PubAck.TOPIC_NAME_INVALID);
        verify(mqttMessageGenerator, never()).createPubRecMsg(2, MqttReasonCodes.PubRec.TOPIC_NAME_INVALID);
    }

    @Test
    public void givenWrongPublishTopicQoS1AndMqttV5_whenValidatePubMsg_thenSendPubResponseWithReasonCode() {
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);

        PublishMsg publishMsg = getPublishMsg(2, "test/+", 1);
        doThrow(DataValidationException.class).when(publishMsgValidationService).validatePubMsg(ctx, publishMsg);
        boolean result = mqttPublishHandler.validatePubMsg(ctx, publishMsg);
        assertThat(result).isFalse();

        verify(mqttMessageGenerator, times(1)).createPubAckMsg(eq(2), eq(MqttReasonCodes.PubAck.TOPIC_NAME_INVALID));
    }

    @Test
    public void givenWrongPublishTopicQoS2AndMqttV5_whenValidatePubMsg_thenSendPubResponseWithReasonCode() {
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);

        PublishMsg publishMsg = getPublishMsg(2, "test/+", 2);
        doThrow(DataValidationException.class).when(publishMsgValidationService).validatePubMsg(ctx, publishMsg);
        boolean result = mqttPublishHandler.validatePubMsg(ctx, publishMsg);
        assertThat(result).isFalse();

        verify(mqttMessageGenerator, times(1)).createPubRecMsg(eq(2), eq(MqttReasonCodes.PubRec.TOPIC_NAME_INVALID));
    }

    @Test
    public void givenPubMsg_whenProcessPubMsg_thenVerifySuccess() {
        when(publishMsgValidationService.validatePubMsg(any(), any())).thenReturn(true);

        PublishMsg publishMsg = getPublishMsg(1, 2);

        mqttPublishHandler.process(ctx, createMqttPubMsg(publishMsg), actorRef);
        verify(mqttPublishHandler, times(1)).processExactlyOnce(ctx, 1);

        publishMsg = getPublishMsg(2, 1);
        mqttPublishHandler.process(ctx, createMqttPubMsg(publishMsg), actorRef);

        verify(mqttPublishHandler, times(1)).processAtLeastOnce(eq(ctx), eq(2));
        verify(mqttPublishHandler, times(2)).persistPubMsg(eq(ctx), any(), eq(actorRef), any());
    }

    @Test
    public void givenRetainPubMsg_whenProcessPubMsg_thenVerifySuccess() {
        when(publishMsgValidationService.validatePubMsg(any(), any())).thenReturn(true);

        PublishMsg publishMsg = getPublishMsg(1, 2, true);

        mqttPublishHandler.process(ctx, createMqttPubMsg(publishMsg), actorRef);
        verify(mqttPublishHandler, times(1)).processExactlyOnce(ctx, 1);

        verify(mqttPublishHandler, times(1)).persistPubMsg(eq(ctx), any(), eq(actorRef), any());
        verify(retainedMsgProcessor, times(1)).process(eq(publishMsg));
    }

    private MqttPublishMsg createMqttPubMsg(PublishMsg publishMsg) {
        return new MqttPublishMsg(UUID.randomUUID(), publishMsg);
    }

    private PublishMsg getPublishMsg(int packetId, String topic, int qos) {
        return getPublishMsg(packetId, topic, qos, false);
    }

    private PublishMsg getPublishMsg(int packetId, int qos) {
        return getPublishMsg(packetId, qos, false);
    }

    private PublishMsg getPublishMsg(int packetId, int qos, boolean isRetained) {
        return getPublishMsg(packetId, "test", qos, isRetained);
    }

    private PublishMsg getPublishMsg(int packetId, String topic, int qos, boolean isRetained) {
        return new PublishMsg(packetId, topic, "data".getBytes(), qos, isRetained, false);
    }

}
