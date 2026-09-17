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
package org.thingsboard.mqtt.broker.service.trace;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.gen.queue.ClientTraceEventProto;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.provider.ClientTraceQueueFactory;
import org.thingsboard.mqtt.broker.server.traffic.DuplexTrafficHandler;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ClientTraceRecorderTest {
    private ClientTraceRegistry registry;
    private ClientTraceRecorder recorder;
    private ArrayBlockingQueue<ClientTraceEventProto> queue;
    private Semaphore slots;
    private final UUID session = UUID.randomUUID();

    @Before
    public void setup() {
        registry = mock(ClientTraceRegistry.class);
        ServiceInfoProvider service = mock(ServiceInfoProvider.class);
        when(service.getServiceId()).thenReturn("test");
        when(registry.get("client")).thenReturn(Optional.of(new ClientTraceRegistry.ActiveTrace(
                UUID.randomUUID(), "client", Instant.now().plusSeconds(60), ClientTraceLevel.FULL)));
        recorder = new ClientTraceRecorder(registry, mock(ClientTraceQueueFactory.class), service);
        queue = new ArrayBlockingQueue<>(1);
        slots = new Semaphore(1);
        ReflectionTestUtils.setField(recorder, "queue", queue);
        ReflectionTestUtils.setField(recorder, "slots", slots);
        ReflectionTestUtils.setField(recorder, "maxDetailsLength", 16);
        ReflectionTestUtils.setField(recorder, "maxTopics", 1);
        ((AtomicBoolean) ReflectionTestUtils.getField(recorder, "running")).set(true);
        ((AtomicLong) ReflectionTestUtils.getField(recorder, "nextCapture")).set(System.nanoTime());
    }

    private MqttMessage subscribe() {
        return MqttMessageBuilders.subscribe().messageId(1)
                .addSubscription(MqttQoS.AT_LEAST_ONCE, "a".repeat(10000))
                .addSubscription(MqttQoS.AT_LEAST_ONCE, "excluded").build();
    }

    @Test
    public void fullQueueSkipsEventConstruction() {
        recorder.tryRecord("client", session, null, "IN", subscribe());
        // A null session would fail during event construction: saturation must skip it.
        recorder.tryRecord("client", null, null, "IN", subscribe());
        assertEquals(1, queue.size());
        assertEquals(1, recorder.getDroppedEvents());
        assertEquals(0, slots.availablePermits());
    }

    @Test
    public void constructionFailureReleasesSlotAndDoesNotEscape() {
        recorder.tryRecord("client", null, null, "IN", subscribe());
        assertEquals(1, recorder.getDroppedEvents());
        assertEquals(1, slots.availablePermits());
        recorder.tryRecord("client", session, null, "IN", subscribe());
        assertEquals(1, queue.size());
    }

    @Test
    public void limitsDetailsAndNumberOfTopics() {
        recorder.tryRecord("client", session, null, "IN", subscribe());
        assertEquals(16, queue.remove().getDetails().length());
        slots.release();
        recorder.tryRecord("client", session, null, "IN", MqttMessageBuilders.subscribe().messageId(2)
                .addSubscription(MqttQoS.AT_LEAST_ONCE, "one")
                .addSubscription(MqttQoS.AT_LEAST_ONCE, "two").build());
        assertEquals("one:1", queue.remove().getDetails());
    }

    @Test
    public void rateLimitRejectsBeforeConstruction() {
        ((AtomicLong) ReflectionTestUtils.getField(recorder, "nextCapture")).set(System.nanoTime() + 60_000_000_000L);
        recorder.tryRecord("client", null, null, "IN", subscribe());
        assertTrue(queue.isEmpty());
        assertEquals(1, slots.availablePermits());
        assertEquals(1, recorder.getDroppedEvents());
    }

    @Test
    public void registryFailureDoesNotEscape() {
        when(registry.get("client")).thenThrow(new IllegalStateException("unavailable"));
        recorder.tryRecord("client", session, null, "IN", subscribe());
        assertEquals(1, recorder.getDroppedEvents());
    }

    @Test
    public void stoppedRecorderDoesNotConsultRegistry() {
        ((AtomicBoolean) ReflectionTestUtils.getField(recorder, "running")).set(false);
        recorder.tryRecord("client", session, null, "IN", subscribe());
        verify(registry, never()).get(anyString());
    }

    @Test
    public void outboundWriteStillRunsIfRecorderThrows() throws Exception {
        ClientTraceRecorder broken = mock(ClientTraceRecorder.class);
        doThrow(new IllegalStateException("trace failure")).when(broken)
                .tryRecord(any(), any(), any(), any(), any());
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class, RETURNS_DEEP_STUBS);
        when(ctx.channel()).thenReturn(channel);
        ChannelPromise promise = mock(ChannelPromise.class);
        MqttMessage message = subscribe();
        DuplexTrafficHandler handler = new DuplexTrafficHandler(mock(TbMessageStatsReportClient.class), broken, session);
        try {
            handler.write(ctx, message, promise);
            fail("Expected injected recorder failure");
        } catch (IllegalStateException expected) {
            verify(ctx).write(message, promise);
        }
    }
}
