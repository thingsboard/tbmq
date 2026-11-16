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
package org.thingsboard.mqtt.broker.service.mqtt.will;

import io.netty.handler.codec.mqtt.MqttProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgProcessor;
import org.thingsboard.mqtt.broker.service.processing.MsgDispatcherService;
import org.thingsboard.mqtt.broker.service.processing.PublisherIdentity;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two queries the standalone shutdown makes: which sessions have a Last Will, and when the wills that are due
 * have been handed to the dispatcher.
 */
class DefaultLastWillServiceDueWillsTest {

    private static final long WAIT_SECONDS = 10;

    private final MsgDispatcherService dispatcher = mock(MsgDispatcherService.class);
    private final DefaultLastWillService lastWillService = new DefaultLastWillService(dispatcher,
            mock(RetainedMsgProcessor.class), mock(StatsManager.class), mock(TbMessageStatsReportClient.class));
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @BeforeEach
    void setUp() {
        lastWillService.setScheduler(scheduler);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void givenASavedWill_whenItsSessionDisconnects_thenTheSessionNoLongerHasOne() {
        SessionInfo session = session(0);
        lastWillService.saveLastWillMsg(session, will(0), PublisherIdentity.EMPTY);

        assertThat(lastWillService.hasLastWill(session.getSessionId())).isTrue();
        assertThat(lastWillService.hasLastWill(UUID.randomUUID())).isFalse();

        lastWillService.removeAndExecuteLastWillIfNeeded(shutdownDisconnect(session), -1);

        assertThat(lastWillService.hasLastWill(session.getSessionId())).isFalse();
    }

    @Test
    void givenADueWill_whenItsSessionDisconnects_thenItIsOnTheSchedulerBeforeTheSessionNoLongerHasOne() {
        ScheduledExecutorService willScheduler = mock(ScheduledExecutorService.class);
        lastWillService.setScheduler(willScheduler);
        SessionInfo session = session(0);
        lastWillService.saveLastWillMsg(session, will(0), PublisherIdentity.EMPTY);
        AtomicBoolean hadOneWhenScheduled = new AtomicBoolean();
        when(willScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
            hadOneWhenScheduled.set(lastWillService.hasLastWill(session.getSessionId()));
            return mock(ScheduledFuture.class);
        });

        lastWillService.removeAndExecuteLastWillIfNeeded(shutdownDisconnect(session), -1);

        // the shutdown reads a session without a will as one whose due will is already on the scheduler
        assertThat(hadOneWhenScheduled).isTrue();
        assertThat(lastWillService.hasLastWill(session.getSessionId())).isFalse();
    }

    @Test
    void givenSchedulingFails_whenItsSessionDisconnects_thenTheSessionNoLongerHasOne() {
        ScheduledExecutorService willScheduler = mock(ScheduledExecutorService.class);
        lastWillService.setScheduler(willScheduler);
        when(willScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenThrow(new RejectedExecutionException("stopped"));
        SessionInfo session = session(0);
        lastWillService.saveLastWillMsg(session, will(0), PublisherIdentity.EMPTY);

        lastWillService.removeAndExecuteLastWillIfNeeded(shutdownDisconnect(session), -1);

        assertThat(lastWillService.hasLastWill(session.getSessionId())).isFalse();
    }

    @Test
    void givenADelayedWill_whenItsSessionDisconnects_thenTheSessionNoLongerHasOne() {
        SessionInfo session = session(120);
        lastWillService.saveLastWillMsg(session, will(60), PublisherIdentity.EMPTY);

        lastWillService.removeAndExecuteLastWillIfNeeded(shutdownDisconnect(session), -1);

        assertThat(lastWillService.hasLastWill(session.getSessionId())).isFalse();
    }

    @Test
    void givenAnImmediateWillWaitingForTheScheduler_whenDueLastWillsHandled_thenItCompletesOnlyAfterTheWillWasDispatched() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        scheduler.execute(() -> await(release));
        SessionInfo session = session(0);
        lastWillService.saveLastWillMsg(session, will(0), PublisherIdentity.EMPTY);
        lastWillService.removeAndExecuteLastWillIfNeeded(shutdownDisconnect(session), -1);

        CompletableFuture<Void> handled = lastWillService.dueLastWillsHandled();

        assertThat(handled).isNotDone();
        verify(dispatcher, never()).persistPublishMsg(any(SessionInfo.class), any(), any(), any());
        release.countDown();
        handled.get(WAIT_SECONDS, TimeUnit.SECONDS);
        verify(dispatcher).persistPublishMsg(eq(session), any(PublishMsg.class), eq(PublisherIdentity.EMPTY), any());
    }

    @Test
    void givenADelayedWill_whenDueLastWillsHandled_thenItDoesNotWaitForIt() throws Exception {
        SessionInfo session = session(120);
        lastWillService.saveLastWillMsg(session, will(60), PublisherIdentity.EMPTY);
        lastWillService.removeAndExecuteLastWillIfNeeded(shutdownDisconnect(session), -1);

        lastWillService.dueLastWillsHandled().get(WAIT_SECONDS, TimeUnit.SECONDS);

        verify(dispatcher, never()).persistPublishMsg(any(SessionInfo.class), any(), any(), any());
    }

    @Test
    void givenADisconnect0x00ProcessedBeforeTheShutdownDisconnect_whenTheDueWillsAreHandled_thenNoWillIsPublished() throws Exception {
        SessionInfo session = session(0);
        lastWillService.saveLastWillMsg(session, will(0), PublisherIdentity.EMPTY);

        // the order the client actor keeps when the shutdown disconnect is queued behind the client's DISCONNECT
        lastWillService.removeAndExecuteLastWillIfNeeded(new MqttDisconnectMsg(session.getSessionId(),
                new DisconnectReason(DisconnectReasonType.ON_DISCONNECT_MSG)), -1);
        lastWillService.removeAndExecuteLastWillIfNeeded(shutdownDisconnect(session), -1);
        lastWillService.dueLastWillsHandled().get(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(lastWillService.hasLastWill(session.getSessionId())).isFalse();
        verify(dispatcher, never()).persistPublishMsg(any(SessionInfo.class), any(), any(), any());
    }

    @Test
    void givenAStoppedScheduler_whenDueLastWillsHandled_thenItIsCompleteAtOnce() {
        scheduler.shutdownNow();

        assertThat(lastWillService.dueLastWillsHandled()).isCompleted();
    }

    private static SessionInfo session(int sessionExpiryInterval) {
        UUID sessionId = UUID.randomUUID();
        return SessionInfo.builder()
                .serviceId("tbmq-lite")
                .sessionId(sessionId)
                .cleanStart(true)
                .sessionExpiryInterval(sessionExpiryInterval)
                .clientInfo(ClientInfo.builder().clientId("will-client-" + sessionId).type(ClientType.DEVICE).build())
                .build();
    }

    private static PublishMsg will(int willDelaySeconds) {
        MqttProperties properties = new MqttProperties();
        if (willDelaySeconds > 0) {
            properties.add(new MqttProperties.IntegerProperty(BrokerConstants.WILL_DELAY_INTERVAL_PROP_ID, willDelaySeconds));
        }
        return PublishMsg.builder()
                .topicName("will/topic")
                .payload("gone".getBytes(StandardCharsets.UTF_8))
                .qos(1)
                .properties(properties)
                .build();
    }

    private static MqttDisconnectMsg shutdownDisconnect(SessionInfo session) {
        return new MqttDisconnectMsg(session.getSessionId(), new DisconnectReason(DisconnectReasonType.ON_SERVER_SHUTTING_DOWN));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
