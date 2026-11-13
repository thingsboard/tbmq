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
package org.thingsboard.mqtt.broker.standalone.device;

import io.netty.handler.codec.mqtt.MqttProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.ClientActorContext;
import org.thingsboard.mqtt.broker.actors.TbActorCtx;
import org.thingsboard.mqtt.broker.actors.TbActorId;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.state.PublishedInFlightCtx;
import org.thingsboard.mqtt.broker.actors.device.DeviceActorConfiguration;
import org.thingsboard.mqtt.broker.actors.device.messages.DeliverPersistedMessagesEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.DeviceConnectedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.IncomingPublishMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketAcknowledgedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketCompletedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketReceivedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketReceivedNoDeliveryEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.SharedSubscriptionEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.StopDeviceActorCommandMsg;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgQueueStore;
import org.thingsboard.mqtt.broker.dao.messages.DeviceQueueInfo;
import org.thingsboard.mqtt.broker.dao.messages.StoredDeviceMsg;
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionCacheService;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StandaloneDeviceActorMessageProcessorTest {

    private static final String CLIENT_ID = "c1";
    private static final int READ_PAGE_SIZE = 256;
    private static final long NOW_MS = 1_700_000_000_000L;

    private final ActorSystemContext systemContext = mock(ActorSystemContext.class);
    private final DeviceMsgQueueStore store = mock(DeviceMsgQueueStore.class);
    private final ReplayLimiter replayLimiter = mock(ReplayLimiter.class);
    private final MqttMsgDeliveryService delivery = mock(MqttMsgDeliveryService.class);
    private final SharedSubscriptionCacheService sharedSubscriptionCache = mock(SharedSubscriptionCacheService.class);
    private final ClientMqttActorManager clientMqttActorManager = mock(ClientMqttActorManager.class);
    private final TbActorCtx actorCtx = mock(TbActorCtx.class);
    private final PublishedInFlightCtx inFlightCtx = mock(PublishedInFlightCtx.class);
    private final ClientSessionCtx sessionCtx = session(inFlightCtx);
    private final PublishedInFlightCtx inFlightCtx2 = mock(PublishedInFlightCtx.class);
    private final ClientSessionCtx sessionCtx2 = session(inFlightCtx2);
    private final TopicSharedSubscription sub = new TopicSharedSubscription("t", "g", 1, 7);

    // the queue the mocked store serves: what RocksDbDeviceMsgQueueStore keeps, without expiry
    private final List<Entry> queue = new ArrayList<>();
    private long sentUpTo;

    private StandaloneDeviceActorMessageProcessor processor;

    @BeforeEach
    void setUp() {
        ClientActorContext clientActorContext = mock(ClientActorContext.class);
        when(systemContext.getClientActorContext()).thenReturn(clientActorContext);
        when(clientActorContext.getClientLogger()).thenReturn(mock(ClientLogger.class));
        when(systemContext.getMqttMsgDeliveryService()).thenReturn(delivery);
        when(systemContext.getSharedSubscriptionCacheService()).thenReturn(sharedSubscriptionCache);
        when(systemContext.getClientMqttActorManager()).thenReturn(clientMqttActorManager);
        when(systemContext.getDeviceActorConfiguration()).thenReturn(new DeviceActorConfiguration());
        when(replayLimiter.tryAcquire(CLIENT_ID)).thenReturn(true);
        when(replayLimiter.renew(CLIENT_ID)).thenReturn(true);
        givenFreeSlots(65535);
        when(inFlightCtx2.freeSlots()).thenReturn(65535);

        when(store.readForDelivery(eq(CLIENT_ID), anyLong(), anyInt())).thenAnswer(inv -> {
            long after = inv.getArgument(1);
            int limit = inv.getArgument(2);
            List<StoredDeviceMsg> page = queue.stream().filter(e -> e.sequence() > after).limit(limit).map(this::stored).toList();
            if (!page.isEmpty()) {
                sentUpTo = Math.max(sentUpTo, page.get(page.size() - 1).sequence());
            }
            return page;
        });
        when(store.remove(eq(CLIENT_ID), anyInt())).thenAnswer(inv -> queue.removeIf(e -> e.packetId() == (int) inv.getArgument(1)));
        when(store.markPubRel(eq(CLIENT_ID), anyInt())).thenAnswer(inv -> {
            int packetId = inv.getArgument(1);
            queue.replaceAll(e -> e.packetId() == packetId ? e.asPubRel() : e);
            return true;
        });
        when(store.info(CLIENT_ID)).thenAnswer(inv -> {
            long lastSequence = queue.isEmpty() ? 0 : queue.get(queue.size() - 1).sequence();
            return new DeviceQueueInfo(0, lastSequence, 0, queue.size(), sentUpTo);
        });
        doAnswer(inv -> {
            queue.clear();
            sentUpTo = 0;
            return null;
        }).when(store).removeQueue(CLIENT_ID);

        processor = new StandaloneDeviceActorMessageProcessor(systemContext, CLIENT_ID, store, replayLimiter, READ_PAGE_SIZE,
                Clock.fixed(Instant.ofEpochMilli(NOW_MS), ZoneOffset.UTC));
    }

    @Test
    void connectDeliversOnePageReadForDelivery() {                   // the store marks the page sent as it reads it
        givenQueue(600);
        givenFreeSlots(1000);

        processor.processDeviceConnect(actorCtx, connected(sessionCtx));

        verify(delivery, times(256)).sendPublishMsgToClient(eq(sessionCtx), any(), eq(false));
        verify(store).readForDelivery(CLIENT_ID, 0, 256);
        verify(store, never()).read(any(), anyLong(), anyInt());
        assertThat(sentUpTo).isEqualTo(256);
    }

    @Test
    void aRunningReplayRenewsItsPermitBeforeEveryRead() {
        givenQueue(600);

        processor.processDeviceConnect(actorCtx, connected(sessionCtx));
        processor.processPacketAcknowledge(new PacketAcknowledgedEventMsg(1));

        verify(replayLimiter, times(2)).renew(CLIENT_ID);
        verify(store).readForDelivery(CLIENT_ID, 256, 1);
    }

    @Test
    void aStalledReplayWhosePermitWasTakenWaitsForANewOneBeforeItReadsOn() {
        givenQueue(600);
        processor.processDeviceConnect(actorCtx, connected(sessionCtx));
        when(replayLimiter.renew(CLIENT_ID)).thenReturn(false);
        when(replayLimiter.tryAcquire(CLIENT_ID)).thenReturn(false);

        processor.processPacketAcknowledge(new PacketAcknowledgedEventMsg(1));

        verify(store).remove(CLIENT_ID, 1);
        verify(store, times(1)).readForDelivery(eq(CLIENT_ID), anyLong(), anyInt());
        verify(systemContext).scheduleMsgWithDelay(eq(actorCtx), any(DeliverPersistedMessagesEventMsg.class), eq(100L));
        verify(replayLimiter, never()).release(any());

        when(replayLimiter.tryAcquire(CLIENT_ID)).thenReturn(true);
        when(replayLimiter.renew(CLIENT_ID)).thenReturn(true);
        processor.processDeliverPersistedMessages(new DeliverPersistedMessagesEventMsg(List.of()));

        verify(store).readForDelivery(CLIENT_ID, 256, 1);
    }

    @Test
    void theNextPageWaitsForAcknowledgements() {
        givenQueue(600);
        givenFreeSlots(1000);
        processor.processDeviceConnect(actorCtx, connected(sessionCtx));
        clearInvocations(delivery);

        processor.processPacketAcknowledge(new PacketAcknowledgedEventMsg(1));

        verify(store).remove(CLIENT_ID, 1);
        verify(delivery, times(1)).sendPublishMsgToClient(any(), any(), anyBoolean());
        verify(delivery).sendPublishMsgToClient(any(), argThat(m -> m.getPacketId() == 257), eq(false));
    }

    @Test
    void neverHandsOverMoreThanTheReceiveMaximumAllows() {
        givenQueue(600);
        givenFreeSlots(10);

        processor.processDeviceConnect(actorCtx, connected(sessionCtx));

        verify(delivery, times(10)).sendPublishMsgToClient(any(), any(), anyBoolean());
    }

    @Test
    void withFlowControlOffOnlyTheReadPageSizeBoundsThePage() {
        givenQueue(600);
        when(sessionCtx.getPublishedInFlightCtx()).thenReturn(null);

        processor.processDeviceConnect(actorCtx, connected(sessionCtx));

        verify(delivery, times(256)).sendPublishMsgToClient(any(), any(), anyBoolean());
    }

    @Test
    void aNonWritableChannelPausesTheDeliveryAndAWritableOneResumesIt() {
        givenQueue(600);
        givenFreeSlots(1000);
        processor.processDeviceConnect(actorCtx, connected(sessionCtx));
        processor.processChannelNonWritable();
        clearInvocations(delivery);

        processor.processPacketAcknowledge(new PacketAcknowledgedEventMsg(1));
        processor.processPacketAcknowledge(new PacketAcknowledgedEventMsg(2));
        verifyNoInteractions(delivery);

        processor.processChannelWritable(actorCtx);
        verify(delivery).sendPublishMsgToClient(any(), argThat(m -> m.getPacketId() == 257), eq(false));
        verify(delivery).sendPublishMsgToClient(any(), argThat(m -> m.getPacketId() == 258), eq(false));
    }

    @Test
    void mqtt_4_4_0_1_and_3_3_1_1_aReconnectResendsSentEntriesWithDupAndTheOriginalPacketId() {
        givenQueueWithSentEntries(3);

        processor.processDeviceConnect(actorCtx, connected(sessionCtx));

        for (int packetId = 1; packetId <= 3; packetId++) {
            int id = packetId;
            verify(delivery).sendPublishMsgToClient(any(), argThat(m -> m.getPacketId() == id), eq(true));
        }
        verify(delivery).sendPublishMsgToClient(any(), argThat(m -> m.getPacketId() == 4), eq(false));
        verify(delivery).sendPublishMsgToClient(any(), argThat(m -> m.getPacketId() == 5), eq(false));
    }

    @Test
    void mqtt_3_3_2_6_theForwardedIntervalIsTheRemainingLifetimeAndZeroOncePassed() {
        queue.add(new Entry(1, 1, PersistedPacketType.PUBLISH, NOW_MS - 60_000, body(10)));   // sent, lifetime passed
        queue.add(new Entry(2, 2, PersistedPacketType.PUBLISH, NOW_MS - 4_500, body(10)));    // 5.5 s left: floor to 5 s
        queue.add(new Entry(3, 3, PersistedPacketType.PUBLISH, NOW_MS - 4_000, body(0)));
        queue.add(new Entry(4, 4, PersistedPacketType.PUBLISH, NOW_MS - 4_000, body(null)));
        sentUpTo = 1;

        processor.processDeviceConnect(actorCtx, connected(sessionCtx));

        ArgumentCaptor<DevicePublishMsg> sent = ArgumentCaptor.forClass(DevicePublishMsg.class);
        verify(delivery, times(4)).sendPublishMsgToClient(eq(sessionCtx), sent.capture(), anyBoolean());
        assertThat(sent.getAllValues()).extracting(DevicePublishMsg::getMsgExpiryInterval).containsExactly(0, 5, 0, null);
    }

    @Test
    void mqtt_3_3_2_6_theRemainingLifetimeOfAnIntervalOfTwoToThe31SecondsOrMoreIsUnsigned() {   // Trello #657
        // netty reads the Four Byte Integer into a signed int: 0xFFFFFFFF arrives as -1, 2^31 s as Integer.MIN_VALUE;
        // the remaining interval goes back into the int property the same way and netty writes it unsigned
        queue.add(new Entry(1, 1, PersistedPacketType.PUBLISH, NOW_MS - 4_000, body(-1)));
        queue.add(new Entry(2, 2, PersistedPacketType.PUBLISH, NOW_MS - 4_000, body(Integer.MIN_VALUE)));

        processor.processDeviceConnect(actorCtx, connected(sessionCtx));

        ArgumentCaptor<DevicePublishMsg> sent = ArgumentCaptor.forClass(DevicePublishMsg.class);
        verify(delivery, times(2)).sendPublishMsgToClient(eq(sessionCtx), sent.capture(), anyBoolean());
        assertThat(sent.getAllValues()).extracting(m -> Integer.toUnsignedLong(m.getMsgExpiryInterval()))
                .containsExactly(0xFFFF_FFFFL - 4, (1L << 31) - 4);
    }

    @Test
    void aStoredPubRelIsResentAsPubRel() {
        queue.add(new Entry(1, 1, PersistedPacketType.PUBREL, NOW_MS, body(null)));
        queue.add(new Entry(2, 2, PersistedPacketType.PUBLISH, NOW_MS, body(null)));

        processor.processDeviceConnect(actorCtx, connected(sessionCtx));

        verify(delivery).sendPubRelMsgToClient(sessionCtx, 1);
        verify(delivery).sendPublishMsgToClient(eq(sessionCtx), argThat(m -> m.getPacketId() == 2), eq(false));
        verify(delivery, times(1)).sendPublishMsgToClient(any(), any(), anyBoolean());
    }

    @Test
    void aDisconnectInTheMiddleOfAReplayReturnsThePermitAndTheReconnectStartsFromTheStore() {
        givenQueue(600);
        givenFreeSlots(1000);
        processor.processDeviceConnect(actorCtx, connected(sessionCtx));

        processor.processDeviceDisconnect(actorCtx);
        verify(replayLimiter).release(CLIENT_ID);
        processor.processPacketAcknowledge(new PacketAcknowledgedEventMsg(1));       // the old session's late ack
        verify(delivery, times(256)).sendPublishMsgToClient(eq(sessionCtx), any(), anyBoolean());

        processor.processDeviceConnect(actorCtx, connected(sessionCtx2));

        verify(store, atLeastOnce()).readForDelivery(CLIENT_ID, 0, 256);
        verify(replayLimiter, times(2)).tryAcquire(CLIENT_ID);
        verify(delivery, never()).sendPublishMsgToClient(eq(sessionCtx2), argThat(m -> m.getPacketId() == 1), anyBoolean());
        verify(delivery).sendPublishMsgToClient(eq(sessionCtx2), argThat(m -> m.getPacketId() == 2), eq(true));
        verify(delivery).sendPublishMsgToClient(eq(sessionCtx2), argThat(m -> m.getPacketId() == 256), eq(true));
        verify(delivery).sendPublishMsgToClient(eq(sessionCtx2), argThat(m -> m.getPacketId() == 257), eq(false));
        verify(delivery, times(256)).sendPublishMsgToClient(eq(sessionCtx2), any(), anyBoolean());
    }

    @Test
    void aTakeoverResendsEveryUnacknowledgedEntryWithDupToTheNewSessionAndSkipsNone() {
        givenQueue(600);
        givenFreeSlots(1000);
        processor.processDeviceConnect(actorCtx, connected(sessionCtx));
        IntStream.rangeClosed(1, 10).forEach(id -> processor.processPacketAcknowledge(new PacketAcknowledgedEventMsg(id)));   // 257..266 follow

        processor.processDeviceConnect(actorCtx, connected(sessionCtx2));              // the new connection, no disconnect between

        ArgumentCaptor<DevicePublishMsg> resent = ArgumentCaptor.forClass(DevicePublishMsg.class);
        verify(delivery, times(256)).sendPublishMsgToClient(eq(sessionCtx2), resent.capture(), eq(true));
        assertThat(resent.getAllValues()).extracting(DevicePublishMsg::getPacketId)
                .containsExactlyElementsOf(IntStream.rangeClosed(11, 266).boxed().toList());
        verify(delivery, never()).sendPublishMsgToClient(eq(sessionCtx2), any(), eq(false));
    }

    @Test
    void aNewMessageWakeUpDuringAReplayDoesNotJumpTheQueue() {
        givenQueue(600);
        givenFreeSlots(1000);
        processor.processDeviceConnect(actorCtx, connected(sessionCtx));
        clearInvocations(delivery);

        processor.processIncomingMsg(new IncomingPublishMsg(msgWithPacketId(601)));

        verifyNoInteractions(delivery);                                            // 256 are outstanding; packet 601 waits its turn
    }

    @Test
    void aNewMessageWakeUpDeliversTheStoredMessageOnceTheQueueIsCaughtUp() {
        processor.processDeviceConnect(actorCtx, connected(sessionCtx));
        queue.add(new Entry(1, 1, PersistedPacketType.PUBLISH, NOW_MS, body(null)));

        processor.processIncomingMsg(new IncomingPublishMsg(msgWithPacketId(1)));

        verify(delivery).sendPublishMsgToClient(eq(sessionCtx), argThat(m -> m.getPacketId() == 1 && "a/b".equals(m.getTopicName())), eq(false));
        verify(replayLimiter, never()).tryAcquire(any());                          // an empty queue on connect takes no permit
    }

    @Test
    void withoutAReplayPermitTheConnectRetriesLater() {
        givenQueue(5);
        when(replayLimiter.tryAcquire(CLIENT_ID)).thenReturn(false);

        processor.processDeviceConnect(actorCtx, connected(sessionCtx));
        processor.processIncomingMsg(new IncomingPublishMsg(msgWithPacketId(6)));    // a wake-up must not bypass the permit

        verifyNoInteractions(delivery);
        verify(systemContext).scheduleMsgWithDelay(eq(actorCtx), any(DeliverPersistedMessagesEventMsg.class), eq(100L));
    }

    @Test
    void theRetryReplaysOnceAPermitIsFree() {
        givenQueue(5);
        when(replayLimiter.tryAcquire(CLIENT_ID)).thenReturn(false, false, true);
        processor.processDeviceConnect(actorCtx, connected(sessionCtx));

        processor.processDeliverPersistedMessages(new DeliverPersistedMessagesEventMsg(List.of()));
        verifyNoInteractions(delivery);
        verify(systemContext, times(2)).scheduleMsgWithDelay(eq(actorCtx), any(DeliverPersistedMessagesEventMsg.class), eq(100L));

        processor.processDeliverPersistedMessages(new DeliverPersistedMessagesEventMsg(List.of()));
        verify(delivery, times(5)).sendPublishMsgToClient(eq(sessionCtx), any(), eq(false));
    }

    @Test
    void aRetryThatArrivesAfterTheDisconnectDoesNothing() {
        givenQueue(5);
        when(replayLimiter.tryAcquire(CLIENT_ID)).thenReturn(false);
        processor.processDeviceConnect(actorCtx, connected(sessionCtx));
        processor.processDeviceDisconnect(actorCtx);

        processor.processDeliverPersistedMessages(new DeliverPersistedMessagesEventMsg(List.of()));

        verifyNoInteractions(delivery);
        verify(replayLimiter, times(1)).tryAcquire(CLIENT_ID);
        verify(replayLimiter, never()).release(any());                              // no permit was held
    }

    @Test
    void thePermitIsReturnedOnceTheBacklogIsHandedOver() {
        givenQueue(5);

        processor.processDeviceConnect(actorCtx, connected(sessionCtx));

        verify(delivery, times(5)).sendPublishMsgToClient(any(), any(), anyBoolean());
        verify(replayLimiter).release(CLIENT_ID);
    }

    @Test
    void aLongReplayKeepsThePermitUntilItReachesTheEnd() {
        givenQueue(300);
        processor.processDeviceConnect(actorCtx, connected(sessionCtx));
        verify(replayLimiter, never()).release(any());

        IntStream.rangeClosed(1, 43).forEach(id -> processor.processPacketAcknowledge(new PacketAcknowledgedEventMsg(id)));
        verify(replayLimiter, never()).release(any());

        processor.processPacketAcknowledge(new PacketAcknowledgedEventMsg(44));     // hands over 300, the last entry
        verify(replayLimiter).release(CLIENT_ID);
        verify(delivery, times(300)).sendPublishMsgToClient(any(), any(), anyBoolean());
    }

    @Test
    void thePermitCoversTheConnectTimeBacklogOnlyWhileTheQueueKeepsGrowing() {
        givenQueue(300);
        processor.processDeviceConnect(actorCtx, connected(sessionCtx));
        appendEntries(301, 400);                                                     // ingress during the replay

        IntStream.rangeClosed(1, 43).forEach(id -> processor.processPacketAcknowledge(new PacketAcknowledgedEventMsg(id)));
        verify(replayLimiter, never()).release(any());

        processor.processPacketAcknowledge(new PacketAcknowledgedEventMsg(44));     // hands over 300: every page so far was full
        verify(replayLimiter).release(CLIENT_ID);

        appendEntries(401, 410);
        IntStream.rangeClosed(45, 60).forEach(id -> processor.processPacketAcknowledge(new PacketAcknowledgedEventMsg(id)));
        verify(delivery, times(316)).sendPublishMsgToClient(any(), any(), anyBoolean());
        verify(delivery).sendPublishMsgToClient(any(), argThat(m -> m.getPacketId() == 316), eq(false));
        verify(replayLimiter, times(1)).release(CLIENT_ID);
    }

    @Test
    void aReconnectWhileAwaitingAPermitKeepsASingleRetryChain() {
        givenQueue(5);
        when(replayLimiter.tryAcquire(CLIENT_ID)).thenReturn(false);
        processor.processDeviceConnect(actorCtx, connected(sessionCtx));
        processor.processDeviceDisconnect(actorCtx);
        processor.processDeviceConnect(actorCtx, connected(sessionCtx2));
        processor.processDeviceConnect(actorCtx, connected(sessionCtx));            // a takeover, no disconnect between
        verify(systemContext, times(1)).scheduleMsgWithDelay(eq(actorCtx), any(DeliverPersistedMessagesEventMsg.class), eq(100L));

        processor.processDeliverPersistedMessages(new DeliverPersistedMessagesEventMsg(List.of()));   // the one retry, still no permit

        verify(systemContext, times(2)).scheduleMsgWithDelay(eq(actorCtx), any(DeliverPersistedMessagesEventMsg.class), eq(100L));
        verifyNoInteractions(delivery);
    }

    @Test
    void mqtt_4_8_2_4_aSharedGroupBacklogIsMovedIntoTheMembersQueue() {
        when(sharedSubscriptionCache.isAnyOtherDeviceClientConnected(CLIENT_ID, sub)).thenReturn(false);
        processor.processDeviceConnect(actorCtx, connected(sessionCtx));
        DevicePublishMsg groupMsg = msgWithPacketId(BrokerConstants.BLANK_PACKET_ID);
        groupMsg.setQos(2);
        byte[] groupBody = DeviceMsgBodyCodec.toNew(groupMsg).body();
        when(store.moveQueue(eq("ss_g_t"), eq(CLIENT_ID), any())).thenAnswer(inv -> {
            UnaryOperator<byte[]> mapper = inv.getArgument(2);
            queue.add(new Entry(1, 1, PersistedPacketType.PUBLISH, NOW_MS, mapper.apply(groupBody)));
            return 1;
        });

        processor.processSharedSubscriptions(actorCtx, new SharedSubscriptionEventMsg(Set.of(sub)));

        verify(store).moveQueue(eq("ss_g_t"), eq(CLIENT_ID), any());
        verify(store, never()).resetPacketId(any(), anyInt());
        ArgumentCaptor<DevicePublishMsg> sent = ArgumentCaptor.forClass(DevicePublishMsg.class);
        verify(delivery).sendPublishMsgToClient(eq(sessionCtx), sent.capture(), eq(false));
        assertThat(sent.getValue().getQos()).isEqualTo(1);                          // lowered to the member's subscription QoS
        assertThat(sent.getValue().getSubscriptionIds()).containsExactly(7);
    }

    @Test
    void aSharedGroupWithAnotherConnectedMemberOrQos0IsNotMoved() {
        TopicSharedSubscription qos0 = new TopicSharedSubscription("t0", "g", 0);
        when(sharedSubscriptionCache.isAnyOtherDeviceClientConnected(CLIENT_ID, sub)).thenReturn(true);

        processor.processSharedSubscriptions(actorCtx, new SharedSubscriptionEventMsg(Set.of(sub, qos0)));

        verify(store, never()).moveQueue(any(), any(), any());
    }

    @Test
    void aPubrecMarksTheEntryPubRelAndSendsPubRelAndPubcompCompletesIt() {
        givenQueue(1);
        processor.processDeviceConnect(actorCtx, connected(sessionCtx));

        processor.processPacketReceived(new PacketReceivedEventMsg(1));
        verify(store).markPubRel(CLIENT_ID, 1);
        verify(delivery).sendPubRelMsgToClient(sessionCtx, 1);

        processor.processPacketComplete(new PacketCompletedEventMsg(1));
        verify(store).remove(CLIENT_ID, 1);
        assertThat(queue).isEmpty();
    }

    @Test
    void aPubrecWithAnErrorCodeRemovesTheEntryAndFreesItsPlace() {
        givenQueue(257);
        processor.processDeviceConnect(actorCtx, connected(sessionCtx));
        clearInvocations(delivery);

        processor.processPacketReceivedNoDelivery(new PacketReceivedNoDeliveryEventMsg(1));

        verify(store).remove(CLIENT_ID, 1);
        verify(delivery).sendPublishMsgToClient(any(), argThat(m -> m.getPacketId() == 257), eq(false));
    }

    @Test
    void acknowledgementsWithNothingOutstandingNeverShrinkThePage() {
        processor.processDeviceConnect(actorCtx, connected(sessionCtx));
        IntStream.rangeClosed(1, 5).forEach(id -> processor.processPacketAcknowledge(new PacketAcknowledgedEventMsg(id)));
        givenQueue(300);

        processor.processIncomingMsg(new IncomingPublishMsg(msgWithPacketId(1)));

        verify(delivery, times(256)).sendPublishMsgToClient(any(), any(), anyBoolean());
    }

    @Test
    void removePersistedMessagesDropsTheQueueAndRestartsTheCursor() {
        givenQueue(300);
        processor.processDeviceConnect(actorCtx, connected(sessionCtx));
        clearInvocations(delivery);

        processor.processRemovePersistedMessages();
        verify(store).removeQueue(CLIENT_ID);
        verify(replayLimiter).release(CLIENT_ID);

        queue.add(new Entry(1, 1, PersistedPacketType.PUBLISH, NOW_MS, body(null)));  // the queue starts over at sequence 1
        processor.processIncomingMsg(new IncomingPublishMsg(msgWithPacketId(1)));
        verify(delivery).sendPublishMsgToClient(eq(sessionCtx), argThat(m -> m.getPacketId() == 1), eq(false));
    }

    @Test
    void aStoreErrorDisconnectsTheClient() {
        givenQueue(5);
        when(store.readForDelivery(eq(CLIENT_ID), anyLong(), anyInt())).thenThrow(new StoreException("Failed to read the device queue"));

        processor.processDeviceConnect(actorCtx, connected(sessionCtx));

        ArgumentCaptor<MqttDisconnectMsg> disconnect = ArgumentCaptor.forClass(MqttDisconnectMsg.class);
        verify(clientMqttActorManager).disconnect(eq(CLIENT_ID), disconnect.capture());
        assertThat(disconnect.getValue().getSessionId()).isEqualTo(sessionCtx.getSessionId());
        assertThat(disconnect.getValue().getReason().getType()).isEqualTo(DisconnectReasonType.ON_ERROR);
        verifyNoInteractions(delivery);
    }

    @Test
    void aStoreErrorWhileDisconnectedDisconnectsNobody() {
        doThrow(new StoreException("Failed to remove the device queue")).when(store).removeQueue(CLIENT_ID);

        processor.processRemovePersistedMessages();

        verifyNoInteractions(clientMqttActorManager);
    }

    @Test
    void theActorStopsOnlyOnTheStopCommandOfTheLastDisconnect() {
        processor.processDeviceConnect(actorCtx, connected(sessionCtx));
        processor.processDeviceDisconnect(actorCtx);
        ArgumentCaptor<StopDeviceActorCommandMsg> stop = ArgumentCaptor.forClass(StopDeviceActorCommandMsg.class);
        verify(systemContext).scheduleMsgWithDelay(eq(actorCtx), stop.capture(), eq(0L));
        TbActorId self = mock(TbActorId.class);
        when(actorCtx.getSelf()).thenReturn(self);

        processor.processActorStop(actorCtx, new StopDeviceActorCommandMsg(UUID.randomUUID()));
        verify(actorCtx, never()).stop(any());

        processor.processActorStop(actorCtx, stop.getValue());
        verify(actorCtx).stop(self);
    }

    @Test
    void aReconnectCancelsThePendingActorStop() {
        processor.processDeviceConnect(actorCtx, connected(sessionCtx));
        processor.processDeviceDisconnect(actorCtx);
        ArgumentCaptor<StopDeviceActorCommandMsg> stop = ArgumentCaptor.forClass(StopDeviceActorCommandMsg.class);
        verify(systemContext).scheduleMsgWithDelay(eq(actorCtx), stop.capture(), eq(0L));
        processor.processDeviceConnect(actorCtx, connected(sessionCtx2));

        processor.processActorStop(actorCtx, stop.getValue());

        verify(actorCtx, never()).stop(any());
    }

    @Test
    void aReadPageSizeBelowOneIsRejected() {
        assertThatThrownBy(() -> new StandaloneDeviceActorMessageProcessorFactory(store, replayLimiter, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("store.device-msg.read-page-size");
    }

    @Test
    void aTakeoverWhileAwaitingAPermitOntoAnEmptyQueueDeliversWakeUpsWithoutAPermit() {
        givenQueue(5);
        when(replayLimiter.tryAcquire(CLIENT_ID)).thenReturn(false);
        processor.processDeviceConnect(actorCtx, connected(sessionCtx));              // AWAITING_PERMIT
        queue.clear();                                                                 // the backlog is gone before the takeover

        processor.processDeviceConnect(actorCtx, connected(sessionCtx2));            // a takeover, no disconnect between
        appendEntries(6, 6);
        processor.processIncomingMsg(new IncomingPublishMsg(msgWithPacketId(6)));

        verify(delivery).sendPublishMsgToClient(eq(sessionCtx2), argThat(m -> m.getPacketId() == 6), eq(false));
        verify(replayLimiter, times(1)).tryAcquire(CLIENT_ID);                        // the takeover asked for no permit
        verify(systemContext, times(1)).scheduleMsgWithDelay(eq(actorCtx), any(DeliverPersistedMessagesEventMsg.class), eq(100L));

        processor.processDeliverPersistedMessages(new DeliverPersistedMessagesEventMsg(List.of()));   // the stale retry
        verify(delivery, times(1)).sendPublishMsgToClient(any(), any(), anyBoolean());
        verify(replayLimiter, times(1)).tryAcquire(CLIENT_ID);
    }

    @Test
    void theStandaloneFactoryCreatesTheCursorProcessor() {
        assertThat(new StandaloneDeviceActorMessageProcessorFactory(store, replayLimiter, READ_PAGE_SIZE).create(systemContext, CLIENT_ID))
                .isExactlyInstanceOf(StandaloneDeviceActorMessageProcessor.class);
    }

    private void givenQueue(int size) {
        IntStream.rangeClosed(1, size).forEach(i -> queue.add(new Entry(i, i, PersistedPacketType.PUBLISH, NOW_MS, body(null))));
    }

    private void appendEntries(int fromSequence, int toSequence) {
        IntStream.rangeClosed(fromSequence, toSequence).forEach(i -> queue.add(new Entry(i, i, PersistedPacketType.PUBLISH, NOW_MS, body(null))));
    }

    private void givenQueueWithSentEntries(int sent) {
        givenQueue(sent + 2);
        sentUpTo = sent;
    }

    private void givenFreeSlots(int freeSlots) {
        when(inFlightCtx.freeSlots()).thenReturn(freeSlots);
    }

    private StoredDeviceMsg stored(Entry e) {
        boolean sent = e.sequence() <= sentUpTo || e.type() == PersistedPacketType.PUBREL;
        return new StoredDeviceMsg(e.sequence(), e.packetId(), e.type(), sent, e.createdTimeMs(), 0L, e.body());
    }

    private static DeviceConnectedEventMsg connected(ClientSessionCtx ctx) {
        return new DeviceConnectedEventMsg(ctx);
    }

    private static ClientSessionCtx session(PublishedInFlightCtx inFlightCtx) {
        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        when(ctx.getSessionId()).thenReturn(UUID.randomUUID());
        when(ctx.getPublishedInFlightCtx()).thenReturn(inFlightCtx);
        return ctx;
    }

    private static byte[] body(Integer msgExpiryInterval) {
        DevicePublishMsg msg = msgWithPacketId(BrokerConstants.BLANK_PACKET_ID);
        msg.setMsgExpiryInterval(msgExpiryInterval);
        return DeviceMsgBodyCodec.toNew(msg).body();
    }

    private static DevicePublishMsg msgWithPacketId(int packetId) {
        return new DevicePublishMsg(CLIENT_ID, "a/b", NOW_MS, 1, packetId, PersistedPacketType.PUBLISH,
                "hello".getBytes(StandardCharsets.UTF_8), new MqttProperties(), false);
    }

    private record Entry(long sequence, int packetId, PersistedPacketType type, long createdTimeMs, byte[] body) {

        Entry asPubRel() {
            return new Entry(sequence, packetId, PersistedPacketType.PUBREL, createdTimeMs, body);
        }

    }

}
