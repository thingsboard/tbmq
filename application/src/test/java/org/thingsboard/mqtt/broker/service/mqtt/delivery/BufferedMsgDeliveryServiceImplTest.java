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
package org.thingsboard.mqtt.broker.service.mqtt.delivery;

import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.util.concurrent.EventExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.service.mqtt.delivery.BufferedMsgDeliveryServiceImpl.SessionFlushState;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BufferedMsgDeliveryServiceImplTest {

    private MqttMsgDeliveryService mqttMsgDeliveryService;
    private BufferedMsgDeliveryServiceImpl bufferedMsgDeliveryService;

    @BeforeEach
    void setup() {
        mqttMsgDeliveryService = mock(MqttMsgDeliveryService.class);
        BufferedMsgDeliverySettings bufferedMsgDeliverySettings = new BufferedMsgDeliverySettings();
        bufferedMsgDeliverySettings.setSchedulerExecutionIntervalMs(100);
        bufferedMsgDeliverySettings.setIdleSessionFlushTimeoutMs(1000);
        bufferedMsgDeliverySettings.setSessionCacheExpirationMs(1000);
        bufferedMsgDeliverySettings.setSessionCacheMaxSize(100);

        bufferedMsgDeliveryService = new BufferedMsgDeliveryServiceImpl(mqttMsgDeliveryService, bufferedMsgDeliverySettings);

        bufferedMsgDeliveryService.setWriteAndFlush(false);
        bufferedMsgDeliveryService.setBufferedMsgCount(2);
        bufferedMsgDeliveryService.setPersistentWriteAndFlush(false);
        bufferedMsgDeliveryService.setPersistentBufferedMsgCount(3);

        bufferedMsgDeliveryService.init();
    }

    @AfterEach
    void tearDown() {
        bufferedMsgDeliveryService.shutdown();
    }

    @Test
    void testSendPublishMsgToClient_WithBufferedFlush() {
        UUID sessionId = UUID.randomUUID();
        ClientSessionCtx ctx = mockSessionCtx(sessionId, "client1");
        MqttPublishMessage mqttMsg = mock(MqttPublishMessage.class);

        bufferedMsgDeliveryService.sendPublishMsgToRegularClient(ctx, mqttMsg);
        verify(mqttMsgDeliveryService).sendPublishMsgToClientWithoutFlush(eq(ctx), eq(mqttMsg));

        bufferedMsgDeliveryService.sendPublishMsgToRegularClient(ctx, mqttMsg);
        verify(mqttMsgDeliveryService, times(2)).sendPublishMsgToClientWithoutFlush(eq(ctx), eq(mqttMsg));

        verify(ctx.getChannel()).flush();
        assertThat(bufferedMsgDeliveryService.getCache().getIfPresent(sessionId).getBufferedCount()).isZero();
    }

    @Test
    void testSendPublishMsgToClient_WithWriteAndFlush() {
        bufferedMsgDeliveryService.setWriteAndFlush(true);
        UUID sessionId = UUID.randomUUID();
        ClientSessionCtx ctx = mockSessionCtx(sessionId, "client2");
        MqttPublishMessage mqttMsg = mock(MqttPublishMessage.class);

        bufferedMsgDeliveryService.sendPublishMsgToRegularClient(ctx, mqttMsg);
        verify(mqttMsgDeliveryService).sendPublishMsgToClient(eq(ctx), eq(mqttMsg));
        assertThat(bufferedMsgDeliveryService.getCache().getIfPresent(sessionId)).isNull();
    }

    @Test
    void testSendPublishMsgToPersistentClient_WithBufferedFlush() {
        UUID sessionId = UUID.randomUUID();
        ClientSessionCtx ctx = mockSessionCtx(sessionId, "client11");
        MqttPublishMessage mqttMsg = mock(MqttPublishMessage.class);

        bufferedMsgDeliveryService.sendPublishMsgToDeviceClient(ctx, mqttMsg);
        verify(mqttMsgDeliveryService).sendPublishMsgToClientWithoutFlush(eq(ctx), eq(mqttMsg));

        bufferedMsgDeliveryService.sendPublishMsgToDeviceClient(ctx, mqttMsg);
        verify(mqttMsgDeliveryService, times(2)).sendPublishMsgToClientWithoutFlush(eq(ctx), eq(mqttMsg));

        bufferedMsgDeliveryService.sendPublishMsgToDeviceClient(ctx, mqttMsg);
        verify(mqttMsgDeliveryService, times(3)).sendPublishMsgToClientWithoutFlush(eq(ctx), eq(mqttMsg));

        verify(ctx.getChannel()).flush();
        assertThat(bufferedMsgDeliveryService.getCache().getIfPresent(sessionId).getBufferedCount()).isZero();
    }

    @Test
    void testSendPublishMsgToPersistentClient_WithWriteAndFlush() {
        bufferedMsgDeliveryService.setPersistentWriteAndFlush(true);
        UUID sessionId = UUID.randomUUID();
        ClientSessionCtx ctx = mockSessionCtx(sessionId, "client22");
        MqttPublishMessage mqttMsg = mock(MqttPublishMessage.class);

        bufferedMsgDeliveryService.sendPublishMsgToDeviceClient(ctx, mqttMsg);
        verify(mqttMsgDeliveryService).sendPublishMsgToClient(eq(ctx), eq(mqttMsg));
        assertThat(bufferedMsgDeliveryService.getCache().getIfPresent(sessionId)).isNull();
    }

    @Test
    void testFlushPendingBuffers_ForceFlush() {
        UUID sessionId = UUID.randomUUID();
        ClientSessionCtx ctx = mockSessionCtx(sessionId, "client3");
        MqttPublishMessage mqttMsg = mock(MqttPublishMessage.class);

        bufferedMsgDeliveryService.sendPublishMsgToRegularClient(ctx, mqttMsg);

        bufferedMsgDeliveryService.flushPendingBuffers(true);
        verify(ctx.getChannel()).flush();
        assertThat(bufferedMsgDeliveryService.getCache().getIfPresent(sessionId).getBufferedCount()).isZero();
    }

    @Test
    void testShutdown_ForceFlush() {
        UUID sessionId = UUID.randomUUID();
        ClientSessionCtx ctx = mockSessionCtx(sessionId, "client4");
        MqttPublishMessage mqttMsg = mock(MqttPublishMessage.class);

        bufferedMsgDeliveryService.sendPublishMsgToRegularClient(ctx, mqttMsg);
        assertThat(bufferedMsgDeliveryService.getCache().getIfPresent(sessionId).getBufferedCount()).isEqualTo(1);
        bufferedMsgDeliveryService.shutdown();

        verify(ctx.getChannel()).flush();
        assertThat(bufferedMsgDeliveryService.getCache().getIfPresent(sessionId).getBufferedCount()).isZero();
    }

    @Test
    void testRemovalListenerFlush() {
        UUID sessionId = UUID.randomUUID();
        ClientSessionCtx ctx = mockSessionCtx(sessionId, "client5");

        ChannelHandlerContext channelCtx = ctx.getChannel();
        EventExecutor executor = channelCtx.executor();

        BufferedMsgDeliveryServiceImpl.SessionFlushState state =
                new BufferedMsgDeliveryServiceImpl.SessionFlushState(System.currentTimeMillis(), new AtomicInteger(1), ctx);

        RemovalListener<UUID, BufferedMsgDeliveryServiceImpl.SessionFlushState> listener =
                bufferedMsgDeliveryService.getCacheRemovalListener();

        RemovalNotification<UUID, SessionFlushState> notification =
                RemovalNotification.create(sessionId, state, RemovalCause.SIZE);

        listener.onRemoval(notification);

        verify(executor).execute(any());
    }

    private ClientSessionCtx mockSessionCtx(UUID sessionId, String clientId) {
        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        when(ctx.getSessionId()).thenReturn(sessionId);
        when(ctx.getClientId()).thenReturn(clientId);

        ChannelHandlerContext channelCtx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        EventExecutor executor = mock(EventExecutor.class);

        when(ctx.getChannel()).thenReturn(channelCtx);
        when(channelCtx.executor()).thenReturn(executor);
        when(channelCtx.channel()).thenReturn(channel);

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        return ctx;
    }

}
