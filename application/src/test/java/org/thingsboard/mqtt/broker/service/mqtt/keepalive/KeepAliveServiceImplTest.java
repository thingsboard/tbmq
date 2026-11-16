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
package org.thingsboard.mqtt.broker.service.mqtt.keepalive;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.common.stats.DefaultStatsFactory;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.processing.backpressure.KeepAliveProbe;
import org.thingsboard.mqtt.broker.service.processing.backpressure.PublishBackpressure;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;
import org.thingsboard.mqtt.broker.standalone.pipeline.PipelineBudget;
import org.thingsboard.mqtt.broker.standalone.pipeline.PipelineProperties;
import org.thingsboard.mqtt.broker.standalone.pipeline.StandalonePublishBackpressure;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class KeepAliveServiceImplTest {

    private static final String CLIENT_ID = "clientId";

    ClientMqttActorManager clientMqttActorManager;
    PublishBackpressure publishBackpressure;
    KeepAliveServiceImpl keepAliveService;

    @BeforeEach
    void setUp() {
        clientMqttActorManager = mock(ClientMqttActorManager.class);
        publishBackpressure = mock(PublishBackpressure.class);
        when(publishBackpressure.probe(any(), anyLong())).thenReturn(KeepAliveProbe.CLOSE);
        keepAliveService = spy(new KeepAliveServiceImpl(clientMqttActorManager, publishBackpressure));
    }

    @Test
    void testIsInactive() {
        var ts = System.currentTimeMillis() - 20000;
        assertThat(keepAliveService.isInactive(10, ts)).isTrue();
        ts = System.currentTimeMillis() - 10000;
        assertThat(keepAliveService.isInactive(10, ts)).isFalse();
    }

    @Test
    void testAcknowledgeControlPacket() {
        assertThatThrownBy(() -> keepAliveService.acknowledgeControlPacket(UUID.randomUUID()))
                .isInstanceOf(MqttException.class);
    }

    @Test
    void testKeepAliveLifecycle() throws InterruptedException {
        UUID sessionId1 = UUID.randomUUID();
        keepAliveService.registerSession("clientId1", sessionId1, 10);
        keepAliveService.registerSession("clientId2", UUID.randomUUID(), 20);
        keepAliveService.registerSession("clientId3", UUID.randomUUID(), 30);

        keepAliveService.processKeepAlive();

        verify(clientMqttActorManager, never()).disconnect(any(), any());
        assertThat(keepAliveService.getKeepAliveInfoSize()).isEqualTo(3);

        keepAliveService.registerSession("clientId4", UUID.randomUUID(), 0);
        assertThat(keepAliveService.getKeepAliveInfoSize()).isEqualTo(4);

        // need to sleep to wait for keepAlive to be expired
        Thread.sleep(5);

        keepAliveService.processKeepAlive();

        verify(clientMqttActorManager, times(0)).disconnect(any(), any());

        keepAliveService.unregisterSession(sessionId1);
        assertThat(keepAliveService.getKeepAliveInfoSize()).isEqualTo(3);
    }

    @Test
    void givenActiveSession_whenProcessKeepAlive_thenItIsNotProbed() throws MqttException {
        UUID sessionId = UUID.randomUUID();
        keepAliveService.registerSession(CLIENT_ID, sessionId, 10);

        keepAliveService.processKeepAlive();

        verify(publishBackpressure, never()).probe(any(), anyLong());
        verify(clientMqttActorManager, never()).disconnect(any(), any());
    }

    @Test
    void givenActiveSession_whenProcessKeepAlive_thenAPausedConnectionIsReadEveryHalfKeepAlive() throws MqttException {
        UUID sessionId = UUID.randomUUID();
        keepAliveService.registerSession(CLIENT_ID, sessionId, 10);

        keepAliveService.processKeepAlive();

        verify(publishBackpressure).readIfDue(sessionId, 5000);
    }

    @Test
    void givenKeepAliveOff_whenProcessKeepAlive_thenNoReadAndNoProbe() throws Exception {
        keepAliveService.registerSession(CLIENT_ID, UUID.randomUUID(), 0);
        Thread.sleep(2);

        keepAliveService.processKeepAlive();

        verifyNoInteractions(publishBackpressure);
    }

    @Test
    void givenInactiveSession_whenProcessKeepAlive_thenProbedInsteadOfRead() throws MqttException {
        UUID sessionId = UUID.randomUUID();
        keepAliveService.registerSession(CLIENT_ID, sessionId, 10);
        expire();

        keepAliveService.processKeepAlive();

        verify(publishBackpressure).probe(eq(sessionId), anyLong());
        verify(publishBackpressure, never()).readIfDue(any(), anyLong());
    }

    @Test
    void givenInactiveSessionAndProbeClose_whenProcessKeepAlive_thenItIsDisconnectedForKeepAlive() throws MqttException {
        UUID sessionId = UUID.randomUUID();
        keepAliveService.registerSession(CLIENT_ID, sessionId, 10);
        expire();

        keepAliveService.processKeepAlive();

        verify(publishBackpressure).probe(eq(sessionId), anyLong());
        verify(clientMqttActorManager).disconnect(eq(CLIENT_ID), argThat(msg -> isKeepAliveDisconnect(msg, sessionId)));
        assertThat(keepAliveService.getKeepAliveInfoSize()).isEqualTo(0);
    }

    @Test
    void givenInactiveSessionAndProbeWait_whenProcessKeepAlive_thenItIsSkippedAndKept() throws MqttException {
        UUID sessionId = UUID.randomUUID();
        keepAliveService.registerSession(CLIENT_ID, sessionId, 10);
        expire();
        when(publishBackpressure.probe(eq(sessionId), anyLong())).thenReturn(KeepAliveProbe.WAIT);

        keepAliveService.processKeepAlive();

        verify(clientMqttActorManager, never()).disconnect(any(), any());
        assertThat(keepAliveService.getKeepAliveInfoSize()).isEqualTo(1);
        // the session is still inactive: the next check probes it again
        keepAliveService.processKeepAlive();
        verify(publishBackpressure, times(2)).probe(eq(sessionId), anyLong());
    }

    @Test
    void givenInactiveSessionAndProbeAlive_whenProcessKeepAlive_thenItsLastPacketTimeIsRefreshedAndItIsSkipped() throws MqttException {
        UUID sessionId = UUID.randomUUID();
        keepAliveService.registerSession(CLIENT_ID, sessionId, 10);
        expire();
        when(publishBackpressure.probe(eq(sessionId), anyLong())).thenReturn(KeepAliveProbe.ALIVE);

        keepAliveService.processKeepAlive();

        verify(clientMqttActorManager, never()).disconnect(any(), any());
        assertThat(keepAliveService.getKeepAliveInfoSize()).isEqualTo(1);
        // refreshed: the session is no longer inactive, so the next check does not probe it
        keepAliveService.processKeepAlive();
        verify(publishBackpressure).probe(eq(sessionId), anyLong());
    }

    // [MQTT-3.1.2-22]: a paused connection does not read, so a live client's PINGREQ waits in the socket
    @Test
    void mqtt_3_1_2_22_aLiveClientOnAPausedConnectionIsNotClosed() throws MqttException {
        Connection paused = pausedSession();
        expire();

        keepAliveService.processKeepAlive(); // probe -> channel.read()

        verify(paused.channel()).read();
        verifyNoInteractions(clientMqttActorManager);

        paused.backpressure().onChannelRead(paused.session()); // the PINGREQ was read
        keepAliveService.processKeepAlive();
        keepAliveService.processKeepAlive();

        verify(clientMqttActorManager, never()).disconnect(any(), any());
        // refreshed once: no further probe after the ALIVE one
        verify(paused.channel()).read();
        assertThat(keepAliveService.getKeepAliveInfoSize()).isEqualTo(1);
    }

    @Test
    void mqtt_3_1_2_22_aDeadClientOnAPausedConnectionIsClosedOnTheNextTick() throws MqttException {
        Connection paused = pausedSession();
        expire();

        keepAliveService.processKeepAlive();
        verifyNoInteractions(clientMqttActorManager);
        keepAliveService.processKeepAlive();

        verify(paused.channel()).read();
        verify(clientMqttActorManager).disconnect(eq(CLIENT_ID),
                argThat(msg -> isKeepAliveDisconnect(msg, paused.session().getSessionId())));
    }

    // data read by the probe is counted once: when the actor refreshed the session for it, the next deadline probes again
    @Test
    void mqtt_3_1_2_22_dataAlreadyCountedByTheSessionDoesNotKeepADeadClientOpen() throws Exception {
        Connection paused = pausedSession();
        expire();

        keepAliveService.processKeepAlive(); // probe -> read #1
        paused.backpressure().onChannelRead(paused.session()); // the PINGREQ
        Thread.sleep(2);
        keepAliveService.acknowledgeControlPacket(paused.session().getSessionId()); // the actor counts it
        expire(); // a keep-alive time later, the client has died

        keepAliveService.processKeepAlive();
        verify(paused.channel(), times(2)).read();
        verifyNoInteractions(clientMqttActorManager);

        keepAliveService.processKeepAlive();
        verify(clientMqttActorManager).disconnect(eq(CLIENT_ID),
                argThat(msg -> isKeepAliveDisconnect(msg, paused.session().getSessionId())));
    }

    // the reads before the deadline find nothing: the deadline probes, and the next check closes, as without them
    @Test
    void mqtt_3_1_2_22_aDeadClientOnAPausedConnectionReadEveryHalfKeepAliveIsStillClosedOnTheTickAfterItsDeadline()
            throws MqttException {
        Connection paused = pausedSession();
        // every check before the deadline finds the read due
        doReturn(0L).when(keepAliveService).pausedReadIntervalMs(anyInt());

        keepAliveService.processKeepAlive();
        keepAliveService.processKeepAlive();
        verify(paused.channel(), times(2)).read();
        expire();

        keepAliveService.processKeepAlive();
        verify(paused.channel(), times(3)).read();
        verifyNoInteractions(clientMqttActorManager);

        keepAliveService.processKeepAlive();
        verify(paused.channel(), times(3)).read();
        verify(clientMqttActorManager).disconnect(eq(CLIENT_ID),
                argThat(msg -> isKeepAliveDisconnect(msg, paused.session().getSessionId())));
    }

    @Test
    void aConnectionThatIsNotPausedIsClosedAtOnceAsToday() throws MqttException {
        Connection connection = connection(false);
        expire();

        keepAliveService.processKeepAlive();

        verify(connection.channel(), never()).read();
        verify(clientMqttActorManager).disconnect(eq(CLIENT_ID),
                argThat(msg -> isKeepAliveDisconnect(msg, connection.session().getSessionId())));
    }

    // The keep-alive thread refreshes the last packet time when a probe finds the client alive. A packet the actor
    // acknowledges meanwhile must not be refused: it would disconnect a live client.
    @Test
    void givenKeepAliveThreadRefreshesLastPacketTime_whenActorAcknowledgesPackets_thenNoneIsRefused() throws Exception {
        UUID sessionId = UUID.randomUUID();
        // stubOnly: neither is verified, and recording ~200k calls would cost memory and time
        PublishBackpressure stubbedBackpressure = mock(PublishBackpressure.class, withSettings().stubOnly());
        KeepAliveServiceImpl keepAliveService = mock(KeepAliveServiceImpl.class, withSettings()
                .spiedInstance(new KeepAliveServiceImpl(clientMqttActorManager, stubbedBackpressure))
                .defaultAnswer(CALLS_REAL_METHODS).stubOnly());
        keepAliveService.registerSession(CLIENT_ID, sessionId, 10);
        when(stubbedBackpressure.probe(eq(sessionId), anyLong())).thenReturn(KeepAliveProbe.ALIVE);
        doReturn(true).when(keepAliveService).isInactive(anyInt(), anyLong());

        AtomicBoolean stop = new AtomicBoolean();
        Thread refresher = new Thread(() -> {
            while (!stop.get()) {
                keepAliveService.processKeepAlive();
            }
        });
        refresher.start();
        try {
            for (int i = 0; i < 200_000; i++) {
                keepAliveService.acknowledgeControlPacket(sessionId);
            }
        } finally {
            stop.set(true);
            refresher.join(5000);
        }

        verify(clientMqttActorManager, never()).disconnect(any(), any());
        assertThat(keepAliveService.getKeepAliveInfoSize()).isEqualTo(1);
    }

    @Test
    void givenSessionRefreshedByTheKeepAlive_whenAcknowledgeControlPacket_thenItIsAccepted() throws Exception {
        UUID sessionId = UUID.randomUUID();
        keepAliveService.registerSession(CLIENT_ID, sessionId, 10);
        expire();
        when(publishBackpressure.probe(eq(sessionId), anyLong())).thenReturn(KeepAliveProbe.ALIVE);
        keepAliveService.processKeepAlive();

        keepAliveService.acknowledgeControlPacket(sessionId);

        verify(clientMqttActorManager, never()).disconnect(any(), any());
    }

    @Test
    void givenSessionClosedForInactivity_whenAcknowledgeControlPacket_thenItIsRefused() throws MqttException {
        UUID sessionId = UUID.randomUUID();
        keepAliveService.registerSession(CLIENT_ID, sessionId, 10);
        expire();
        keepAliveService.processKeepAlive();

        assertThatThrownBy(() -> keepAliveService.acknowledgeControlPacket(sessionId))
                .isInstanceOf(MqttException.class);
    }

    // the sessions registered so far are inactive, and the ones refreshed from now on are not
    private void expire() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long threshold = System.currentTimeMillis();
        doAnswer(invocation -> (long) invocation.getArgument(1) < threshold)
                .when(keepAliveService).isInactive(anyInt(), anyLong());
    }

    private static boolean isKeepAliveDisconnect(MqttDisconnectMsg msg, UUID sessionId) {
        return msg.getSessionId().equals(sessionId) && msg.getReason().getType() == DisconnectReasonType.ON_KEEP_ALIVE;
    }

    private Connection pausedSession() throws MqttException {
        return connection(true);
    }

    // a keep-alive service over a real back-pressure with a budget that pauses at 8 queued messages, and a registered
    // session that is paused or not
    private Connection connection(boolean paused) throws MqttException {
        PipelineProperties properties = new PipelineProperties();
        properties.getPipeline().setMaxMessages(10);
        DefaultStatsFactory statsFactory = new DefaultStatsFactory(new SimpleMeterRegistry());
        PipelineBudget budget = new PipelineBudget(properties, statsFactory);
        StandalonePublishBackpressure backpressure = new StandalonePublishBackpressure(budget, statsFactory);
        keepAliveService = spy(new KeepAliveServiceImpl(clientMqttActorManager, backpressure));

        Channel channel = mock(Channel.class);
        when(channel.config()).thenReturn(mock(ChannelConfig.class));
        when(channel.isActive()).thenReturn(true);
        ChannelHandlerContext channelCtx = mock(ChannelHandlerContext.class);
        when(channelCtx.channel()).thenReturn(channel);
        ClientSessionCtx session = new ClientSessionCtx();
        session.setChannel(channelCtx);

        keepAliveService.registerSession(CLIENT_ID, session.getSessionId(), 10);
        if (paused) {
            for (int i = 0; i < 8; i++) {
                assertThat(budget.tryAcquire(1)).isTrue();
            }
            backpressure.onPublish(session);
        }
        return new Connection(session, channel, backpressure);
    }

    private record Connection(ClientSessionCtx session, Channel channel, StandalonePublishBackpressure backpressure) {
    }

}
