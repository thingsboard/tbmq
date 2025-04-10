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

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppMsgBufferedDeliveryStrategyTest {

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
    void testBufferedDeliveryStrategyFlushesAfterBatch() throws NoSuchFieldException, IllegalAccessException {
        var strategy = new AppMsgBufferedDeliveryStrategy(deliveryService, clientLogger);
        Field field = AppMsgBufferedDeliveryStrategy.class.getDeclaredField("bufferedMsgCount");
        field.setAccessible(true);
        field.set(strategy, 2);

        mockSubmitStrategyConsuming();

        strategy.process(submitStrategy, clientSessionCtx);

        verify(deliveryService, times(3)).sendPublishMsgToClientWithoutFlush(eq(clientSessionCtx), any());
        verify(clientSessionCtx.getChannel(), times(2)).flush(); // once at count=2, once at end
        verify(clientLogger, times(3)).logEvent(eq("test-client"), any(), any());
    }

    @Test
    void testBufferedDeliveryStrategyFlushesAfterBatchWithPubRel() throws NoSuchFieldException, IllegalAccessException {
        when(msg3.getPacketType()).thenReturn(PersistedPacketType.PUBREL);
        when(msg3.getPacketId()).thenReturn(1);

        var strategy = new AppMsgBufferedDeliveryStrategy(deliveryService, clientLogger);
        Field field = AppMsgBufferedDeliveryStrategy.class.getDeclaredField("bufferedMsgCount");
        field.setAccessible(true);
        field.set(strategy, 2);

        mockSubmitStrategyConsuming();

        strategy.process(submitStrategy, clientSessionCtx);

        verify(deliveryService, times(2)).sendPublishMsgToClientWithoutFlush(eq(clientSessionCtx), any());
        verify(deliveryService).sendPubRelMsgToClientWithoutFlush(eq(clientSessionCtx), eq(1));
        verify(clientSessionCtx.getChannel(), times(2)).flush(); // once at count=2, once at end
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
