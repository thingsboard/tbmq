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
package org.thingsboard.mqtt.broker.actors.client.service.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.TbActorRef;
import org.thingsboard.mqtt.broker.actors.client.messages.PubAckResponseMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.PubRecResponseMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPublishMsg;
import org.thingsboard.mqtt.broker.actors.client.state.MqttMsgWrapper;
import org.thingsboard.mqtt.broker.actors.client.state.OrderedProcessingQueue;
import org.thingsboard.mqtt.broker.actors.client.state.OrderedProcessingQueueImpl;
import org.thingsboard.mqtt.broker.actors.client.state.PubResponseProcessingCtx;
import org.thingsboard.mqtt.broker.actors.msg.TbActorMsg;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.exception.DataValidationException;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgProcessor;
import org.thingsboard.mqtt.broker.service.mqtt.sparkplug.SparkplugCertificateRepublisher;
import org.thingsboard.mqtt.broker.service.mqtt.validation.PublishMsgValidationService;
import org.thingsboard.mqtt.broker.service.processing.MsgDispatcherService;
import org.thingsboard.mqtt.broker.service.processing.PublisherIdentity;
import org.thingsboard.mqtt.broker.session.AwaitingPubRelPacketsCtx;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;
import org.thingsboard.mqtt.broker.session.TopicAliasCtx;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = MqttPublishHandler.class)
public class MqttPublishHandlerTest {

    private static final int MAX_AWAITING_QUEUE_SIZE = 10;

    @MockitoBean
    MqttMessageGenerator mqttMessageGenerator;
    @MockitoBean
    MsgDispatcherService msgDispatcherService;
    @MockitoBean
    ClientMqttActorManager clientMqttActorManager;
    @MockitoBean
    ClientLogger clientLogger;
    @MockitoBean
    RetainedMsgProcessor retainedMsgProcessor;
    @MockitoBean
    PublishMsgValidationService publishMsgValidationService;
    @MockitoBean
    TbMessageStatsReportClient tbMessageStatsReportClient;
    @MockitoBean
    SparkplugCertificateRepublisher sparkplugCertificateRepublisher;

    @MockitoSpyBean
    MqttPublishHandler mqttPublishHandler;

    ClientSessionCtx ctx;
    TbActorRef actorRef;
    AwaitingPubRelPacketsCtx awaitingPubRelPacketsCtx;
    int processedActorMsgsCount;

