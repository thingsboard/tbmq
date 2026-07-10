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
package org.thingsboard.mqtt.broker.service.mqtt.delivery;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.concurrent.GenericFutureListener;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.stats.DefaultStatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.stats.timer.TimerStats;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DefaultMqttPublishMsgDeliveryServiceTest {

    @Mock
    ClientSessionCtx ctx;
    @Mock
    ChannelHandlerContext channel;
    @Mock
    ChannelFuture future;
    @Mock
    MqttPublishMessage msg;
    @Mock
    TbMessageStatsReportClient reportClient;

    SimpleMeterRegistry meterRegistry;

    @Before
    public void setUp() {
        lenient().when(ctx.getChannel()).thenReturn(channel);
    }

    private DefaultMqttPublishMsgDeliveryService buildService(boolean statsEnabled) {
        meterRegistry = new SimpleMeterRegistry();
        StatsFactory statsFactory = new DefaultStatsFactory(meterRegistry);
        TimerStats timerStats = new TimerStats(statsFactory);
        StatsManager statsManager = mock(StatsManager.class);
        when(statsManager.isEnabled()).thenReturn(statsEnabled);
        when(statsManager.getDeliveryTimerStats()).thenReturn(timerStats);
        return new DefaultMqttPublishMsgDeliveryService(reportClient, statsManager);
    }

    private Timer deliveryTimer() {
        return meterRegistry.find(StatsType.DELIVERY.getPrintName()).timer();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private GenericFutureListener captureListener() {
        ArgumentCaptor<GenericFutureListener> captor = ArgumentCaptor.forClass(GenericFutureListener.class);
        verify(future).addListener(captor.capture());
        return captor.getValue();
    }

    private void stubPersistence(boolean persistent) {
        SessionInfo sessionInfo = mock(SessionInfo.class);
        when(sessionInfo.isPersistent()).thenReturn(persistent);
        when(ctx.getSessionInfo()).thenReturn(sessionInfo);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void givenStatsEnabled_whenSendPublish_thenDeliveryRecordedOnFutureCompletionNotSynchronously() throws Exception {
        DefaultMqttPublishMsgDeliveryService service = buildService(true);
        when(ctx.addInFlightMsg(msg)).thenReturn(true);
        when(channel.writeAndFlush(msg)).thenReturn(future);
        when(future.isSuccess()).thenReturn(true);

        service.sendPublishMsgToClient(ctx, msg);

        // Delivery must be recorded when the async write completes, not synchronously at submission.
        assertEquals(0, deliveryTimer().count());
        captureListener().operationComplete(future);
        assertEquals(1, deliveryTimer().count());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void givenAsyncFailureNonPersistentNonRetained_whenSendPublish_thenReportsDroppedAndNoDelivery() throws Exception {
        DefaultMqttPublishMsgDeliveryService service = buildService(true);
        MqttFixedHeader fixedHeader = mock(MqttFixedHeader.class);
        when(fixedHeader.isRetain()).thenReturn(false);
        when(msg.fixedHeader()).thenReturn(fixedHeader);
        stubPersistence(false);
        when(ctx.addInFlightMsg(msg)).thenReturn(true);
        when(channel.writeAndFlush(msg)).thenReturn(future);
        when(future.isSuccess()).thenReturn(false);

        service.sendPublishMsgToClient(ctx, msg);
        captureListener().operationComplete(future);

        verify(reportClient, times(1)).reportDroppedMsgs();
        assertEquals(0, deliveryTimer().count());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void givenAsyncFailurePersistent_whenSendPublish_thenNotCounted() throws Exception {
        DefaultMqttPublishMsgDeliveryService service = buildService(true);
        MqttFixedHeader fixedHeader = mock(MqttFixedHeader.class);
        when(fixedHeader.isRetain()).thenReturn(false);
        when(msg.fixedHeader()).thenReturn(fixedHeader);
        stubPersistence(true);
        when(ctx.addInFlightMsg(msg)).thenReturn(true);
        when(channel.writeAndFlush(msg)).thenReturn(future);
        when(future.isSuccess()).thenReturn(false);

        service.sendPublishMsgToClient(ctx, msg);
        captureListener().operationComplete(future);

        verify(reportClient, never()).reportDroppedMsgs();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void givenAsyncFailureQos0Persistent_whenSendPublish_thenReportsDropped() throws Exception {
        DefaultMqttPublishMsgDeliveryService service = buildService(true);
        MqttFixedHeader fixedHeader = mock(MqttFixedHeader.class);
        when(fixedHeader.isRetain()).thenReturn(false);
        when(fixedHeader.qosLevel()).thenReturn(MqttQoS.AT_MOST_ONCE);
        when(msg.fixedHeader()).thenReturn(fixedHeader);
        stubPersistence(true);
        when(ctx.addInFlightMsg(msg)).thenReturn(true);
        when(channel.writeAndFlush(msg)).thenReturn(future);
        when(future.isSuccess()).thenReturn(false);

        service.sendPublishMsgToClient(ctx, msg);
        captureListener().operationComplete(future);

        verify(reportClient, times(1)).reportDroppedMsgs();
        assertEquals(0, deliveryTimer().count());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void givenAsyncFailureRetained_whenSendPublish_thenNotCounted() throws Exception {
        DefaultMqttPublishMsgDeliveryService service = buildService(true);
        MqttFixedHeader fixedHeader = mock(MqttFixedHeader.class);
        when(fixedHeader.isRetain()).thenReturn(true);
        when(msg.fixedHeader()).thenReturn(fixedHeader);
        when(ctx.addInFlightMsg(msg)).thenReturn(true);
        when(channel.writeAndFlush(msg)).thenReturn(future);
        when(future.isSuccess()).thenReturn(false);

        service.sendPublishMsgToClient(ctx, msg);
        captureListener().operationComplete(future);

        verify(reportClient, never()).reportDroppedMsgs();
    }

    @Test
    public void givenStatsDisabled_whenSendPublish_thenNoListenerAttachedButMessageStillWritten() {
        DefaultMqttPublishMsgDeliveryService service = buildService(false);
        when(ctx.addInFlightMsg(msg)).thenReturn(true);
        when(channel.writeAndFlush(msg)).thenReturn(future);

        service.sendPublishMsgToClient(ctx, msg);

        verify(channel).writeAndFlush(msg);
        verify(future, never()).addListener(any(GenericFutureListener.class));
        assertNotNull(deliveryTimer());
        assertEquals(0, deliveryTimer().count());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void givenStatsEnabled_whenSendPublishWithoutFlush_thenWritesWithoutFlushAndRecordsOnCompletion() throws Exception {
        DefaultMqttPublishMsgDeliveryService service = buildService(true);
        when(ctx.addInFlightMsg(msg)).thenReturn(true);
        when(channel.write(msg)).thenReturn(future);
        when(future.isSuccess()).thenReturn(true);

        service.sendPublishMsgToClientWithoutFlush(ctx, msg);

        verify(channel).write(msg);
        captureListener().operationComplete(future);
        assertEquals(1, deliveryTimer().count());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void givenStatsEnabled_whenSendAlreadyTracked_thenRecordsOnCompletion() throws Exception {
        DefaultMqttPublishMsgDeliveryService service = buildService(true);
        when(channel.writeAndFlush(msg)).thenReturn(future);
        when(future.isSuccess()).thenReturn(true);

        service.sendAlreadyTrackedPublishMsgToClient(ctx, msg);

        assertEquals(0, deliveryTimer().count());
        captureListener().operationComplete(future);
        assertEquals(1, deliveryTimer().count());
    }

    @Test
    public void givenSyncThrowNonPersistentNonRetained_whenSendAlreadyTracked_thenReportsDroppedAndRecordsNothing() {
        DefaultMqttPublishMsgDeliveryService service = buildService(true);
        MqttFixedHeader fixedHeader = mock(MqttFixedHeader.class);
        when(fixedHeader.isRetain()).thenReturn(false);
        when(msg.fixedHeader()).thenReturn(fixedHeader);
        stubPersistence(false);
        when(channel.writeAndFlush(msg)).thenThrow(new RuntimeException("boom"));

        service.sendAlreadyTrackedPublishMsgToClient(ctx, msg);

        verify(reportClient, times(1)).reportDroppedMsgs();
        verify(future, never()).addListener(any(GenericFutureListener.class));
        assertEquals(0, deliveryTimer().count());
    }

    @Test
    public void givenSyncThrowPersistentQos1_whenSendAlreadyTracked_thenNotCounted() {
        DefaultMqttPublishMsgDeliveryService service = buildService(true);
        MqttFixedHeader fixedHeader = mock(MqttFixedHeader.class);
        when(fixedHeader.isRetain()).thenReturn(false);
        when(fixedHeader.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        when(msg.fixedHeader()).thenReturn(fixedHeader);
        stubPersistence(true);
        when(channel.writeAndFlush(msg)).thenThrow(new RuntimeException("boom"));

        service.sendAlreadyTrackedPublishMsgToClient(ctx, msg);

        verify(reportClient, never()).reportDroppedMsgs();
    }

    @Test
    public void givenInFlightSlotNotReserved_whenSendPublish_thenNothingWrittenOrRecorded() {
        DefaultMqttPublishMsgDeliveryService service = buildService(true);
        when(ctx.addInFlightMsg(msg)).thenReturn(false);

        service.sendPublishMsgToClient(ctx, msg);

        verify(channel, never()).writeAndFlush(any());
        verify(future, never()).addListener(any(GenericFutureListener.class));
        verify(reportClient, never()).reportDroppedMsgs();
        assertEquals(0, deliveryTimer().count());
    }

    @Test
    public void givenWriteThrowsSynchronously_whenSendPublishNonRetained_thenReportsDroppedAndRecordsNothing() {
        DefaultMqttPublishMsgDeliveryService service = buildService(true);
        MqttFixedHeader fixedHeader = mock(MqttFixedHeader.class);
        when(fixedHeader.isRetain()).thenReturn(false);
        when(msg.fixedHeader()).thenReturn(fixedHeader);
        stubPersistence(false);
        when(ctx.addInFlightMsg(msg)).thenReturn(true);
        when(channel.writeAndFlush(msg)).thenThrow(new RuntimeException("boom"));

        service.sendPublishMsgToClient(ctx, msg);

        verify(reportClient, times(1)).reportDroppedMsgs();
        verify(future, never()).addListener(any(GenericFutureListener.class));
        assertEquals(0, deliveryTimer().count());
    }

    @Test
    public void givenSyncThrowQos0Persistent_whenSendPublish_thenReportsDropped() {
        DefaultMqttPublishMsgDeliveryService service = buildService(true);
        MqttFixedHeader fixedHeader = mock(MqttFixedHeader.class);
        when(fixedHeader.isRetain()).thenReturn(false);
        when(fixedHeader.qosLevel()).thenReturn(MqttQoS.AT_MOST_ONCE);
        when(msg.fixedHeader()).thenReturn(fixedHeader);
        stubPersistence(true);
        when(ctx.addInFlightMsg(msg)).thenReturn(true);
        when(channel.writeAndFlush(msg)).thenThrow(new RuntimeException("boom"));

        service.sendPublishMsgToClient(ctx, msg);

        verify(reportClient, times(1)).reportDroppedMsgs();
        verify(future, never()).addListener(any(GenericFutureListener.class));
        assertEquals(0, deliveryTimer().count());
    }
}
