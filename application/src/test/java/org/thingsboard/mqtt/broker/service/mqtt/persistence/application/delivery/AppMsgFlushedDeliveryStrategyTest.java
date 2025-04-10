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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.delivery;

import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationSubmitStrategy;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.PersistedPublishMsg;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppMsgFlushedDeliveryStrategyTest {

    PublishMsgDeliveryService deliveryService;
    ClientLogger clientLogger;

    ApplicationSubmitStrategy submitStrategy;
    ClientSessionCtx clientSessionCtx;
    PersistedPublishMsg msg1, msg2, msg3;

    @BeforeEach
    void setUp() {
        clientSessionCtx = mock(ClientSessionCtx.class);
        when(clientSessionCtx.getClientId()).thenReturn("test-client");
        ChannelHandlerContext channel = mock(ChannelHandlerContext.class);
        when(clientSessionCtx.getChannel()).thenReturn(channel);

        deliveryService = mock(PublishMsgDeliveryService.class);
        clientLogger = mock(ClientLogger.class);
        submitStrategy = mock(ApplicationSubmitStrategy.class);

        msg1 = mock(PersistedPublishMsg.class);
        msg2 = mock(PersistedPublishMsg.class);
        msg3 = mock(PersistedPublishMsg.class);

        when(msg1.getPacketType()).thenReturn(PersistedPacketType.PUBLISH);
        when(msg2.getPacketType()).thenReturn(PersistedPacketType.PUBLISH);
        when(msg3.getPacketType()).thenReturn(PersistedPacketType.PUBLISH);

        when(msg1.getPublishMsg()).thenReturn(mock(PublishMsg.class));
        when(msg2.getPublishMsg()).thenReturn(mock(PublishMsg.class));
        when(msg3.getPublishMsg()).thenReturn(mock(PublishMsg.class));
    }

    @Test
    void testFlushedDeliveryStrategyFlushesEachMessage() {
        var strategy = new AppMsgFlushedDeliveryStrategy(deliveryService, clientLogger);

        mockSubmitStrategyConsuming();

        strategy.process(submitStrategy, clientSessionCtx);

        verify(deliveryService, times(3)).sendPublishMsgToClientWithoutFlush(eq(clientSessionCtx), any());
        verify(clientSessionCtx.getChannel(), times(3)).flush();
        verify(clientLogger, times(3)).logEvent(eq("test-client"), any(), any());
    }

    @Test
    void testFlushedDeliveryStrategyFlushesEachMessageWithPubRel() {
        when(msg3.getPacketType()).thenReturn(PersistedPacketType.PUBREL);
        when(msg3.getPacketId()).thenReturn(1);

        var strategy = new AppMsgFlushedDeliveryStrategy(deliveryService, clientLogger);

        mockSubmitStrategyConsuming();

        strategy.process(submitStrategy, clientSessionCtx);

        verify(deliveryService, times(2)).sendPublishMsgToClientWithoutFlush(eq(clientSessionCtx), any());
        verify(deliveryService).sendPubRelMsgToClientWithoutFlush(eq(clientSessionCtx), eq(1));
        verify(clientSessionCtx.getChannel(), times(3)).flush();
        verify(clientLogger, times(3)).logEvent(eq("test-client"), any(), any());
    }

    private void mockSubmitStrategyConsuming() {
        doAnswer(invocation -> {
            var consumer = invocation.getArgument(0, java.util.function.Consumer.class);
            consumer.accept(msg1);
            consumer.accept(msg2);
            consumer.accept(msg3);
            return null;
        }).when(submitStrategy).process(any());
    }
}