    @Before
    public void setUp() {
        ctx = mock(ClientSessionCtx.class);
        actorRef = mock(TbActorRef.class);

        ChannelHandlerContext channelHandlerContext = mock(ChannelHandlerContext.class);
        when(ctx.getChannel()).thenReturn(channelHandlerContext);

        when(ctx.getPubResponseProcessingCtx()).thenReturn(new PubResponseProcessingCtx(MAX_AWAITING_QUEUE_SIZE));
        awaitingPubRelPacketsCtx = new AwaitingPubRelPacketsCtx();
        when(ctx.getAwaitingPubRelPacketsCtx()).thenReturn(awaitingPubRelPacketsCtx);
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
    public void givenProcessedQos1Msg_whenProcessPubAckResponseInWrongOrder_thenDoNotSendPubAckMsg() {
        mqttPublishHandler.processAtLeastOnce(ctx, 1);

        mqttPublishHandler.processPubAckResponse(ctx, new PubAckResponseMsg(UUID.randomUUID(), MqttMsgWrapper.newInstance(2)));

        verify(mqttMessageGenerator, never()).createPubAckMsg(anyInt(), any());
        verify(ctx, never()).getChannel();
    }

    @Test
    public void givenProcessedQos2Msg_whenProcessPubRecResponse_thenSendPubRecMsg() {
        MqttMsgWrapper mqttMsgWrapper = mqttPublishHandler.processExactlyOnce(ctx, 1);

        mqttPublishHandler.processPubRecResponse(ctx, new PubRecResponseMsg(UUID.randomUUID(), mqttMsgWrapper));

        verify(mqttMessageGenerator, times(1)).createPubRecMsg(1, null);
        verify(ctx, times(2)).getChannel();
    }

    @Test
    public void givenProcessedQos2Msg_whenProcessPubRecResponseInWrongOrder_thenDoNotSendPubRecMsg() {
        mqttPublishHandler.processExactlyOnce(ctx, 1);

        mqttPublishHandler.processPubRecResponse(ctx, new PubRecResponseMsg(UUID.randomUUID(), MqttMsgWrapper.newInstance(2)));

        verify(mqttMessageGenerator, never()).createPubRecMsg(anyInt(), any());
        verify(ctx, never()).getChannel();
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
        verify(tbMessageStatsReportClient, times(1)).reportDroppedMsgs();
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
        verify(tbMessageStatsReportClient, times(1)).reportDroppedMsgs();
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
        verify(tbMessageStatsReportClient, never()).reportDroppedMsgs();
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
    public void givenPublishMsg_whenProcessPubMsgWithErrorInTopicAliasExecution_thenDisconnectClient() {
        PublishMsg publishMsg = getPublishMsg(2, "test/+", 2);

        TopicAliasCtx topicAliasCtx = mock(TopicAliasCtx.class);
        when(ctx.getTopicAliasCtx()).thenReturn(topicAliasCtx);
        when(ctx.getClientId()).thenReturn("clientId");
        when(topicAliasCtx.getTopicNameByAlias(publishMsg)).thenThrow(MqttException.class);

        mqttPublishHandler.process(ctx, createMqttPubMsg(publishMsg), actorRef);

        ArgumentCaptor<MqttDisconnectMsg> newMsgCaptor = ArgumentCaptor.forClass(MqttDisconnectMsg.class);
        verify(clientMqttActorManager, times(1)).disconnect(eq("clientId"), newMsgCaptor.capture());

        MqttDisconnectMsg disconnectMsg = newMsgCaptor.getValue();
        assertThat(disconnectMsg).isNotNull();
        assertThat(disconnectMsg.getReason().getType()).isEqualTo(DisconnectReasonType.ON_TOPIC_ALIAS_INVALID);
    }

    @Test
    public void givenPublishMsg_whenProcessPubMsgWithUnknownTopicAlias_thenDisconnectClient() {
        PublishMsg publishMsg = getPublishMsg(1, "test/+", 1);

        TopicAliasCtx topicAliasCtx = mock(TopicAliasCtx.class);
        when(ctx.getTopicAliasCtx()).thenReturn(topicAliasCtx);
        when(ctx.getClientId()).thenReturn("clientId");
        when(topicAliasCtx.getTopicNameByAlias(publishMsg)).thenThrow(new MqttException(TopicAliasCtx.UNKNOWN_TOPIC_ALIAS_MSG));

        mqttPublishHandler.process(ctx, createMqttPubMsg(publishMsg), actorRef);

        ArgumentCaptor<MqttDisconnectMsg> newMsgCaptor = ArgumentCaptor.forClass(MqttDisconnectMsg.class);
        verify(clientMqttActorManager, times(1)).disconnect(eq("clientId"), newMsgCaptor.capture());

        MqttDisconnectMsg disconnectMsg = newMsgCaptor.getValue();
        assertThat(disconnectMsg).isNotNull();
        assertThat(disconnectMsg.getReason().getType()).isEqualTo(DisconnectReasonType.ON_PROTOCOL_ERROR);
    }

    @Test
    public void givenSamePublishMsg_whenProcessPubMsgTwice_thenPersistMsgOnlyOnce() {
        when(publishMsgValidationService.validatePubMsg(any(), any())).thenReturn(true);

        PublishMsg publishMsg = getPublishMsg(1, 2);

        mqttPublishHandler.process(ctx, createMqttPubMsg(publishMsg), actorRef);
        mqttPublishHandler.process(ctx, createMqttPubMsg(publishMsg), actorRef);

        // the duplicate arrives while the first msg is still being persisted, so it gets no entry in the ordered queue
        verify(mqttPublishHandler, times(1)).processExactlyOnce(eq(ctx), eq(1));
        assertThat(queueSize(ctx.getPubResponseProcessingCtx().getQos2PubRecResponseMessages())).isEqualTo(1);
        verify(mqttPublishHandler).persistPubMsg(eq(ctx), any(), eq(actorRef), any()); // second process will not cause to persist a duplicate msg into Kafka
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

    @Test
    public void givenAcceptedPubMsg_whenProcessPubMsg_thenInvokesSparkplugCertificateRepublisher() {
        when(publishMsgValidationService.validatePubMsg(any(), any())).thenReturn(true);
        SessionInfo sessionInfo = mock(SessionInfo.class);
        when(ctx.getSessionInfo()).thenReturn(sessionInfo);
        when(ctx.getClientCertCn()).thenReturn("cn-edge");
        when(ctx.getUsername()).thenReturn("edge-user");

        PublishMsg publishMsg = getPublishMsg(1, "spBv1.0/G1/NBIRTH/E1", 0);

        mqttPublishHandler.process(ctx, createMqttPubMsg(publishMsg), actorRef);

        verify(sparkplugCertificateRepublisher, times(1))
                .maybeRepublish(eq(sessionInfo), eq(publishMsg), eq(new PublisherIdentity("edge-user", "cn-edge")));
    }

    @Test
    public void givenRejectedPubMsg_whenProcessPubMsg_thenSparkplugCertificateRepublisherNotInvoked() {
        when(publishMsgValidationService.validatePubMsg(any(), any())).thenReturn(false);
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);

        PublishMsg publishMsg = getPublishMsg(1, "spBv1.0/G1/NBIRTH/E1", 0);

        mqttPublishHandler.process(ctx, createMqttPubMsg(publishMsg), actorRef);

        verify(sparkplugCertificateRepublisher, never())
                .maybeRepublish(any(), any(), any());
    }

    @Test
    public void givenNbirthPubMsg_whenProcessPubMsg_thenOriginalPersistedAndCertificateRepublishHookInvoked() {
        when(publishMsgValidationService.validatePubMsg(any(), any())).thenReturn(true);
        when(ctx.getSessionInfo()).thenReturn(mock(SessionInfo.class));

        PublishMsg publishMsg = getPublishMsg(1, "spBv1.0/G1/NBIRTH/E1", 0);

        mqttPublishHandler.process(ctx, createMqttPubMsg(publishMsg), actorRef);

        // original publish still flows through the standard persistence path
        verify(mqttPublishHandler, times(1)).persistPubMsg(eq(ctx), eq(publishMsg), eq(actorRef), any());
        // certificate republish hook invoked exactly once
        verify(sparkplugCertificateRepublisher, times(1))
                .maybeRepublish(any(), eq(publishMsg), any());
    }

    @Test
    public void givenSessionWithUsernameAndCert_whenPersistPubMsg_thenDispatchesPublisherIdentity() {
        SessionInfo sessionInfo = mock(SessionInfo.class);
        when(ctx.getSessionInfo()).thenReturn(sessionInfo);
        when(ctx.getUsername()).thenReturn("alice");
        when(ctx.getClientCertCn()).thenReturn("CN=dev");
        PublishMsg publishMsg = getPublishMsg(1, "sensors/1", 1);

        mqttPublishHandler.persistPubMsg(ctx, publishMsg, actorRef, null);

        verify(msgDispatcherService).persistPublishMsg(eq(sessionInfo), eq(publishMsg), eq(new PublisherIdentity("alice", "CN=dev")), any());
    }

    @Test
    public void givenMqtt5Qos1MsgFailedToPersist_whenLaterMsgsPersisted_thenPubAcksReleasedInOrderWithErrorCodeForFailedMsg() {
        givenMqtt5Session();
        processPublishMsgs(1, 1, 2, 3);
        List<TbQueueCallback> callbacks = capturePersistCallbacks(3);

        callbacks.get(0).onFailure(new RuntimeException("NOT_LEADER_OR_FOLLOWER"));
        callbacks.get(1).onSuccess(null);
        callbacks.get(2).onSuccess(null);
        processPubResponsesSentToActor(3);

        InOrder inOrder = inOrder(mqttMessageGenerator);
        inOrder.verify(mqttMessageGenerator).createPubAckMsg(1, MqttReasonCodes.PubAck.UNSPECIFIED_ERROR);
        inOrder.verify(mqttMessageGenerator).createPubAckMsg(2, MqttReasonCodes.PubAck.SUCCESS);
        inOrder.verify(mqttMessageGenerator).createPubAckMsg(3, MqttReasonCodes.PubAck.SUCCESS);
        assertThat(queueSize(ctx.getPubResponseProcessingCtx().getQos1PubAckResponseMessages())).isZero();
    }

    @Test
    public void givenMqtt5Qos2MsgFailedToPersist_whenLaterMsgPersisted_thenPubRecsReleasedInOrderAndFailedPacketIdIsFreed() {
        givenMqtt5Session();
        processPublishMsgs(2, 20, 21);
        List<TbQueueCallback> callbacks = capturePersistCallbacks(2);

        callbacks.get(0).onFailure(new RuntimeException("NOT_LEADER_OR_FOLLOWER"));
        callbacks.get(1).onSuccess(null);
        processPubResponsesSentToActor(2);

        InOrder inOrder = inOrder(mqttMessageGenerator);
        inOrder.verify(mqttMessageGenerator).createPubRecMsg(20, MqttReasonCodes.PubRec.UNSPECIFIED_ERROR);
        inOrder.verify(mqttMessageGenerator).createPubRecMsg(21, MqttReasonCodes.PubRec.SUCCESS);
        assertThat(queueSize(ctx.getPubResponseProcessingCtx().getQos2PubRecResponseMessages())).isZero();
        assertThat(awaitingPubRelPacketsCtx.getAwaitingPacket(20)).isNull();
    }

    @Test
    public void givenMqtt5Qos2MsgFailedToPersistBehindPendingMsg_whenPendingMsgPersisted_thenFailedPacketIdIsFreed() {
        givenMqtt5Session();
        processPublishMsgs(2, 20, 21);
        List<TbQueueCallback> callbacks = capturePersistCallbacks(2);

        // the failure of 21 is handled while 20 is still in flight, so its error PUBREC can't be released yet
        callbacks.get(1).onFailure(new RuntimeException("NOT_LEADER_OR_FOLLOWER"));
        processPubResponsesSentToActor(1);
        verify(mqttMessageGenerator, never()).createPubRecMsg(anyInt(), any());

        callbacks.get(0).onSuccess(null);
        processPubResponsesSentToActor(2);

        InOrder inOrder = inOrder(mqttMessageGenerator);
        inOrder.verify(mqttMessageGenerator).createPubRecMsg(20, MqttReasonCodes.PubRec.SUCCESS);
        inOrder.verify(mqttMessageGenerator).createPubRecMsg(21, MqttReasonCodes.PubRec.UNSPECIFIED_ERROR);
        assertThat(awaitingPubRelPacketsCtx.getAwaitingPacket(21)).isNull();
        assertThat(awaitingPubRelPacketsCtx.getAwaitingPacket(20)).isNotNull();
    }

    @Test
    public void givenMqtt5Qos2MsgPersistedBehindPendingMsg_whenPendingMsgPersisted_thenBothMarkedPersisted() {
        givenMqtt5Session();
        processPublishMsgs(2, 20, 21);
        List<TbQueueCallback> callbacks = capturePersistCallbacks(2);

        // 21 is stored first, while 20 is still in flight, so its PUBREC can't be released yet
        callbacks.get(1).onSuccess(null);
        processPubResponsesSentToActor(1);
        callbacks.get(0).onSuccess(null);
        processPubResponsesSentToActor(2);

        InOrder inOrder = inOrder(mqttMessageGenerator);
        inOrder.verify(mqttMessageGenerator).createPubRecMsg(20, MqttReasonCodes.PubRec.SUCCESS);
        inOrder.verify(mqttMessageGenerator).createPubRecMsg(21, MqttReasonCodes.PubRec.SUCCESS);
        assertThat(awaitingPubRelPacketsCtx.getAwaitingPacket(20).isPersisted()).isTrue();
        assertThat(awaitingPubRelPacketsCtx.getAwaitingPacket(21).isPersisted()).isTrue();
    }

    @Test
    public void givenMqtt311Qos2MsgBeingPersisted_whenDuplicateReceived_thenLaterPubRecsAreNotHeldBack() {
        when(publishMsgValidationService.validatePubMsg(any(), any())).thenReturn(true);
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_3_1_1);
        when(ctx.getClientId()).thenReturn("clientId");
        when(ctx.getSessionId()).thenReturn(UUID.randomUUID());

        processPublishMsgs(2, 1);
        processPublishMsgs(2, 1); // retransmitted while the first one is still being persisted
        processPublishMsgs(2, 2);
        List<TbQueueCallback> callbacks = capturePersistCallbacks(2);
        callbacks.get(0).onSuccess(null);
        callbacks.get(1).onSuccess(null);
        processPubResponsesSentToActor(2);

        InOrder inOrder = inOrder(mqttMessageGenerator);
        inOrder.verify(mqttMessageGenerator).createPubRecMsg(1, null);
        inOrder.verify(mqttMessageGenerator).createPubRecMsg(2, null);
        assertThat(queueSize(ctx.getPubResponseProcessingCtx().getQos2PubRecResponseMessages())).isZero();
    }

    @Test
    public void givenPersistedQos2MsgAwaitingPubRel_whenDuplicateReceived_thenPubRecResentWithoutPersistingAgain() {
        givenMqtt5Session();
        processPublishMsgs(2, 1);
        capturePersistCallbacks(1).get(0).onSuccess(null);
        processPubResponsesSentToActor(1);

        processPublishMsgs(2, 1); // the client didn't get the PUBREC and retransmits
        processPubResponsesSentToActor(2);

        verify(mqttMessageGenerator, times(2)).createPubRecMsg(1, MqttReasonCodes.PubRec.SUCCESS);
        verify(msgDispatcherService, times(1)).persistPublishMsg(any(), any(), any(), any());
        assertThat(queueSize(ctx.getPubResponseProcessingCtx().getQos2PubRecResponseMessages())).isZero();
    }

    @Test
    public void givenMqtt5Qos2MsgFailedToPersist_whenClientReusesPacketId_thenNewMsgIsPersisted() {
        givenMqtt5Session();
        processPublishMsgs(2, 20);
        capturePersistCallbacks(1).get(0).onFailure(new RuntimeException("NOT_LEADER_OR_FOLLOWER"));
        processPubResponsesSentToActor(1);

        // after a PUBREC with a failure reason code the packet id is free for reuse (MQTT 5, 4.3.3)
        processPublishMsgs(2, 20);

        verify(msgDispatcherService, times(2)).persistPublishMsg(any(), any(), any(), any());
    }

    @Test
    public void givenMqtt311Qos1MsgFailedToPersist_whenFailureCallback_thenDisconnectClient() {
        when(publishMsgValidationService.validatePubMsg(any(), any())).thenReturn(true);
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_3_1_1);
        when(ctx.getClientId()).thenReturn("clientId");
        when(ctx.getSessionId()).thenReturn(UUID.randomUUID());
        processPublishMsgs(1, 1);

        capturePersistCallbacks(1).get(0).onFailure(new RuntimeException("NOT_LEADER_OR_FOLLOWER"));

        ArgumentCaptor<MqttDisconnectMsg> disconnectCaptor = ArgumentCaptor.forClass(MqttDisconnectMsg.class);
        verify(clientMqttActorManager, timeout(5000)).disconnect(eq("clientId"), disconnectCaptor.capture());
        assertThat(disconnectCaptor.getValue().getReason().getType()).isEqualTo(DisconnectReasonType.ON_ERROR);
        verify(actorRef, never()).tell(any());
    }

