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
package org.thingsboard.mqtt.broker.actors.device;

import com.google.common.util.concurrent.Futures;
import io.netty.handler.codec.mqtt.MqttProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.TbActorCtx;
import org.thingsboard.mqtt.broker.actors.device.messages.DeviceConnectedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.IncomingPublishMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketAcknowledgedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketCompletedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketReceivedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.SharedSubscriptionEventMsg;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.dao.client.device.DevicePacketIdAndSerialNumberService;
import org.thingsboard.mqtt.broker.dao.client.device.DeviceSessionCtxService;
import org.thingsboard.mqtt.broker.dao.client.device.PacketIdAndSerialNumber;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.dto.PacketIdAndSerialNumberDto;
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
import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PersistedDeviceActorMessageProcessorTest {

    private static final String SS_TEST_KEY = "testKey";
    private static final String CLIENT = "client";

    PersistedDeviceActorMessageProcessor persistedDeviceActorMessageProcessor;

    DeviceMsgService deviceMsgService;
    DeviceSessionCtxService deviceSessionCtxService;
    DevicePacketIdAndSerialNumberService serialNumberService;
    PublishMsgDeliveryService publishMsgDeliveryService;
    ClientMqttActorManager clientMqttActorManager;
    ClientLogger clientLogger;
    DeviceActorConfiguration deviceActorConfig;
    SharedSubscriptionCacheService sharedSubscriptionCacheService;

    @Before
    public void setUp() throws Exception {
        ActorSystemContext actorSystemContext = mock(ActorSystemContext.class);

        deviceMsgService = mock(DeviceMsgService.class);
        deviceSessionCtxService = mock(DeviceSessionCtxService.class);
        serialNumberService = mock(DevicePacketIdAndSerialNumberService.class);
        publishMsgDeliveryService = mock(PublishMsgDeliveryService.class);
        clientMqttActorManager = mock(ClientMqttActorManager.class);
        clientLogger = mock(ClientLogger.class);
        deviceActorConfig = mock(DeviceActorConfiguration.class);
        sharedSubscriptionCacheService = mock(SharedSubscriptionCacheService.class);

        when(actorSystemContext.getDeviceMsgService()).thenReturn(deviceMsgService);
        when(actorSystemContext.getDeviceSessionCtxService()).thenReturn(deviceSessionCtxService);
        when(actorSystemContext.getSerialNumberService()).thenReturn(serialNumberService);
        when(actorSystemContext.getPublishMsgDeliveryService()).thenReturn(publishMsgDeliveryService);
        when(actorSystemContext.getClientMqttActorManager()).thenReturn(clientMqttActorManager);
        when(actorSystemContext.getClientLogger()).thenReturn(clientLogger);
        when(actorSystemContext.getDeviceActorConfiguration()).thenReturn(deviceActorConfig);
        when(actorSystemContext.getSharedSubscriptionCacheService()).thenReturn(sharedSubscriptionCacheService);

        this.persistedDeviceActorMessageProcessor = spy(new PersistedDeviceActorMessageProcessor(actorSystemContext, CLIENT));
    }

    @After
    public void tearDown() throws Exception {
        Mockito.reset(deviceMsgService, deviceSessionCtxService, serialNumberService, publishMsgDeliveryService,
                clientMqttActorManager, clientLogger, deviceActorConfig, sharedSubscriptionCacheService);
    }

    @Test
    public void givenDeviceConnectedEventMsg_whenProcessDeviceConnect_thenSuccess() {
        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        persistedDeviceActorMessageProcessor.processDeviceConnect(new DeviceConnectedEventMsg(ctx));

        verify(deviceMsgService, times(1)).findPersistedMessages(eq(CLIENT));
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
        when(sharedSubscriptionCacheService.isAnyOtherDeviceClientConnected(eq(CLIENT), eq(sharedSubscription))).thenReturn(true);

        persistedDeviceActorMessageProcessor.processingSharedSubscriptions(msg);

        verify(deviceMsgService, times(0)).findPersistedMessages(anyString());
        verify(deviceSessionCtxService, times(0)).removeDeviceSessionContext(anyString());
    }

    @Test
    public void givenSharedSubscriptionEventMsgAndQosZero_whenProcessingSharedSubscriptions_thenDoNothing() {
        TopicSharedSubscription sharedSubscription = new TopicSharedSubscription("tf", "g1");
        SharedSubscriptionEventMsg msg = new SharedSubscriptionEventMsg(
                Set.of(
                        sharedSubscription
                )
        );
        when(sharedSubscriptionCacheService.isAnyOtherDeviceClientConnected(eq(CLIENT), eq(sharedSubscription))).thenReturn(false);

        persistedDeviceActorMessageProcessor.processingSharedSubscriptions(msg);

        verify(deviceMsgService, times(0)).findPersistedMessages(anyString());
        verify(deviceSessionCtxService, times(0)).removeDeviceSessionContext(anyString());
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

        when(deviceMsgService.findPersistedMessages("ss_g1_tf")).thenReturn(List.of(devicePublishMsg));

        TopicSharedSubscription sharedSubscription = new TopicSharedSubscription("tf", "g1", 1);
        SharedSubscriptionEventMsg msg = new SharedSubscriptionEventMsg(
                Set.of(
                        sharedSubscription
                )
        );
        when(sharedSubscriptionCacheService.isAnyOtherDeviceClientConnected(eq(CLIENT), eq(sharedSubscription))).thenReturn(false);

        persistedDeviceActorMessageProcessor.processingSharedSubscriptions(msg);

        verify(deviceMsgService, times(1)).findPersistedMessages(eq("ss_g1_tf"));
        verify(deviceSessionCtxService, times(1)).removeDeviceSessionContext(eq("ss_g1_tf"));
        verify(serialNumberService, times(1)).saveLastSerialNumbers(any());
    }

    @Test
    public void givenTopicSharedSubscriptionAndMessages_whenUpdateMessagesBeforePublish_thenGetExpectedResult() {
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

        PacketIdAndSerialNumber packetIdAndSerialNumber = PacketIdAndSerialNumber.newInstance(1, 0L);
        TopicSharedSubscription topicSharedSubscription = new TopicSharedSubscription("tf", "g1", 1);
        List<DevicePublishMsg> devicePublishMsgList = List.of(msg1, msg2, msg3);

        persistedDeviceActorMessageProcessor.updateMessagesBeforePublish(packetIdAndSerialNumber, topicSharedSubscription, devicePublishMsgList);

        assertEquals(2, devicePublishMsgList.get(0).getPacketId().intValue());
        assertEquals(1, devicePublishMsgList.get(0).getSerialNumber().longValue());
        assertEquals(0, devicePublishMsgList.get(0).getQos().intValue());

        assertEquals(3, devicePublishMsgList.get(1).getPacketId().intValue());
        assertEquals(2, devicePublishMsgList.get(1).getSerialNumber().longValue());
        assertEquals(1, devicePublishMsgList.get(1).getQos().intValue());

        assertEquals(4, devicePublishMsgList.get(2).getPacketId().intValue());
        assertEquals(3, devicePublishMsgList.get(2).getSerialNumber().longValue());
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

        verify(publishMsgDeliveryService, times(0)).sendPublishMsgToClient(any(), any());
        verify(publishMsgDeliveryService, times(1)).sendPubRelMsgToClient(eq(ctx), eq(1));
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

        verify(publishMsgDeliveryService, times(0)).sendPublishMsgToClient(any(), any());
        verify(publishMsgDeliveryService, times(0)).sendPubRelMsgToClient(any(), anyInt());
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
                .serialNumber(100L)
                .packetType(PersistedPacketType.PUBLISH)
                .time(System.currentTimeMillis())
                .qos(1)
                .properties(properties)
                .build();
        persistedDeviceActorMessageProcessor.deliverPersistedMsg(devicePublishMsg);

        verify(publishMsgDeliveryService, times(1)).sendPublishMsgToClient(any(), any());
        verify(publishMsgDeliveryService, times(0)).sendPubRelMsgToClient(any(), anyInt());

        assertEquals(1, persistedDeviceActorMessageProcessor.getInFlightPacketIds().size());
        assertEquals(100L, persistedDeviceActorMessageProcessor.getLastPersistedMsgSentSerialNumber());
    }

    @Test
    public void givenSession_whenProcessDeviceDisconnect_thenSuccess() {
        TbActorCtx tbActorCtx = mock(TbActorCtx.class);
        persistedDeviceActorMessageProcessor.processDeviceDisconnect(tbActorCtx);

        assertNull(persistedDeviceActorMessageProcessor.getSessionCtx());
        assertNotNull(persistedDeviceActorMessageProcessor.getStopActorCommandUUID());
    }

    @Test
    public void givenDevicePublishMsg_whenSerialNumberIsLessThanLastPersistedMsgSentSerialNumber_thenStopProcessing() {
        DevicePublishMsg devicePublishMsg = DevicePublishMsg
                .builder()
                .serialNumber(-2L)
                .build();

        persistedDeviceActorMessageProcessor.process(new IncomingPublishMsg(devicePublishMsg));

        verify(publishMsgDeliveryService, times(0)).sendPublishMsgToClient(any(), any());
    }

    @Test
    public void givenDevicePublishMsg_whenMessageIsExpired_thenStopProcessing() {
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID, -100));

        DevicePublishMsg devicePublishMsg = DevicePublishMsg
                .builder()
                .serialNumber(1L)
                .time(System.currentTimeMillis())
                .properties(properties)
                .build();

        persistedDeviceActorMessageProcessor.process(new IncomingPublishMsg(devicePublishMsg));

        verify(publishMsgDeliveryService, times(0)).sendPublishMsgToClient(any(), any());
    }

    @Test
    public void givenDevicePublishMsg_whenMessageIsNotExpired_thenProcessingIsCorrect() {
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID, 100));

        DevicePublishMsg devicePublishMsg = DevicePublishMsg
                .builder()
                .serialNumber(0L)
                .packetId(1)
                .time(System.currentTimeMillis())
                .properties(properties)
                .clientId(CLIENT)
                .payload(null)
                .qos(1)
                .build();

        persistedDeviceActorMessageProcessor.process(new IncomingPublishMsg(devicePublishMsg));

        verify(publishMsgDeliveryService, times(1)).sendPublishMsgToClient(any(), any());

        assertTrue(persistedDeviceActorMessageProcessor.getInFlightPacketIds().contains(1));
    }

    @Test
    public void givenDevicePublishMsg_whenCheckForMissedMessagesAndProcessBeforeFirstIncomingMsg_thenVerifiedMethodExecution() {
        persistedDeviceActorMessageProcessor.setLastPersistedMsgSentSerialNumber(5L);

        DevicePublishMsg devicePublishMsg = DevicePublishMsg
                .builder()
                .serialNumber(10L)
                .build();

        when(deviceMsgService.findPersistedMessages(anyString(), anyLong(), anyLong())).thenReturn(List.of());

        persistedDeviceActorMessageProcessor.checkForMissedMessagesAndProcessBeforeFirstIncomingMsg(devicePublishMsg);

        verify(deviceMsgService, times(1)).findPersistedMessages(eq(CLIENT), eq(6L), eq(10L));
    }

    @Test
    public void givenNextDevicePublishMsg_whenCheckForMissedMessagesAndProcessBeforeFirstIncomingMsg_thenVerifiedMethodExecution() {
        persistedDeviceActorMessageProcessor.setLastPersistedMsgSentSerialNumber(9L);

        DevicePublishMsg devicePublishMsg = DevicePublishMsg
                .builder()
                .serialNumber(10L)
                .build();
        persistedDeviceActorMessageProcessor.checkForMissedMessagesAndProcessBeforeFirstIncomingMsg(devicePublishMsg);

        verify(deviceMsgService, times(0)).findPersistedMessages(anyString(), anyLong(), anyLong());
    }

    @Test
    public void givenDevicePublishMsgAndAlreadyProcessedMsg_whenCheckForMissedMessagesAndProcessBeforeFirstIncomingMsg_thenVerifiedMethodExecution() {
        persistedDeviceActorMessageProcessor.setProcessedAnyMsg(true);

        DevicePublishMsg devicePublishMsg = DevicePublishMsg
                .builder()
                .serialNumber(10L)
                .build();
        persistedDeviceActorMessageProcessor.checkForMissedMessagesAndProcessBeforeFirstIncomingMsg(devicePublishMsg);

        verify(deviceMsgService, times(0)).findPersistedMessages(anyString(), anyLong(), anyLong());
    }

    @Test
    public void givenPacketAcknowledgedEventMsg_whenProcessPacketAcknowledge_thenVerifiedMethodExecution() {
        persistedDeviceActorMessageProcessor.getInFlightPacketIds().add(1);

        when(deviceMsgService.tryRemovePersistedMessage(anyString(), anyInt())).thenReturn(Futures.immediateVoidFuture());
        persistedDeviceActorMessageProcessor.processPacketAcknowledge(new PacketAcknowledgedEventMsg(1));

        verify(deviceMsgService, times(1)).tryRemovePersistedMessage(eq(CLIENT), eq(1));

        assertTrue(persistedDeviceActorMessageProcessor.getInFlightPacketIds().isEmpty());
    }

    @Test
    public void givenPacketAcknowledgedEventMsgForSharedSubscription_whenProcessPacketAcknowledge_thenVerifiedMethodExecution() {
        persistedDeviceActorMessageProcessor.getInFlightPacketIds().add(1);

        Map<Integer, SharedSubscriptionPublishPacket> sentPacketIdsFromSharedSubscription = persistedDeviceActorMessageProcessor.getSentPacketIdsFromSharedSubscription();
        sentPacketIdsFromSharedSubscription.put(1, new SharedSubscriptionPublishPacket(SS_TEST_KEY, 200));

        when(deviceMsgService.tryRemovePersistedMessage(anyString(), anyInt())).thenReturn(Futures.immediateVoidFuture());
        persistedDeviceActorMessageProcessor.processPacketAcknowledge(new PacketAcknowledgedEventMsg(1));

        verify(deviceMsgService, times(1)).tryRemovePersistedMessage(eq(SS_TEST_KEY), eq(200));

        assertTrue(persistedDeviceActorMessageProcessor.getInFlightPacketIds().isEmpty());
    }

    @Test
    public void givenPacketReceivedEventMsg_whenProcessPacketReceived_thenVerifiedMethodExecution() {
        persistedDeviceActorMessageProcessor.getInFlightPacketIds().add(1);

        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        persistedDeviceActorMessageProcessor.setSessionCtx(ctx);

        when(deviceMsgService.tryUpdatePacketReceived(anyString(), anyInt())).thenReturn(Futures.immediateVoidFuture());
        persistedDeviceActorMessageProcessor.processPacketReceived(new PacketReceivedEventMsg(1));

        verify(deviceMsgService, times(1)).tryUpdatePacketReceived(eq(CLIENT), eq(1));
        verify(publishMsgDeliveryService, times(1)).sendPubRelMsgToClient(eq(ctx), eq(1));

        assertTrue(persistedDeviceActorMessageProcessor.getInFlightPacketIds().isEmpty());
    }

    @Test
    public void givenPacketReceivedEventMsgForSharedSubscription_whenProcessPacketReceived_thenVerifiedMethodExecution() {
        persistedDeviceActorMessageProcessor.getInFlightPacketIds().add(1);

        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        persistedDeviceActorMessageProcessor.setSessionCtx(ctx);

        Map<Integer, SharedSubscriptionPublishPacket> sentPacketIdsFromSharedSubscription = persistedDeviceActorMessageProcessor.getSentPacketIdsFromSharedSubscription();
        sentPacketIdsFromSharedSubscription.put(1, new SharedSubscriptionPublishPacket(SS_TEST_KEY, 200));

        when(deviceMsgService.tryUpdatePacketReceived(anyString(), anyInt())).thenReturn(Futures.immediateVoidFuture());
        persistedDeviceActorMessageProcessor.processPacketReceived(new PacketReceivedEventMsg(1));

        verify(deviceMsgService, times(1)).tryUpdatePacketReceived(eq(SS_TEST_KEY), eq(200));
        verify(publishMsgDeliveryService, times(1)).sendPubRelMsgToClient(eq(ctx), eq(1));

        assertTrue(persistedDeviceActorMessageProcessor.getInFlightPacketIds().isEmpty());
    }

    @Test
    public void givenPacketCompletedEventMsg_whenProcessPacketComplete_thenVerifiedMethodExecution() {
        when(deviceMsgService.tryRemovePersistedMessage(anyString(), anyInt())).thenReturn(Futures.immediateVoidFuture());
        persistedDeviceActorMessageProcessor.processPacketComplete(new PacketCompletedEventMsg(1));

        verify(deviceMsgService, times(1)).tryRemovePersistedMessage(eq(CLIENT), eq(1));
    }

    @Test
    public void givenPacketCompletedEventMsgForSharedSubscription_whenProcessPacketComplete_thenVerifiedMethodExecution() {
        Map<Integer, SharedSubscriptionPublishPacket> sentPacketIdsFromSharedSubscription = persistedDeviceActorMessageProcessor.getSentPacketIdsFromSharedSubscription();
        sentPacketIdsFromSharedSubscription.put(1, new SharedSubscriptionPublishPacket(SS_TEST_KEY, 200));

        when(deviceMsgService.tryRemovePersistedMessage(anyString(), anyInt())).thenReturn(Futures.immediateVoidFuture());
        persistedDeviceActorMessageProcessor.processPacketComplete(new PacketCompletedEventMsg(1));

        verify(deviceMsgService, times(1)).tryRemovePersistedMessage(eq(SS_TEST_KEY), eq(200));
    }

    @Test
    public void givenInitialPacketIdAndSerialNumber_whenIncrementAndGet_thenReturnExpectedResult() {
        PacketIdAndSerialNumber packetIdAndSerialNumber = PacketIdAndSerialNumber.initialInstance();

        PacketIdAndSerialNumberDto packetIdAndSerialNumberDto =
                persistedDeviceActorMessageProcessor.getAndIncrementPacketIdAndSerialNumber(packetIdAndSerialNumber);

        assertEquals(1, packetIdAndSerialNumberDto.getPacketId());
        assertEquals(0, packetIdAndSerialNumberDto.getSerialNumber());

        packetIdAndSerialNumberDto =
                persistedDeviceActorMessageProcessor.getAndIncrementPacketIdAndSerialNumber(packetIdAndSerialNumber);

        assertEquals(2, packetIdAndSerialNumberDto.getPacketId());
        assertEquals(1, packetIdAndSerialNumberDto.getSerialNumber());
    }

    @Test
    public void givenPacketIdAndSerialNumber_whenIncrementAndGet_thenReturnExpectedResult() {
        PacketIdAndSerialNumber packetIdAndSerialNumber = PacketIdAndSerialNumber.newInstance(10, 20);

        PacketIdAndSerialNumberDto packetIdAndSerialNumberDto =
                persistedDeviceActorMessageProcessor.getAndIncrementPacketIdAndSerialNumber(packetIdAndSerialNumber);

        assertEquals(11, packetIdAndSerialNumberDto.getPacketId());
        assertEquals(21, packetIdAndSerialNumberDto.getSerialNumber());

        packetIdAndSerialNumberDto =
                persistedDeviceActorMessageProcessor.getAndIncrementPacketIdAndSerialNumber(packetIdAndSerialNumber);

        assertEquals(12, packetIdAndSerialNumberDto.getPacketId());
        assertEquals(22, packetIdAndSerialNumberDto.getSerialNumber());
    }

    @Test
    public void givenFullPacketIdAndSerialNumber_whenIncrementAndGet_thenReturnExpectedResult() {
        PacketIdAndSerialNumber packetIdAndSerialNumber = PacketIdAndSerialNumber.newInstance(0xfffd, 50);

        PacketIdAndSerialNumberDto packetIdAndSerialNumberDto =
                persistedDeviceActorMessageProcessor.getAndIncrementPacketIdAndSerialNumber(packetIdAndSerialNumber);

        assertEquals(0xfffe, packetIdAndSerialNumberDto.getPacketId());
        assertEquals(51, packetIdAndSerialNumberDto.getSerialNumber());

        packetIdAndSerialNumberDto =
                persistedDeviceActorMessageProcessor.getAndIncrementPacketIdAndSerialNumber(packetIdAndSerialNumber);

        assertEquals(0xffff, packetIdAndSerialNumberDto.getPacketId());
        assertEquals(52, packetIdAndSerialNumberDto.getSerialNumber());

        packetIdAndSerialNumberDto =
                persistedDeviceActorMessageProcessor.getAndIncrementPacketIdAndSerialNumber(packetIdAndSerialNumber);

        assertEquals(1, packetIdAndSerialNumberDto.getPacketId());
        assertEquals(53, packetIdAndSerialNumberDto.getSerialNumber());

        packetIdAndSerialNumberDto =
                persistedDeviceActorMessageProcessor.getAndIncrementPacketIdAndSerialNumber(packetIdAndSerialNumber);

        assertEquals(2, packetIdAndSerialNumberDto.getPacketId());
        assertEquals(54, packetIdAndSerialNumberDto.getSerialNumber());
    }
}
