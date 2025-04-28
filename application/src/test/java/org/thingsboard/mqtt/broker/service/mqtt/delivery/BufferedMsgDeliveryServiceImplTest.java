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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.delivery.BufferedMsgDeliveryServiceImpl.SessionFlushState;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BufferedMsgDeliveryServiceImplTest {

    private PublishMsgDeliveryService publishMsgDeliveryService;
    private BufferedMsgDeliveryServiceImpl bufferedService;

    @BeforeEach
    void setup() {
        publishMsgDeliveryService = mock(PublishMsgDeliveryService.class);

        bufferedService = new BufferedMsgDeliveryServiceImpl(publishMsgDeliveryService);
        // Set fields manually since @Value won't inject in tests
        bufferedService.setFlushSchedulerIntervalMs(100);
        bufferedService.setIdleFlushTimeoutMs(1000);
        bufferedService.setSessionFlushCacheExpirationMs(1000);
        bufferedService.setSessionFlushCacheMaxSize(100);
        bufferedService.setWriteAndFlush(false);
        bufferedService.setBufferedMsgCount(2);
        bufferedService.setPersistentWriteAndFlush(false);
        bufferedService.setPersistentBufferedMsgCount(2);

        bufferedService.init();
    }

    @Test
    void testSendPublishMsgToClient_WithBufferedFlush() throws Exception {
        UUID sessionId = UUID.randomUUID();
        ClientSessionCtx ctx = mockSessionCtx(sessionId, "client1");
        MqttPublishMessage mqttMsg = mock(MqttPublishMessage.class);

        bufferedService.sendPublishMsgToRegularClient(ctx, mqttMsg);
        verify(publishMsgDeliveryService).doSendPublishMsgToClientWithoutFlush(eq(ctx), eq(mqttMsg));

        // Trigger flush after bufferedMsgCount
        bufferedService.sendPublishMsgToRegularClient(ctx, mqttMsg);
        verify(publishMsgDeliveryService, times(2)).doSendPublishMsgToClientWithoutFlush(eq(ctx), eq(mqttMsg));
    }

    @Test
    void testSendPublishMsgToClient_WithWriteAndFlush() {
        bufferedService.setWriteAndFlush(true);
        ClientSessionCtx ctx = mockSessionCtx(UUID.randomUUID(), "client2");
        MqttPublishMessage mqttMsg = mock(MqttPublishMessage.class);

        bufferedService.sendPublishMsgToRegularClient(ctx, mqttMsg);
        verify(publishMsgDeliveryService).doSendPublishMsgToClient(eq(ctx), eq(mqttMsg));
    }

    @Test
    void testFlushPendingBuffers_ForceFlush() {
        UUID sessionId = UUID.randomUUID();
        ClientSessionCtx ctx = mockSessionCtx(sessionId, "client3");
        MqttPublishMessage mqttMsg = mock(MqttPublishMessage.class);

        bufferedService.sendPublishMsgToRegularClient(ctx, mqttMsg);

        bufferedService.flushPendingBuffers(true);
    }

    @Test
    void testRemovalListenerFlush() {
        UUID sessionId = UUID.randomUUID();
        ClientSessionCtx ctx = mockSessionCtx(sessionId, "client4");

        ChannelHandlerContext channelCtx = ctx.getChannel();
        Channel channel = channelCtx.channel();
        EventExecutor executor = channelCtx.executor();

        // Ensure buffered count > 0 for eviction to trigger flush
        BufferedMsgDeliveryServiceImpl.SessionFlushState state =
                new BufferedMsgDeliveryServiceImpl.SessionFlushState(System.currentTimeMillis(), new AtomicInteger(1), ctx);

        // Get the removal listener and trigger onRemoval manually
        RemovalListener<UUID, BufferedMsgDeliveryServiceImpl.SessionFlushState> listener =
                bufferedService.getCacheRemovalListener();

        // Create a proper RemovalNotification
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
        when(channel.isActive()).thenReturn(true);

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run(); // run immediately for test
            return null;
        }).when(executor).execute(any(Runnable.class));

        return ctx;
    }


}