    private void givenMqtt5Session() {
        when(publishMsgValidationService.validatePubMsg(any(), any())).thenReturn(true);
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);
        when(ctx.getClientId()).thenReturn("clientId");
        when(ctx.getSessionId()).thenReturn(UUID.randomUUID());
    }

    private void processPublishMsgs(int qos, int... packetIds) {
        for (int packetId : packetIds) {
            mqttPublishHandler.process(ctx, createMqttPubMsg(getPublishMsg(packetId, qos)), actorRef);
        }
    }

    private List<TbQueueCallback> capturePersistCallbacks(int expectedCount) {
        ArgumentCaptor<TbQueueCallback> callbackCaptor = ArgumentCaptor.forClass(TbQueueCallback.class);
        verify(msgDispatcherService, times(expectedCount)).persistPublishMsg(any(), any(), any(), callbackCaptor.capture());
        return callbackCaptor.getAllValues();
    }

    // plays the client actor role: hands the responses produced by the persist callbacks back to the handler
    private void processPubResponsesSentToActor(int expectedTotalCount) {
        ArgumentCaptor<TbActorMsg> actorMsgCaptor = ArgumentCaptor.forClass(TbActorMsg.class);
        verify(actorRef, timeout(5000).times(expectedTotalCount)).tell(actorMsgCaptor.capture());
        List<TbActorMsg> actorMsgs = actorMsgCaptor.getAllValues();
        List<TbActorMsg> newActorMsgs = actorMsgs.subList(processedActorMsgsCount, actorMsgs.size());
        processedActorMsgsCount = actorMsgs.size();
        for (TbActorMsg actorMsg : newActorMsgs) {
            if (actorMsg instanceof PubAckResponseMsg pubAckResponseMsg) {
                mqttPublishHandler.processPubAckResponse(ctx, pubAckResponseMsg);
            } else if (actorMsg instanceof PubRecResponseMsg pubRecResponseMsg) {
                mqttPublishHandler.processPubRecResponse(ctx, pubRecResponseMsg);
            }
        }
    }

    private int queueSize(OrderedProcessingQueue orderedProcessingQueue) {
        return ((OrderedProcessingQueueImpl) orderedProcessingQueue).getQueueSize().get();
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
