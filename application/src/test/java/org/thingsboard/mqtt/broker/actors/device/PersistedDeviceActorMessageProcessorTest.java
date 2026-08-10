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
package org.thingsboard.mqtt.broker.actors.device;

import io.netty.handler.codec.mqtt.MqttProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.ClientActorContext;
import org.thingsboard.mqtt.broker.actors.TbActorCtx;
import org.thingsboard.mqtt.broker.actors.device.messages.DeliverPersistedMessagesEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.DeviceConnectedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.IncomingPublishMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketAcknowledgedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketCompletedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketReceivedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketReceivedNoDeliveryEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.QuotaDeferredRetryMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.SharedSubscriptionEventMsg;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.dto.SharedSubscriptionPublishPacket;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.limits.QuotaGrant;
import org.thingsboard.mqtt.broker.service.limits.ThroughputQuotaService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionCacheService;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PersistedDeviceActorMessageProcessorTest {

    private static final String SS_TEST_KEY = "testKey";
    private static final String CLIENT_ID = "client";

    PersistedDeviceActorMessageProcessor persistedDeviceActorMessageProcessor;

    ActorSystemContext systemContext;
    DeviceMsgService deviceMsgService;
    MqttMsgDeliveryService mqttMsgDeliveryService;
    ClientMqttActorManager clientMqttActorManager;
    ClientLogger clientLogger;
    DeviceActorConfiguration deviceActorConfig;
    SharedSubscriptionCacheService sharedSubscriptionCacheService;
    ThroughputQuotaService throughputQuotaService;
    TbMessageStatsReportClient tbMessageStatsReportClient;

    @Before
    public void setUp() throws Exception {
        systemContext = mock(ActorSystemContext.class);

        deviceMsgService = mock(DeviceMsgService.class);
        mqttMsgDeliveryService = mock(MqttMsgDeliveryService.class);
        clientMqttActorManager = mock(ClientMqttActorManager.class);
        clientLogger = mock(ClientLogger.class);
        deviceActorConfig = mock(DeviceActorConfiguration.class);
        sharedSubscriptionCacheService = mock(SharedSubscriptionCacheService.class);
        ClientActorContext clientActorContext = mock(ClientActorContext.class);

        when(systemContext.getDeviceMsgService()).thenReturn(deviceMsgService);
        when(systemContext.getMqttMsgDeliveryService()).thenReturn(mqttMsgDeliveryService);
        when(systemContext.getClientActorContext()).thenReturn(clientActorContext);
        when(clientActorContext.getClientLogger()).thenReturn(clientLogger);
        when(systemContext.getDeviceActorConfiguration()).thenReturn(deviceActorConfig);
        when(systemContext.getSharedSubscriptionCacheService()).thenReturn(sharedSubscriptionCacheService);

        throughputQuotaService = mock(ThroughputQuotaService.class);
        tbMessageStatsReportClient = mock(TbMessageStatsReportClient.class);
        when(systemContext.getThroughputQuotaService()).thenReturn(throughputQuotaService);
        when(systemContext.getTbMessageStatsReportClient()).thenReturn(tbMessageStatsReportClient);
        lenient().when(throughputQuotaService.tryConsumeOutgoingDeferrable(anyInt()))
                .thenAnswer(inv -> new QuotaGrant(inv.getArgument(0), false));

        this.persistedDeviceActorMessageProcessor = spy(new PersistedDeviceActorMessageProcessor(systemContext, CLIENT_ID));
    }

    @After
    public void tearDown() {
    }

    @Test
    public void givenDeviceConnectedEventMsg_whenProcessDeviceConnect_thenSuccess() {
        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        TbActorCtx actorCtx = mock(TbActorCtx.class);
        var devicePublishMsgList = CompletableFuture.completedStage(List.of(DevicePublishMsg.builder().build()));
        when(deviceMsgService.findPersistedMessages(anyString())).thenReturn(devicePublishMsgList);

        persistedDeviceActorMessageProcessor.processDeviceConnect(actorCtx, new DeviceConnectedEventMsg(ctx));

        verify(deviceMsgService).findPersistedMessages(eq(CLIENT_ID));
        assertEquals(persistedDeviceActorMessageProcessor.getSessionCtx(), ctx);
    }

    @Test
    public void givenSharedSubscriptionEventMsgAndAlreadyConnectedClient_whenProcessingSharedSubscriptions_thenDoNothing() {
        TbActorCtx actorCtx = mock(TbActorCtx.class);
        TopicSharedSubscription sharedSubscription = new TopicSharedSubscription("tf", "g1", 2);
        SharedSubscriptionEventMsg msg = new SharedSubscriptionEventMsg(
                Set.of(
                        sharedSubscription
                )
        );
        int expectedPacketId = 10;
        when(deviceMsgService.getLastPacketId(CLIENT_ID)).thenReturn(CompletableFuture.completedStage(expectedPacketId));
        when(deviceMsgService.saveLastPacketId(eq(CLIENT_ID), anyInt())).thenReturn(CompletableFuture.completedStage("OK"));
        when(sharedSubscriptionCacheService.isAnyOtherDeviceClientConnected(eq(CLIENT_ID), eq(sharedSubscription))).thenReturn(true);

        persistedDeviceActorMessageProcessor.processSharedSubscriptions(actorCtx, msg);

        verify(deviceMsgService).getLastPacketId(eq(CLIENT_ID));
        verify(deviceMsgService).saveLastPacketId(CLIENT_ID, expectedPacketId);
        verifyNoMoreInteractions(deviceMsgService);
    }

    @Test
    public void givenSharedSubscriptionEventMsgAndQosZero_whenProcessingSharedSubscriptions_thenDoNothing() {
        TbActorCtx actorCtx = mock(TbActorCtx.class);
        TopicSharedSubscription sharedSubscription = new TopicSharedSubscription("tf", "g1");
        SharedSubscriptionEventMsg msg = new SharedSubscriptionEventMsg(
                Set.of(
                        sharedSubscription
                )
        );
        int expectedPacketId = 10;
        when(deviceMsgService.getLastPacketId(CLIENT_ID)).thenReturn(CompletableFuture.completedStage(expectedPacketId));
        when(deviceMsgService.saveLastPacketId(eq(CLIENT_ID), anyInt())).thenReturn(CompletableFuture.completedStage("OK"));
        when(sharedSubscriptionCacheService.isAnyOtherDeviceClientConnected(eq(CLIENT_ID), eq(sharedSubscription))).thenReturn(false);

        persistedDeviceActorMessageProcessor.processSharedSubscriptions(actorCtx, msg);

        verify(deviceMsgService).getLastPacketId(CLIENT_ID);
        verify(deviceMsgService).saveLastPacketId(CLIENT_ID, expectedPacketId);
        verifyNoMoreInteractions(deviceMsgService);
    }

    @Test
    public void givenSharedSubscriptionEventMsg_whenProcessingSharedSubscriptions_thenVerifiedMethodExecution() {
        TbActorCtx actorCtx = mock(TbActorCtx.class);
        DevicePublishMsg devicePublishMsg = DevicePublishMsg
                .builder()
                .packetId(1)
                .qos(1)
                .packetType(PersistedPacketType.PUBLISH)
                .time(System.currentTimeMillis())
                .properties(MqttProperties.NO_PROPERTIES)
                .build();

        int lastPacketId = 1;
        when(deviceMsgService.getLastPacketId(CLIENT_ID)).thenReturn(CompletableFuture.completedStage(lastPacketId));
        when(deviceMsgService.findPersistedMessages("ss_g1_tf")).thenReturn(CompletableFuture.completedStage(List.of(devicePublishMsg)));
        when(deviceMsgService.removeLastPacketId("ss_g1_tf")).thenReturn(CompletableFuture.completedStage(1L));
        when(deviceMsgService.saveLastPacketId(eq(CLIENT_ID), anyInt())).thenReturn(CompletableFuture.completedStage("OK"));

        TopicSharedSubscription sharedSubscription = new TopicSharedSubscription("tf", "g1", 1);
        SharedSubscriptionEventMsg msg = new SharedSubscriptionEventMsg(
                Set.of(
                        sharedSubscription
                )
        );
        when(sharedSubscriptionCacheService.isAnyOtherDeviceClientConnected(eq(CLIENT_ID), eq(sharedSubscription))).thenReturn(false);

        persistedDeviceActorMessageProcessor.processSharedSubscriptions(actorCtx, msg);

        verify(deviceMsgService).getLastPacketId(eq(CLIENT_ID));
        verify(deviceMsgService).findPersistedMessages(eq("ss_g1_tf"));
        verify(deviceMsgService).removeLastPacketId(eq("ss_g1_tf"));
        verify(deviceMsgService).saveLastPacketId(eq(CLIENT_ID), eq(lastPacketId + 1));
    }

    @Test
    public void givenTopicSharedSubscriptionAndMessages_whenUpdateMessagesBeforePublish_AndReturnLastPacketId_thenGetExpectedResult() {
        DevicePublishMsg msg1 = DevicePublishMsg
                .builder()
                .packetId(100)
                .qos(0)
                .build();
        DevicePublishMsg msg2 = DevicePublishMsg
                .builder()
                .packetId(200)
                .qos(1)
                .build();
        DevicePublishMsg msg3 = DevicePublishMsg
                .builder()
                .packetId(300)
                .qos(2)
                .build();

        int lastPacketId = 1;
        TopicSharedSubscription topicSharedSubscription = new TopicSharedSubscription("tf", "g1", 1);
        List<DevicePublishMsg> devicePublishMsgList = List.of(msg1, msg2, msg3);

        persistedDeviceActorMessageProcessor.updateMessagesBeforePublishAndReturnLastPacketId(lastPacketId, topicSharedSubscription, devicePublishMsgList);

        assertEquals(2, devicePublishMsgList.get(0).getPacketId().intValue());
        assertEquals(0, devicePublishMsgList.get(0).getQos().intValue());

        assertEquals(3, devicePublishMsgList.get(1).getPacketId().intValue());
        assertEquals(1, devicePublishMsgList.get(1).getQos().intValue());

        assertEquals(4, devicePublishMsgList.get(2).getPacketId().intValue());
        assertEquals(1, devicePublishMsgList.get(2).getQos().intValue());

        ConcurrentMap<Integer, SharedSubscriptionPublishPacket> sentPacketIdsFromSharedSubscription = persistedDeviceActorMessageProcessor.getSentPacketIdsFromSharedSubscription();
        assertEquals(3, sentPacketIdsFromSharedSubscription.size());
        SharedSubscriptionPublishPacket sharedSubscriptionPublishPacket = sentPacketIdsFromSharedSubscription.get(3);
        assertEquals(sharedSubscriptionPublishPacket, new SharedSubscriptionPublishPacket("ss_g1_tf", 200));
    }

    @Test
    public void givenDevicePublishMsg_whenDeliverPersistedPubrelMsg_thenVerifiedMethodExecution() {
        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        persistedDeviceActorMessageProcessor.setSessionCtx(ctx);
        DevicePublishMsg devicePublishMsg = DevicePublishMsg
                .builder()
                .packetId(1)
                .packetType(PersistedPacketType.PUBREL)
                .build();
        persistedDeviceActorMessageProcessor.deliverPersistedMsg(devicePublishMsg);

        verify(mqttMsgDeliveryService, never()).sendPublishMsgToClient(any(), any(), anyBoolean());
        verify(mqttMsgDeliveryService).sendPubRelMsgToClient(eq(ctx), eq(1));
    }

    @Test
    public void givenExpiredDevicePublishMsg_whenDeliverPersistedPublishMsg_thenVerifiedMethodExecution() {
        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        persistedDeviceActorMessageProcessor.setSessionCtx(ctx);
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID, -100));
        DevicePublishMsg devicePublishMsg = DevicePublishMsg
                .builder()
                .packetId(1)
                .packetType(PersistedPacketType.PUBLISH)
                .time(System.currentTimeMillis())
                .properties(properties)
                .build();
        persistedDeviceActorMessageProcessor.deliverPersistedMsg(devicePublishMsg);

        verify(mqttMsgDeliveryService, never()).sendPublishMsgToClient(any(), any(), anyBoolean());
        verify(mqttMsgDeliveryService, never()).sendPubRelMsgToClient(any(), anyInt());
    }

    @Test
    public void givenDevicePublishMsg_whenDeliverPersistedPublishMsg_thenVerifiedMethodExecution() {
        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        persistedDeviceActorMessageProcessor.setSessionCtx(ctx);
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID, 100));
        DevicePublishMsg devicePublishMsg = DevicePublishMsg
                .builder()
                .packetId(101)
                .packetType(PersistedPacketType.PUBLISH)
                .time(System.currentTimeMillis())
                .qos(1)
                .properties(properties)
                .build();
        persistedDeviceActorMessageProcessor.deliverPersistedMsg(devicePublishMsg);

        verify(mqttMsgDeliveryService).sendPublishMsgToClient(any(), any(), anyBoolean());
        verify(mqttMsgDeliveryService, never()).sendPubRelMsgToClient(any(), anyInt());

        assertEquals(1, persistedDeviceActorMessageProcessor.getInFlightPacketIds().size());
    }

    @Test
    public void givenQuotaExhausted_whenDeliverPersistedMsg_thenSettleWithoutSendOrCounterMutation() {
        when(throughputQuotaService.tryConsumeOutgoingDeferrable(1)).thenReturn(new QuotaGrant(0, true));
        when(deviceMsgService.removePersistedMessage(CLIENT_ID, 5)).thenReturn(CompletableFuture.completedFuture(null));
        persistedDeviceActorMessageProcessor.setSessionCtx(mock(ClientSessionCtx.class));

        DevicePublishMsg msg = DevicePublishMsg.builder()
                .packetId(5)
                .packetType(PersistedPacketType.PUBLISH)
                .time(System.currentTimeMillis())
                .properties(new MqttProperties())
                .build();
        persistedDeviceActorMessageProcessor.deliverPersistedMsg(msg);

        verify(mqttMsgDeliveryService, never()).sendPublishMsgToClient(any(), any(), anyBoolean());
        verify(deviceMsgService).removePersistedMessage(CLIENT_ID, 5);
        verify(tbMessageStatsReportClient).reportDroppedMsgs();
        assertEquals(0, persistedDeviceActorMessageProcessor.getUnacknowledgedMsgCounter().get());
        assertTrue(persistedDeviceActorMessageProcessor.getInFlightPacketIds().isEmpty());
    }

    @Test
    public void givenQuotaExhaustedForSharedSubMsg_whenDeliver_thenRemoveByShareKeyAndOriginalPacketId() {
        when(throughputQuotaService.tryConsumeOutgoingDeferrable(1)).thenReturn(new QuotaGrant(0, true));
        when(deviceMsgService.removePersistedMessage(SS_TEST_KEY, 5)).thenReturn(CompletableFuture.completedFuture(null));
        persistedDeviceActorMessageProcessor.setSessionCtx(mock(ClientSessionCtx.class));
        persistedDeviceActorMessageProcessor.getSentPacketIdsFromSharedSubscription()
                .put(77, new SharedSubscriptionPublishPacket(SS_TEST_KEY, 5));

        DevicePublishMsg msg = DevicePublishMsg.builder()
                .packetId(77)
                .packetType(PersistedPacketType.PUBLISH)
                .time(System.currentTimeMillis())
                .properties(new MqttProperties())
                .build();
        persistedDeviceActorMessageProcessor.deliverPersistedMsg(msg);

        verify(deviceMsgService).removePersistedMessage(SS_TEST_KEY, 5);
        verify(tbMessageStatsReportClient).reportDroppedMsgs();
    }

    @Test
    public void givenSession_whenProcessDeviceDisconnect_thenSuccess() {
        TbActorCtx tbActorCtx = mock(TbActorCtx.class);
        persistedDeviceActorMessageProcessor.processDeviceDisconnect(tbActorCtx);

        assertNull(persistedDeviceActorMessageProcessor.getSessionCtx());
        assertNotNull(persistedDeviceActorMessageProcessor.getStopActorCommandUUID());
    }

    @Test
    public void givenDevicePublishMsg_whenMessageIsExpired_thenStopProcessing() {
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID, -100));

        DevicePublishMsg devicePublishMsg = DevicePublishMsg
                .builder()
                .packetId(2)
                .time(System.currentTimeMillis())
                .properties(properties)
                .build();

        persistedDeviceActorMessageProcessor.processIncomingMsg(new IncomingPublishMsg(devicePublishMsg));

        verify(mqttMsgDeliveryService, never()).sendPublishMsgToClient(any(), any(), anyBoolean());
    }

    @Test
    public void givenDevicePublishMsg_whenChannelNonWritable_thenStopProcessing() {
        persistedDeviceActorMessageProcessor.processChannelNonWritable();
        MqttProperties properties = new MqttProperties();

        DevicePublishMsg devicePublishMsg = DevicePublishMsg
                .builder()
                .packetId(2)
                .time(System.currentTimeMillis())
                .properties(properties)
                .build();

        persistedDeviceActorMessageProcessor.processIncomingMsg(new IncomingPublishMsg(devicePublishMsg));

        verify(mqttMsgDeliveryService, never()).sendPublishMsgToClient(any(), any(), anyBoolean());
    }

    @Test
    public void givenDevicePublishMsg_whenMessageIsNotExpired_thenProcessingIsCorrect() {
        persistedDeviceActorMessageProcessor.setSessionCtx(mock(ClientSessionCtx.class));
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID, 100));

        DevicePublishMsg devicePublishMsg = DevicePublishMsg
                .builder()
                .packetId(1)
                .time(System.currentTimeMillis())
                .properties(properties)
                .clientId(CLIENT_ID)
                .payload(null)
                .qos(1)
                .build();

        persistedDeviceActorMessageProcessor.processIncomingMsg(new IncomingPublishMsg(devicePublishMsg));

        verify(mqttMsgDeliveryService).sendPublishMsgToClient(any(), any(), anyBoolean());

        assertTrue(persistedDeviceActorMessageProcessor.getInFlightPacketIds().contains(1));
    }

    @Test
    public void givenPacketAcknowledgedEventMsg_whenProcessPacketAcknowledge_thenVerifiedMethodExecution() {
        when(deviceMsgService.removePersistedMessage(anyString(), anyInt())).thenReturn(CompletableFuture.completedStage("OK"));
        persistedDeviceActorMessageProcessor.getInFlightPacketIds().add(1);
        persistedDeviceActorMessageProcessor.processPacketAcknowledge(new PacketAcknowledgedEventMsg(1));
        verify(deviceMsgService).removePersistedMessage(eq(CLIENT_ID), eq(1));
        assertTrue(persistedDeviceActorMessageProcessor.getInFlightPacketIds().isEmpty());
    }

    @Test
    public void givenPacketAcknowledgedEventMsgForSharedSubscription_whenProcessPacketAcknowledge_thenVerifiedMethodExecution() {
        when(deviceMsgService.removePersistedMessage(anyString(), anyInt())).thenReturn(CompletableFuture.completedStage("OK"));
        persistedDeviceActorMessageProcessor.getInFlightPacketIds().add(1);

        Map<Integer, SharedSubscriptionPublishPacket> sentPacketIdsFromSharedSubscription = persistedDeviceActorMessageProcessor.getSentPacketIdsFromSharedSubscription();
        sentPacketIdsFromSharedSubscription.put(1, new SharedSubscriptionPublishPacket(SS_TEST_KEY, 200));

        persistedDeviceActorMessageProcessor.processPacketAcknowledge(new PacketAcknowledgedEventMsg(1));
        verify(deviceMsgService).removePersistedMessage(eq(SS_TEST_KEY), eq(200));
        assertTrue(persistedDeviceActorMessageProcessor.getInFlightPacketIds().isEmpty());
    }

    @Test
    public void givenPacketReceivedEventMsg_whenProcessPacketReceived_thenVerifiedMethodExecution() {
        when(deviceMsgService.updatePacketReceived(anyString(), anyInt())).thenReturn(CompletableFuture.completedStage("OK"));
        persistedDeviceActorMessageProcessor.getInFlightPacketIds().add(1);

        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        persistedDeviceActorMessageProcessor.setSessionCtx(ctx);

        persistedDeviceActorMessageProcessor.processPacketReceived(new PacketReceivedEventMsg(1));

        verify(deviceMsgService).updatePacketReceived(eq(CLIENT_ID), eq(1));
        verify(mqttMsgDeliveryService).sendPubRelMsgToClient(eq(ctx), eq(1));

        assertTrue(persistedDeviceActorMessageProcessor.getInFlightPacketIds().isEmpty());
    }

    @Test
    public void givenPacketReceivedEventMsgForSharedSubscription_whenProcessPacketReceived_thenVerifiedMethodExecution() {
        when(deviceMsgService.updatePacketReceived(anyString(), anyInt())).thenReturn(CompletableFuture.completedStage("OK"));
        persistedDeviceActorMessageProcessor.getInFlightPacketIds().add(1);

        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        persistedDeviceActorMessageProcessor.setSessionCtx(ctx);

        Map<Integer, SharedSubscriptionPublishPacket> sentPacketIdsFromSharedSubscription = persistedDeviceActorMessageProcessor.getSentPacketIdsFromSharedSubscription();
        sentPacketIdsFromSharedSubscription.put(1, new SharedSubscriptionPublishPacket(SS_TEST_KEY, 200));

        persistedDeviceActorMessageProcessor.processPacketReceived(new PacketReceivedEventMsg(1));

        verify(deviceMsgService).updatePacketReceived(eq(SS_TEST_KEY), eq(200));
        verify(mqttMsgDeliveryService).sendPubRelMsgToClient(eq(ctx), eq(1));

        assertTrue(persistedDeviceActorMessageProcessor.getInFlightPacketIds().isEmpty());
    }

    @Test
    public void givenPacketReceivedNoDeliveryEventMsg_whenProcessPacketReceivedNoDelivery_thenVerifiedMethodExecution() {
        when(deviceMsgService.removePersistedMessage(anyString(), anyInt())).thenReturn(CompletableFuture.completedStage("OK"));
        persistedDeviceActorMessageProcessor.getInFlightPacketIds().add(1);

        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        persistedDeviceActorMessageProcessor.setSessionCtx(ctx);

        persistedDeviceActorMessageProcessor.processPacketReceivedNoDelivery(new PacketReceivedNoDeliveryEventMsg(1));

        verify(deviceMsgService).removePersistedMessage(eq(CLIENT_ID), eq(1));
        assertTrue(persistedDeviceActorMessageProcessor.getInFlightPacketIds().isEmpty());
    }

    @Test
    public void givenPacketReceivedNoDeliveryEventMsgForSharedSubscription_whenProcessPacketReceivedNoDelivery_thenVerifiedMethodExecution() {
        when(deviceMsgService.removePersistedMessage(anyString(), anyInt())).thenReturn(CompletableFuture.completedStage("OK"));
        persistedDeviceActorMessageProcessor.getInFlightPacketIds().add(1);

        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        persistedDeviceActorMessageProcessor.setSessionCtx(ctx);

        Map<Integer, SharedSubscriptionPublishPacket> sentPacketIdsFromSharedSubscription = persistedDeviceActorMessageProcessor.getSentPacketIdsFromSharedSubscription();
        sentPacketIdsFromSharedSubscription.put(1, new SharedSubscriptionPublishPacket(SS_TEST_KEY, 200));

        persistedDeviceActorMessageProcessor.processPacketReceivedNoDelivery(new PacketReceivedNoDeliveryEventMsg(1));

        verify(deviceMsgService).removePersistedMessage(eq(SS_TEST_KEY), eq(200));
        assertTrue(persistedDeviceActorMessageProcessor.getInFlightPacketIds().isEmpty());
    }

    @Test
    public void givenPacketCompletedEventMsg_whenProcessPacketComplete_thenVerifiedMethodExecution() {
        when(deviceMsgService.removePersistedMessage(anyString(), anyInt())).thenReturn(CompletableFuture.completedStage("OK"));
        persistedDeviceActorMessageProcessor.processPacketComplete(new PacketCompletedEventMsg(1));

        verify(deviceMsgService).removePersistedMessage(eq(CLIENT_ID), eq(1));
    }

    @Test
    public void givenPacketCompletedEventMsgForSharedSubscription_whenProcessPacketComplete_thenVerifiedMethodExecution() {
        when(deviceMsgService.removePersistedMessage(anyString(), anyInt())).thenReturn(CompletableFuture.completedStage("OK"));
        Map<Integer, SharedSubscriptionPublishPacket> sentPacketIdsFromSharedSubscription = persistedDeviceActorMessageProcessor.getSentPacketIdsFromSharedSubscription();
        sentPacketIdsFromSharedSubscription.put(1, new SharedSubscriptionPublishPacket(SS_TEST_KEY, 200));

        persistedDeviceActorMessageProcessor.processPacketComplete(new PacketCompletedEventMsg(1));

        verify(deviceMsgService).removePersistedMessage(eq(SS_TEST_KEY), eq(200));
    }

    @Test
    public void givenInitialPacketId_whenUpdateMessagesBeforePublishAndReturnLastPacketId_thenReturnExpectedResult() {
        int lastPacketId = 0; // no last_packet_id in redis
        var msg = DevicePublishMsg.builder().packetId(BrokerConstants.BLANK_PACKET_ID).qos(0).build();

        TopicSharedSubscription topicSharedSubscription = new TopicSharedSubscription("tf", "g1", 1);
        lastPacketId = persistedDeviceActorMessageProcessor
                .updateMessagesBeforePublishAndReturnLastPacketId(lastPacketId, topicSharedSubscription, List.of(msg));
        assertEquals(1, lastPacketId);
        assertEquals(1, (int) msg.getPacketId());
        assertEquals(0, (int) msg.getQos());
    }

    @Test
    public void givenPacketId_whenUpdateMessagesBeforePublishAndReturnLastPacketId_thenReturnExpectedResult() {
        int lastPacketId = 100;
        var msg = DevicePublishMsg.builder().packetId(BrokerConstants.BLANK_PACKET_ID).qos(1).build();

        TopicSharedSubscription topicSharedSubscription = new TopicSharedSubscription("tf", "g1", 1);
        lastPacketId = persistedDeviceActorMessageProcessor
                .updateMessagesBeforePublishAndReturnLastPacketId(lastPacketId, topicSharedSubscription, List.of(msg));
        assertEquals(101, lastPacketId);
        assertEquals(101, (int) msg.getPacketId());
        assertEquals(1, (int) msg.getQos());
    }

    @Test
    public void givenMaxPacketId_whenUpdateMessagesBeforePublishAndReturnLastPacketId_thenReturnExpectedResult() {
        int lastPacketId = BrokerConstants.MAX_PACKET_ID;
        var msg = DevicePublishMsg.builder().packetId(BrokerConstants.BLANK_PACKET_ID).qos(1).build();

        TopicSharedSubscription topicSharedSubscription = new TopicSharedSubscription("tf", "g1", 1);
        lastPacketId = persistedDeviceActorMessageProcessor
                .updateMessagesBeforePublishAndReturnLastPacketId(lastPacketId, topicSharedSubscription, List.of(msg));
        assertEquals(1, lastPacketId);
        assertEquals(1, (int) msg.getPacketId());
        assertEquals(1, (int) msg.getQos());
    }

    @Test
    public void givenClient_whenProcessRemovePersistedMessages_thenExecuteRemove() {
        when(deviceMsgService.removePersistedMessages(eq(CLIENT_ID))).thenReturn(new CompletableFuture<>());

        persistedDeviceActorMessageProcessor.processRemovePersistedMessages();

        verify(deviceMsgService).removePersistedMessages(CLIENT_ID);
    }

    @Test
    public void givenWritableClient_whenProcessChannelNonWritable_thenChangeState() {
        assertTrue(persistedDeviceActorMessageProcessor.isChannelWritable());

        persistedDeviceActorMessageProcessor.processChannelNonWritable();

        assertFalse(persistedDeviceActorMessageProcessor.isChannelWritable());
    }

    @Test
    public void givenNotWritableClient_whenProcessChannelWritable_thenFindPersistedMessages() {
        TbActorCtx actorCtx = mock(TbActorCtx.class);
        when(deviceMsgService.findPersistedMessages(eq(CLIENT_ID))).thenReturn(new CompletableFuture<>());
        persistedDeviceActorMessageProcessor.setChannelWritable(false);

        persistedDeviceActorMessageProcessor.processChannelWritable(actorCtx);

        verify(deviceMsgService).findPersistedMessages(eq(CLIENT_ID));
    }

    @Test
    public void givenNotWritableClient_whenProcessDeliverPersistedMessages_thenChangeState() {
        persistedDeviceActorMessageProcessor.setChannelWritable(false);

        persistedDeviceActorMessageProcessor.processDeliverPersistedMessages(new DeliverPersistedMessagesEventMsg(List.of()));
        assertTrue(persistedDeviceActorMessageProcessor.isChannelWritable());
        verify(mqttMsgDeliveryService, never()).sendPublishMsgToClient(any(), any(), anyBoolean());
    }

    @Test
    public void givenLocalShortfall_whenDeliverPersistedMsg_thenDeferWithoutRemovingOrDropping() {
        when(throughputQuotaService.tryConsumeOutgoingDeferrable(1)).thenReturn(new QuotaGrant(0, false));
        persistedDeviceActorMessageProcessor.setSessionCtx(mock(ClientSessionCtx.class));
        persistedDeviceActorMessageProcessor.setActorCtx(mock(TbActorCtx.class));

        DevicePublishMsg msg = DevicePublishMsg.builder()
                .packetId(5)
                .packetType(PersistedPacketType.PUBLISH)
                .time(System.currentTimeMillis())
                .properties(new MqttProperties())
                .build();
        persistedDeviceActorMessageProcessor.deliverPersistedMsg(msg);

        verify(mqttMsgDeliveryService, never()).sendPublishMsgToClient(any(), any(), anyBoolean());
        verify(deviceMsgService, never()).removePersistedMessage(any(), anyInt());
        verify(tbMessageStatsReportClient, never()).reportDroppedMsgs();
        assertEquals("the message is queued for retry, not destroyed",
                1, persistedDeviceActorMessageProcessor.getDeliveryQueue().size());
        assertTrue(persistedDeviceActorMessageProcessor.getInFlightPacketIds().isEmpty());
        assertEquals(0, persistedDeviceActorMessageProcessor.getUnacknowledgedMsgCounter().get());
    }

    @Test
    public void givenLocalShortfall_whenManyMessagesDeferred_thenOneRetryScheduled() {
        when(throughputQuotaService.tryConsumeOutgoingDeferrable(1)).thenReturn(new QuotaGrant(0, false));
        persistedDeviceActorMessageProcessor.setSessionCtx(mock(ClientSessionCtx.class));
        TbActorCtx actorCtx = mock(TbActorCtx.class);
        persistedDeviceActorMessageProcessor.setActorCtx(actorCtx);

        for (int packetId = 1; packetId <= 3; packetId++) {
            persistedDeviceActorMessageProcessor.deliverPersistedMsg(DevicePublishMsg.builder()
                    .packetId(packetId)
                    .packetType(PersistedPacketType.PUBLISH)
                    .time(System.currentTimeMillis())
                    .properties(new MqttProperties())
                    .build());
        }

        verify(systemContext, times(1)).scheduleMsgWithDelay(
                eq(actorCtx), any(QuotaDeferredRetryMsg.class), eq(ThroughputQuotaService.DEFER_RETRY_MS));
        assertEquals(3, persistedDeviceActorMessageProcessor.getDeliveryQueue().size());
    }

    @Test
    public void givenDeferredMsgAtQueueHead_whenDrainResumes_thenOrderPreserved() {
        when(throughputQuotaService.tryConsumeOutgoingDeferrable(1))
                .thenReturn(new QuotaGrant(0, false))   // first attempt defers packet 1
                .thenReturn(new QuotaGrant(1, false))   // retry delivers packet 1
                .thenReturn(new QuotaGrant(1, false));  // then packet 2
        persistedDeviceActorMessageProcessor.setSessionCtx(mock(ClientSessionCtx.class));
        persistedDeviceActorMessageProcessor.setActorCtx(mock(TbActorCtx.class));

        persistedDeviceActorMessageProcessor.getDeliveryQueue().addLast(publish(1));
        persistedDeviceActorMessageProcessor.getDeliveryQueue().addLast(publish(2));

        persistedDeviceActorMessageProcessor.processDeliveryQueue();          // defers on packet 1, stops
        assertEquals("drain stops at the deferral instead of overtaking it",
                2, persistedDeviceActorMessageProcessor.getDeliveryQueue().size());
        assertEquals(1, persistedDeviceActorMessageProcessor.getDeliveryQueue().peekFirst().getPacketId().intValue());

        persistedDeviceActorMessageProcessor.processQuotaDeferredRetry();     // resumes in order
        assertTrue(persistedDeviceActorMessageProcessor.getDeliveryQueue().isEmpty());

        InOrder inOrder = inOrder(mqttMsgDeliveryService);
        inOrder.verify(mqttMsgDeliveryService).sendPublishMsgToClient(any(), argThat(m -> m.getPacketId() == 1), anyBoolean());
        inOrder.verify(mqttMsgDeliveryService).sendPublishMsgToClient(any(), argThat(m -> m.getPacketId() == 2), anyBoolean());
    }

    @Test
    public void givenQuotaRetryScheduled_whenLiveMessageArrives_thenEnqueuedBehindBacklogInsteadOfSent() {
        when(throughputQuotaService.tryConsumeOutgoingDeferrable(1))
                .thenReturn(new QuotaGrant(0, false))   // defers packet 1, schedules the retry
                .thenReturn(new QuotaGrant(1, false));  // would grant the live message if it were ever consulted
        persistedDeviceActorMessageProcessor.setSessionCtx(mock(ClientSessionCtx.class));
        persistedDeviceActorMessageProcessor.setActorCtx(mock(TbActorCtx.class));

        persistedDeviceActorMessageProcessor.deliverPersistedMsg(publish(1)); // defers -> quotaRetryScheduled = true

        DevicePublishMsg liveMsg = publish(2);
        persistedDeviceActorMessageProcessor.processIncomingMsg(new IncomingPublishMsg(liveMsg));

        // the live message must not overtake the older deferred one: no charge, no send, just enqueued behind it
        verify(throughputQuotaService, times(1)).tryConsumeOutgoingDeferrable(1);
        verify(mqttMsgDeliveryService, never()).sendPublishMsgToClient(any(), any(), anyBoolean());
        assertEquals("deferred backlog stays ahead of the live arrival",
                2, persistedDeviceActorMessageProcessor.getDeliveryQueue().size());
        assertEquals(1, persistedDeviceActorMessageProcessor.getDeliveryQueue().peekFirst().getPacketId().intValue());
        assertEquals(2, persistedDeviceActorMessageProcessor.getDeliveryQueue().peekLast().getPacketId().intValue());
    }

    @Test
    public void givenDisconnected_whenQuotaRetryFires_thenNothingDelivered() {
        persistedDeviceActorMessageProcessor.setSessionCtx(null);
        persistedDeviceActorMessageProcessor.getDeliveryQueue().addLast(publish(1));

        persistedDeviceActorMessageProcessor.processQuotaDeferredRetry();

        verify(mqttMsgDeliveryService, never()).sendPublishMsgToClient(any(), any(), anyBoolean());
    }

    private DevicePublishMsg publish(int packetId) {
        return DevicePublishMsg.builder()
                .packetId(packetId)
                .packetType(PersistedPacketType.PUBLISH)
                .time(System.currentTimeMillis())
                .properties(new MqttProperties())
                .build();
    }

}
