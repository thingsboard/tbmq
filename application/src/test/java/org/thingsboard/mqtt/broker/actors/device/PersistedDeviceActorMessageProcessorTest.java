/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.TbActorCtx;
import org.thingsboard.mqtt.broker.actors.device.messages.DeviceConnectedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.IncomingPublishMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketAcknowledgedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketCompletedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketReceivedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.SharedSubscriptionEventMsg;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.dto.SharedSubscriptionPublishPacket;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PersistedDeviceActorMessageProcessorTest {

    private static final String SS_TEST_KEY = "testKey";
    private static final String CLIENT_ID = "client";

    PersistedDeviceActorMessageProcessor persistedDeviceActorMessageProcessor;

    DeviceMsgService deviceMsgService;
    PublishMsgDeliveryService publishMsgDeliveryService;
    ClientMqttActorManager clientMqttActorManager;
    ClientLogger clientLogger;
    DeviceActorConfiguration deviceActorConfig;
    SharedSubscriptionCacheService sharedSubscriptionCacheService;

    @Before
    public void setUp() throws Exception {
        ActorSystemContext actorSystemContext = mock(ActorSystemContext.class);

        deviceMsgService = mock(DeviceMsgService.class);
        publishMsgDeliveryService = mock(PublishMsgDeliveryService.class);
        clientMqttActorManager = mock(ClientMqttActorManager.class);
        clientLogger = mock(ClientLogger.class);
        deviceActorConfig = mock(DeviceActorConfiguration.class);
        sharedSubscriptionCacheService = mock(SharedSubscriptionCacheService.class);

        when(actorSystemContext.getDeviceMsgService()).thenReturn(deviceMsgService);
        when(actorSystemContext.getPublishMsgDeliveryService()).thenReturn(publishMsgDeliveryService);
        when(actorSystemContext.getClientMqttActorManager()).thenReturn(clientMqttActorManager);
        when(actorSystemContext.getClientLogger()).thenReturn(clientLogger);
        when(actorSystemContext.getDeviceActorConfiguration()).thenReturn(deviceActorConfig);
        when(actorSystemContext.getSharedSubscriptionCacheService()).thenReturn(sharedSubscriptionCacheService);

        this.persistedDeviceActorMessageProcessor = spy(new PersistedDeviceActorMessageProcessor(actorSystemContext, CLIENT_ID));
    }

    @After
    public void tearDown() {
    }

    @Test
    public void givenDeviceConnectedEventMsg_whenProcessDeviceConnect_thenSuccess() {
        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        var devicePublishMsgList = CompletableFuture.completedStage(List.of(DevicePublishMsg.builder().build()));
        when(deviceMsgService.findPersistedMessages(anyString())).thenReturn(devicePublishMsgList);

        persistedDeviceActorMessageProcessor.processDeviceConnect(new DeviceConnectedEventMsg(ctx));

        verify(deviceMsgService).findPersistedMessages(eq(CLIENT_ID));
        assertEquals(persistedDeviceActorMessageProcessor.getSessionCtx(), ctx);
    }

    @Test
    public void givenSharedSubscriptionEventMsgAndAlreadyConnectedClient_whenProcessingSharedSubscriptions_thenDoNothing() {
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

        persistedDeviceActorMessageProcessor.processingSharedSubscriptions(msg);

        verify(deviceMsgService).getLastPacketId(eq(CLIENT_ID));
        verify(deviceMsgService).saveLastPacketId(CLIENT_ID, expectedPacketId);
        verifyNoMoreInteractions(deviceMsgService);
    }

    @Test
    public void givenSharedSubscriptionEventMsgAndQosZero_whenProcessingSharedSubscriptions_thenDoNothing() {
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

        persistedDeviceActorMessageProcessor.processingSharedSubscriptions(msg);

        verify(deviceMsgService).getLastPacketId(CLIENT_ID);
        verify(deviceMsgService).saveLastPacketId(CLIENT_ID, expectedPacketId);
        verifyNoMoreInteractions(deviceMsgService);
    }

    @Test
    public void givenSharedSubscriptionEventMsg_whenProcessingSharedSubscriptions_thenVerifiedMethodExecution() {
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

        persistedDeviceActorMessageProcessor.processingSharedSubscriptions(msg);

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

        verify(publishMsgDeliveryService, never()).sendPublishMsgToClient(any(), any(), anyBoolean());
        verify(publishMsgDeliveryService).sendPubRelMsgToClient(eq(ctx), eq(1));
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

        verify(publishMsgDeliveryService, never()).sendPublishMsgToClient(any(), any(), anyBoolean());
        verify(publishMsgDeliveryService, never()).sendPubRelMsgToClient(any(), anyInt());
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

        verify(publishMsgDeliveryService).sendPublishMsgToClient(any(), any(), anyBoolean());
        verify(publishMsgDeliveryService, never()).sendPubRelMsgToClient(any(), anyInt());

        assertEquals(1, persistedDeviceActorMessageProcessor.getInFlightPacketIds().size());
        assertEquals(101L, persistedDeviceActorMessageProcessor.getLastPersistedMsgSentPacketId());
    }

    @Test
    public void givenSession_whenProcessDeviceDisconnect_thenSuccess() {
        TbActorCtx tbActorCtx = mock(TbActorCtx.class);
        persistedDeviceActorMessageProcessor.processDeviceDisconnect(tbActorCtx);

        assertNull(persistedDeviceActorMessageProcessor.getSessionCtx());
        assertEquals(0L, persistedDeviceActorMessageProcessor.getLastPersistedMsgSentPacketId());
        assertNotNull(persistedDeviceActorMessageProcessor.getStopActorCommandUUID());
    }

    @Test
    public void givenDevicePublishMsg_whenSerialNumberIsLessThanLastPersistedMsgSentSerialNumber_thenStopProcessing() {
        DevicePublishMsg devicePublishMsg = DevicePublishMsg.builder().packetId(0).build();

        persistedDeviceActorMessageProcessor.process(new IncomingPublishMsg(devicePublishMsg));

        verify(publishMsgDeliveryService, never()).sendPublishMsgToClient(any(), any(), anyBoolean());
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

        persistedDeviceActorMessageProcessor.process(new IncomingPublishMsg(devicePublishMsg));

        verify(publishMsgDeliveryService, never()).sendPublishMsgToClient(any(), any(), anyBoolean());
    }

    @Test
    public void givenDevicePublishMsg_whenMessageIsNotExpired_thenProcessingIsCorrect() {
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

        persistedDeviceActorMessageProcessor.process(new IncomingPublishMsg(devicePublishMsg));

        verify(publishMsgDeliveryService).sendPublishMsgToClient(any(), any(), anyBoolean());

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
        verify(publishMsgDeliveryService).sendPubRelMsgToClient(eq(ctx), eq(1));

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
        verify(publishMsgDeliveryService).sendPubRelMsgToClient(eq(ctx), eq(1));

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

}
