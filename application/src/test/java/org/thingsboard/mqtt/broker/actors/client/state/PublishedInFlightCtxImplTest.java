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

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.service.mqtt.delivery.MqttPublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.flow.control.FlowControlService;
import org.thingsboard.mqtt.broker.service.stats.FlowControlStats;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PublishedInFlightCtxImplTest {

    private static final String CLIENT_ID = "test-client";
    private static final int RECEIVE_MAX = 5;

    private FlowControlService flowControlService;
    private ClientSessionCtx clientSessionCtx;
    private MqttPublishMsgDeliveryService deliveryService;
    private FlowControlStats stats;
    private ChannelHandlerContext channelCtx;
    private Channel channel;

    private PublishedInFlightCtxImpl ctx;

    @Before
    public void setUp() {
        flowControlService = mock(FlowControlService.class);
        clientSessionCtx = mock(ClientSessionCtx.class);
        deliveryService = mock(MqttPublishMsgDeliveryService.class);
        stats = mock(FlowControlStats.class);
        channelCtx = mock(ChannelHandlerContext.class);
        channel = mock(Channel.class);

        when(clientSessionCtx.getClientId()).thenReturn(CLIENT_ID);
        when(clientSessionCtx.getChannel()).thenReturn(channelCtx);
        when(channelCtx.channel()).thenReturn(channel);
        when(channel.isWritable()).thenReturn(true);

        ctx = new PublishedInFlightCtxImpl(flowControlService, clientSessionCtx, deliveryService, stats, RECEIVE_MAX, 5);
    }

    @Test
    public void addInFlightMsg_qos0_returnsTrueWithoutTracking() {
        boolean result = ctx.addInFlightMsg(qos0(1));

        assertTrue(result);
        verify(stats, never()).incInflight();
        verify(flowControlService, never()).addToMap(eq(CLIENT_ID), any());
    }

    @Test
    public void addInFlightMsg_belowReceiveMax_returnsTrueAndReservesSlot() {
        for (int i = 1; i <= 5; i++) {
            assertTrue(ctx.addInFlightMsg(qos1(i)));
        }
        // 6th should not reserve (window full, will buffer)
        assertFalse(ctx.addInFlightMsg(qos1(6)));

        verify(stats, times(5)).incInflight();
        verify(flowControlService, times(1)).addToMap(eq(CLIENT_ID), same(ctx));
    }

    @Test
    public void addInFlightMsg_atReceiveMax_buffersAndRegistersForSweep() {
        for (int i = 1; i <= 5; i++) {
            assertTrue(ctx.addInFlightMsg(qos1(i)));
        }
        boolean result = ctx.addInFlightMsg(qos1(6));

        assertFalse(result);
        verify(stats, times(1)).incDelayed();
        verify(flowControlService, times(1)).addToMap(eq(CLIENT_ID), same(ctx));
    }

    @Test
    public void addInFlightMsg_delayedQueueFull_dropsAndReleasesByteBuf() {
        // Fill in-flight window
        for (int i = 1; i <= 5; i++) {
            ctx.addInFlightMsg(qos1(i));
        }
        // Fill delayed queue
        for (int i = 6; i <= 10; i++) {
            ctx.addInFlightMsg(qos1(i));
        }

        MqttPublishMessage overflow = qos1(11);
        boolean result = ctx.addInFlightMsg(overflow);

        assertFalse(result);
        assertEquals(0, overflow.payload().refCnt());
        verify(stats, times(1)).incDropOverflow();
    }

    @Test
    public void addInFlightMsg_mqtt3xDefaultReceiveMax_alwaysReservesInFlight() {
        ctx = new PublishedInFlightCtxImpl(flowControlService, clientSessionCtx, deliveryService, stats,
                BrokerConstants.DEFAULT_RECEIVE_MAXIMUM, 5);

        for (int i = 1; i <= 1000; i++) {
            assertTrue(ctx.addInFlightMsg(qos1(i)));
        }

        verify(stats, times(1000)).incInflight();
        verify(stats, never()).incDelayed();
        verify(flowControlService, never()).addToMap(eq(CLIENT_ID), any());
    }

    @Test
    public void ackInFlightMsg_knownId_removesFromInFlight() {
        ctx.addInFlightMsg(qos1(1));
        ctx.addInFlightMsg(qos1(2));

        ctx.ackInFlightMsg(1);
        verify(stats, times(1)).decInflight();
        verify(stats, never()).incUnknownAck();

        // Re-ack 1: should be unknown now.
        ctx.ackInFlightMsg(1);
        verify(stats, times(1)).incUnknownAck();
    }

    @Test
    public void ackInFlightMsg_unknownId_incrementsCounterAndIgnores() {
        ctx.ackInFlightMsg(42);

        verify(stats, times(1)).incUnknownAck();
        verify(stats, never()).decInflight();
    }

    @Test
    public void ackInFlightMsg_outOfOrder_decrementsRegardless() {
        for (int i = 1; i <= 5; i++) {
            ctx.addInFlightMsg(qos1(i));
        }
        ctx.ackInFlightMsg(3);
        ctx.ackInFlightMsg(1);
        ctx.ackInFlightMsg(5);
        ctx.ackInFlightMsg(2);
        ctx.ackInFlightMsg(4);

        verify(stats, never()).incUnknownAck();
        verify(stats, times(5)).decInflight();

        // Window should be empty now — a new msg id 6 must reserve a slot.
        assertTrue(ctx.addInFlightMsg(qos1(6)));
    }

    @Test
    public void ackInFlightMsg_strayPubCompAfterFailurePubRec_incrementsUnknownAck() {
        ctx.addInFlightMsg(qos1(7));
        ctx.ackInFlightMsg(7);
        verify(stats, times(1)).decInflight();

        // Stray ack (e.g. PUBCOMP after PUBREC failure) for the same id.
        ctx.ackInFlightMsg(7);
        verify(stats, times(1)).incUnknownAck();
    }

    @Test
    public void ackInFlightMsg_drainsBufferedMessages() {
        for (int i = 1; i <= 5; i++) {
            ctx.addInFlightMsg(qos1(i));
        }
        MqttPublishMessage buffered = qos1(6);
        ctx.addInFlightMsg(buffered);

        ctx.ackInFlightMsg(1);

        ArgumentCaptor<MqttPublishMessage> captor = ArgumentCaptor.forClass(MqttPublishMessage.class);
        verify(deliveryService, times(1)).sendAlreadyTrackedPublishMsgToClient(same(clientSessionCtx), captor.capture());
        assertEquals(6, captor.getValue().variableHeader().packetId());
    }

    @Test
    public void tryDrain_doesNotReEnterAddInFlightMsg_andDeliversBufferedMessages() {
        // Regression for the bug where tryDrain -> deliveryService.sendPublishMsgToClient ->
        // ctx.addInFlightMsg -> re-buffer (because the just-drained packetId is already
        // in the Set, so the window appears full). Use sendAlreadyTrackedPublishMsgToClient
        // which skips the addInFlightMsg precondition.

        // Fill the window + buffer two messages
        for (int id = 1; id <= RECEIVE_MAX; id++) ctx.addInFlightMsg(qos1(id));
        ctx.addInFlightMsg(qos1(RECEIVE_MAX + 1));   // buffered
        ctx.addInFlightMsg(qos1(RECEIVE_MAX + 2));   // buffered

        // Wire the mock to call back into ctx.addInFlightMsg as production does — if any
        // caller is using the wrong method this test will reveal it via captured packetIds.
        doAnswer(inv -> {
            MqttPublishMessage m = inv.getArgument(1);
            // Simulate real DefaultMqttPublishMsgDeliveryService behavior: it would call
            // ctx.addInFlightMsg(m) first. We DON'T do that here because we're asserting
            // that the drain path uses the alternative (already-tracked) method.
            return null;
        }).when(deliveryService).sendAlreadyTrackedPublishMsgToClient(eq(clientSessionCtx), any());

        // Ack a slot -> triggers tryDrain which should drain one buffered message
        ctx.ackInFlightMsg(1);

        // Verify the drain path used the bypass method, with the FIRST buffered packetId (FIFO).
        ArgumentCaptor<MqttPublishMessage> sent = ArgumentCaptor.forClass(MqttPublishMessage.class);
        verify(deliveryService, times(1)).sendAlreadyTrackedPublishMsgToClient(eq(clientSessionCtx), sent.capture());
        verify(deliveryService, never()).sendPublishMsgToClient(any(), any());
        assertEquals(RECEIVE_MAX + 1, sent.getValue().variableHeader().packetId());
    }

    @Test
    public void onChannelWritable_resumesDrainAfterNonWritablePause() {
        for (int i = 1; i <= 5; i++) {
            ctx.addInFlightMsg(qos1(i));
        }
        ctx.addInFlightMsg(qos1(6));

        // Channel non-writable: drain on ack must not send.
        when(channel.isWritable()).thenReturn(false);
        ctx.ackInFlightMsg(1);
        verify(deliveryService, never()).sendAlreadyTrackedPublishMsgToClient(any(), any());

        // Channel writable again: explicit hook drains.
        when(channel.isWritable()).thenReturn(true);
        ctx.onChannelWritable();
        verify(deliveryService, times(1)).sendAlreadyTrackedPublishMsgToClient(same(clientSessionCtx), any());
    }

    @Test
    public void expireTtl_dropsExpiredAndReleasesByteBufs() throws InterruptedException {
        // Fill in-flight window so the next message is buffered (not sent in-flight).
        for (int i = 1; i <= 5; i++) {
            ctx.addInFlightMsg(qos1(i));
        }
        MqttPublishMessage expired = qos1(6);
        ctx.addInFlightMsg(expired);

        Thread.sleep(20);
        ctx.expireTtl(1L);

        verify(stats, times(1)).incDropTtl();
        assertEquals(0, expired.payload().refCnt());
    }

    @Test
    public void release_clearsBothStructuresAndReleasesBufferedMessages() {
        for (int i = 1; i <= 5; i++) {
            ctx.addInFlightMsg(qos1(i));
        }
        MqttPublishMessage b1 = qos1(6);
        MqttPublishMessage b2 = qos1(7);
        MqttPublishMessage b3 = qos1(8);
        ctx.addInFlightMsg(b1);
        ctx.addInFlightMsg(b2);
        ctx.addInFlightMsg(b3);

        ctx.release();

        assertEquals(0, b1.payload().refCnt());
        assertEquals(0, b2.payload().refCnt());
        assertEquals(0, b3.payload().refCnt());

        // After release, the window is empty and a new in-flight should reserve.
        assertTrue(ctx.addInFlightMsg(qos1(99)));
    }

    @Test
    public void concurrentAddAndAck_isSafe() throws InterruptedException {
        // Use large window so adds don't get rejected.
        ctx = new PublishedInFlightCtxImpl(flowControlService, clientSessionCtx, deliveryService, stats,
                BrokerConstants.DEFAULT_RECEIVE_MAXIMUM, 5);

        int ops = 2000;
        AtomicBoolean failed = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(2);

        Thread t1 = new Thread(() -> {
            try {
                for (int i = 1; i <= ops; i++) {
                    ctx.addInFlightMsg(qos1(i));
                }
            } catch (Throwable e) {
                failed.set(true);
            } finally {
                latch.countDown();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                for (int i = 1; i <= ops; i++) {
                    ctx.ackInFlightMsg(i);
                }
            } catch (Throwable e) {
                failed.set(true);
            } finally {
                latch.countDown();
            }
        });

        t1.start();
        t2.start();
        boolean finished = latch.await(10, TimeUnit.SECONDS);
        assertTrue("Threads did not finish in time", finished);
        assertFalse("An exception was thrown during concurrent ops", failed.get());
    }

    @Test
    public void release_concurrentWithAck_isSafe() throws InterruptedException {
        ctx = new PublishedInFlightCtxImpl(flowControlService, clientSessionCtx, deliveryService, stats,
                BrokerConstants.DEFAULT_RECEIVE_MAXIMUM, 5);

        int ops = 1000;
        for (int i = 1; i <= ops; i++) {
            ctx.addInFlightMsg(qos1(i));
        }

        AtomicBoolean failed = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(2);

        Thread releaser = new Thread(() -> {
            try {
                ctx.release();
            } catch (Throwable e) {
                failed.set(true);
            } finally {
                latch.countDown();
            }
        });

        Thread acker = new Thread(() -> {
            try {
                for (int i = 1; i <= ops; i++) {
                    ctx.ackInFlightMsg(i);
                }
            } catch (Throwable e) {
                failed.set(true);
            } finally {
                latch.countDown();
            }
        });

        releaser.start();
        acker.start();
        boolean finished = latch.await(10, TimeUnit.SECONDS);
        assertTrue("Threads did not finish in time", finished);
        assertFalse("An exception was thrown during release vs ack", failed.get());
    }

    @Test
    public void receiveMaxOne_serialDelivery() {
        ctx = new PublishedInFlightCtxImpl(flowControlService, clientSessionCtx, deliveryService, stats, 1, 5);

        assertTrue(ctx.addInFlightMsg(qos1(1)));

        MqttPublishMessage second = qos1(2);
        assertFalse(ctx.addInFlightMsg(second));

        ctx.ackInFlightMsg(1);

        ArgumentCaptor<MqttPublishMessage> captor = ArgumentCaptor.forClass(MqttPublishMessage.class);
        verify(deliveryService, times(1)).sendAlreadyTrackedPublishMsgToClient(same(clientSessionCtx), captor.capture());
        assertEquals(2, captor.getValue().variableHeader().packetId());
    }

    private MqttPublishMessage qos1(int packetId) {
        MqttFixedHeader fixed = new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.AT_LEAST_ONCE, false, 0);
        return new MqttPublishMessage(fixed,
                new MqttPublishVariableHeader("t", packetId),
                Unpooled.buffer(1).writeByte(0x42));
    }

    private MqttPublishMessage qos0(int packetId) {
        // QoS 0 never reaches the release path, EMPTY_BUFFER is fine.
        MqttFixedHeader fixed = new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.AT_MOST_ONCE, false, 0);
        return new MqttPublishMessage(fixed, new MqttPublishVariableHeader("t", packetId), Unpooled.EMPTY_BUFFER);
    }

}
