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
package org.thingsboard.mqtt.broker.service.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.integration.IntegrationSubscriptionUpdateService;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.dao.integration.IntegrationService;
import org.thingsboard.mqtt.broker.gen.queue.IntegrationLifecycleConfigProto;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.service.notification.InternodeNotificationsService;
import org.thingsboard.mqtt.broker.service.queue.IntegrationTopicService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationCleanupServiceImplTest {

    static final long TTL_MS = TimeUnit.DAYS.toMillis(7);

    @Mock
    IntegrationService integrationService;
    @Mock
    IntegrationTopicService integrationTopicService;
    @Mock
    IntegrationSubscriptionUpdateService integrationSubscriptionUpdateService;
    @Mock
    IntegrationLifecycleEventTypeCache lifecycleEventTypeCache;
    @Mock
    InternodeNotificationsService internodeNotificationsService;

    @InjectMocks
    IntegrationCleanupServiceImpl service;

    @BeforeEach
    void setUp() {
        IntegrationExpiryChecker expiryChecker = new IntegrationExpiryChecker();
        ReflectionTestUtils.setField(expiryChecker, "ttlMs", TTL_MS);
        ReflectionTestUtils.setField(service, "expiryChecker", expiryChecker);
    }

    @Test
    void givenIntegrationId_whenDeleteIntegrationTopics_thenDeletesBothDataAndEventTopics() {
        String integrationId = UUID.randomUUID().toString();

        service.deleteIntegrationTopics(integrationId);

        verify(integrationTopicService).deleteTopic(eq(integrationId), any(BasicCallback.class));
        verify(integrationTopicService).deleteEventTopic(eq(integrationId), any(BasicCallback.class));
    }

    /**
     * IntegrationTopicServiceImpl.deleteTopic deletes the consumer group before the callback-bearing topic delete and
     * rethrows on anything but GroupIdNotFoundException, so a shared try would let one failure skip the other topic -
     * and the sweep would never retry, having already detached the integration.
     */
    @Test
    void givenDataTopicDeletionThrows_whenDeleteIntegrationTopics_thenStillDeletesTheEventTopic() {
        String integrationId = UUID.randomUUID().toString();
        doThrow(new RuntimeException("kafka admin timeout"))
                .when(integrationTopicService).deleteTopic(eq(integrationId), any(BasicCallback.class));

        service.deleteIntegrationTopics(integrationId);

        verify(integrationTopicService).deleteEventTopic(eq(integrationId), any(BasicCallback.class));
    }

    @Test
    void givenEventTopicDeletionThrows_whenDeleteIntegrationTopics_thenDoesNotPropagate() {
        String integrationId = UUID.randomUUID().toString();
        doThrow(new RuntimeException("kafka admin timeout"))
                .when(integrationTopicService).deleteEventTopic(eq(integrationId), any(BasicCallback.class));

        assertThatCode(() -> service.deleteIntegrationTopics(integrationId)).doesNotThrowAnyException();

        verify(integrationTopicService).deleteTopic(eq(integrationId), any(BasicCallback.class));
    }

    /**
     * The exception reaching this callback may be wrapped, either by the Kafka future or by
     * IntegrationTopicServiceImpl.deleteConsumerGroup's rethrow, so a bare instanceof would log a routine missing
     * topic as a warning.
     */
    @Test
    void givenTopicMissingExceptionWrappedTwoLevelsDeep_whenDeleteIntegrationTopics_thenLogsAtDebugNotWarn() {
        String integrationId = UUID.randomUUID().toString();
        doThrow(new RuntimeException(new ExecutionException(new UnknownTopicOrPartitionException("Topic missing"))))
                .when(integrationTopicService).deleteTopic(eq(integrationId), any(BasicCallback.class));
        Logger logger = (Logger) LoggerFactory.getLogger(IntegrationCleanupServiceImpl.class);
        // The class's effective level is WARN (root), so DEBUG must be raised here to observe the expected message -
        // otherwise this test could pass vacuously even if the callback were never reached at all.
        // Mutating this shared Logback logger's level is safe only because this module has no junit-platform.properties
        // enabling concurrent test execution, unlike common/data and integration/executor - if that ever changes here,
        // this and the next test need isolating too.
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.deleteIntegrationTopics(integrationId);

            assertThat(appender.list)
                    .as("a topic-missing error wrapped inside other exceptions must still be treated as expected")
                    .noneMatch(event -> event.getLevel() == Level.WARN)
                    .anyMatch(event -> event.getLevel() == Level.DEBUG && event.getFormattedMessage().contains("does not exist"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
    }

    @Test
    void givenUnrelatedException_whenDeleteIntegrationTopics_thenStillLogsWarn() {
        String integrationId = UUID.randomUUID().toString();
        doThrow(new RuntimeException("kafka admin timeout"))
                .when(integrationTopicService).deleteTopic(eq(integrationId), any(BasicCallback.class));
        Logger logger = (Logger) LoggerFactory.getLogger(IntegrationCleanupServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.deleteIntegrationTopics(integrationId);

            assertThat(appender.list)
                    .as("an unrelated failure must still be logged at WARN - the predicate has not become always-true")
                    .anyMatch(event -> event.getLevel() == Level.WARN);
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void givenExpiredDisabledIntegrationWithSubscriptions_whenCleanUp_thenDetachesAndDeletesBothTopics() {
        Integration integration = givenIntegrations(expiredDisabledIntegration());
        String integrationId = integration.getIdStr();
        when(integrationSubscriptionUpdateService.hasSubscriptions(integrationId)).thenReturn(true);

        service.cleanUp();

        verify(integrationSubscriptionUpdateService).clearSubscriptions(integrationId);
        verify(lifecycleEventTypeCache).remove(integrationId);
        verify(integrationTopicService).deleteTopic(eq(integrationId), any(BasicCallback.class));
        verify(integrationTopicService).deleteEventTopic(eq(integrationId), any(BasicCallback.class));
    }

    /**
     * The event type cache is node-local while the topics are deleted cluster-wide, so the eviction has to reach the
     * other nodes - otherwise they keep publishing lifecycle events into a topic that no longer exists.
     */
    @Test
    void givenEvictedEventTypes_whenCleanUp_thenBroadcastsTheEvictionToTheCluster() {
        Integration integration = givenIntegrations(expiredDisabledIntegration());
        when(lifecycleEventTypeCache.remove(integration.getIdStr())).thenReturn(true);

        service.cleanUp();

        ArgumentCaptor<InternodeNotificationProto> captor = ArgumentCaptor.forClass(InternodeNotificationProto.class);
        verify(internodeNotificationsService).broadcast(captor.capture());
        IntegrationLifecycleConfigProto broadcast = captor.getValue().getIntegrationLifecycleConfigProto();
        assertThat(broadcast.getIntegrationId()).isEqualTo(integration.getIdStr());
        // Not a delete: the row survives, so the eviction is expressed as an opt-in with no event types.
        assertThat(broadcast.getDeleted()).isFalse();
        assertThat(broadcast.getLifecycleEventTypesList()).isEmpty();
    }

    /**
     * The sweeping node can legitimately have nothing cached while another node still does: the startup skip leaves
     * the events cache empty on a node that restarted after the expiry, while its subscriptions come back from the
     * subscriptions topic. Gating the broadcast on the local eviction would delete both topics cluster-wide without
     * telling the nodes that are still publishing.
     */
    @Test
    void givenOnlySubscriptionsDetachedOnThisNode_whenCleanUp_thenStillBroadcastsTheEviction() {
        Integration integration = givenIntegrations(expiredDisabledIntegration());
        when(integrationSubscriptionUpdateService.hasSubscriptions(integration.getIdStr())).thenReturn(true);
        // lifecycleEventTypeCache.remove returns false: nothing was cached on this node

        service.cleanUp();

        verify(internodeNotificationsService).broadcast(any(InternodeNotificationProto.class));
    }

    @Test
    void givenAlreadyDetachedIntegration_whenCleanUp_thenDoesNotBroadcast() {
        givenIntegrations(expiredDisabledIntegration());
        // both hasSubscriptions and remove report nothing: an earlier sweep already did the work

        service.cleanUp();

        verifyNoInteractions(internodeNotificationsService);
    }

    @Test
    void givenExpiredDisabledEventsOnlyIntegration_whenCleanUp_thenDetachesAndDeletesBothTopics() {
        Integration integration = givenIntegrations(expiredDisabledIntegration());
        String integrationId = integration.getIdStr();
        // nothing to unsubscribe, but the events stream is still attached
        when(lifecycleEventTypeCache.remove(integrationId)).thenReturn(true);

        service.cleanUp();

        verify(integrationTopicService).deleteTopic(eq(integrationId), any(BasicCallback.class));
        verify(integrationTopicService).deleteEventTopic(eq(integrationId), any(BasicCallback.class));
    }

    @Test
    void givenAlreadyDetachedIntegration_whenCleanUp_thenSkipsTopicDeletion() {
        Integration integration = givenIntegrations(expiredDisabledIntegration());
        // both report nothing detached: an earlier sweep already did the work

        service.cleanUp();

        verify(integrationSubscriptionUpdateService).hasSubscriptions(integration.getIdStr());
        verify(integrationSubscriptionUpdateService, never()).clearSubscriptions(integration.getIdStr());
        verify(lifecycleEventTypeCache).remove(integration.getIdStr());
        verifyNoInteractions(integrationTopicService);
    }

    @Test
    void givenEnabledIntegration_whenCleanUp_thenLeavesItAlone() {
        givenIntegrations(newIntegration(true, System.currentTimeMillis() - TTL_MS - 1));

        service.cleanUp();

        verifyNoInteractions(integrationSubscriptionUpdateService, lifecycleEventTypeCache, integrationTopicService);
    }

    @Test
    void givenNotYetExpiredDisabledIntegration_whenCleanUp_thenLeavesItAlone() {
        givenIntegrations(newIntegration(false, System.currentTimeMillis() - TTL_MS + TimeUnit.HOURS.toMillis(1)));

        service.cleanUp();

        verifyNoInteractions(integrationSubscriptionUpdateService, lifecycleEventTypeCache, integrationTopicService);
    }

    /**
     * The page may have been fetched before a loop of blocking admin calls, so the destructive part must act on the
     * re-read row: an integration enabled since must be left running with its subscriptions and event types intact.
     */
    @Test
    void givenIntegrationEnabledSinceItWasFetched_whenCleanUp_thenLeavesItAlone() {
        Integration stale = expiredDisabledIntegration();
        givenIntegrations(stale);
        Integration current = newIntegration(true, stale.getDisconnectedTime());
        current.setId(stale.getId());
        when(integrationService.findIntegrationById(stale.getId())).thenReturn(current);

        service.cleanUp();

        verifyNoInteractions(integrationSubscriptionUpdateService, lifecycleEventTypeCache, integrationTopicService);
    }

    @Test
    void givenIntegrationDeletedSinceItWasFetched_whenCleanUp_thenLeavesItAlone() {
        Integration stale = expiredDisabledIntegration();
        givenIntegrations(stale);
        when(integrationService.findIntegrationById(stale.getId())).thenReturn(null);

        service.cleanUp();

        verifyNoInteractions(integrationSubscriptionUpdateService, lifecycleEventTypeCache, integrationTopicService);
    }

    @Test
    void givenCleanupDisabled_whenCleanUp_thenDoesNothing() {
        IntegrationExpiryChecker disabled = new IntegrationExpiryChecker();
        ReflectionTestUtils.setField(disabled, "ttlMs", 0L);
        ReflectionTestUtils.setField(service, "expiryChecker", disabled);

        service.cleanUp();

        verifyNoInteractions(integrationService, integrationSubscriptionUpdateService, lifecycleEventTypeCache, integrationTopicService);
    }

    /**
     * The loop must advance the page link and stop on the last page. With a single-page stub neither the advance nor
     * the exit condition is ever executed.
     */
    @Test
    void givenTwoPagesOfIntegrations_whenCleanUp_thenSweepsBothAndStops() {
        Integration first = expiredDisabledIntegration();
        Integration second = expiredDisabledIntegration();
        when(integrationService.findIntegrations(any(PageLink.class)))
                .thenReturn(new PageData<>(List.of(first), 2, 2, true))
                .thenReturn(new PageData<>(List.of(second), 2, 2, false));
        when(integrationService.findIntegrationById(first.getId())).thenReturn(first);
        when(integrationService.findIntegrationById(second.getId())).thenReturn(second);
        when(integrationSubscriptionUpdateService.hasSubscriptions(first.getIdStr())).thenReturn(true);
        when(integrationSubscriptionUpdateService.hasSubscriptions(second.getIdStr())).thenReturn(true);

        service.cleanUp();

        verify(integrationTopicService).deleteTopic(eq(first.getIdStr()), any(BasicCallback.class));
        verify(integrationTopicService).deleteTopic(eq(second.getIdStr()), any(BasicCallback.class));
        ArgumentCaptor<PageLink> pageLinkCaptor = ArgumentCaptor.forClass(PageLink.class);
        verify(integrationService, times(2)).findIntegrations(pageLinkCaptor.capture());
        List<PageLink> pageLinks = pageLinkCaptor.getAllValues();
        // The second call must use the next page, not a repeat of the first - otherwise the sweep never terminates.
        assertThat(pageLinks.get(1).getPage()).isEqualTo(pageLinks.get(0).getPage() + 1);
    }

    @Test
    void givenFailingIntegration_whenCleanUp_thenKeepsSweepingTheRest() {
        Integration failing = expiredDisabledIntegration();
        Integration next = expiredDisabledIntegration();
        givenIntegrations(failing, next);
        doThrow(new RuntimeException("kafka admin timeout"))
                .when(integrationSubscriptionUpdateService).hasSubscriptions(failing.getIdStr());
        when(integrationSubscriptionUpdateService.hasSubscriptions(next.getIdStr())).thenReturn(true);

        service.cleanUp();

        verify(integrationTopicService, never()).deleteTopic(eq(failing.getIdStr()), any(BasicCallback.class));
        verify(integrationTopicService).deleteTopic(eq(next.getIdStr()), any(BasicCallback.class));
        verify(integrationTopicService).deleteEventTopic(eq(next.getIdStr()), any(BasicCallback.class));
    }

    private Integration givenIntegrations(Integration... integrations) {
        when(integrationService.findIntegrations(any(PageLink.class)))
                .thenReturn(new PageData<>(List.of(integrations), 1, integrations.length, false));
        for (Integration integration : integrations) {
            // by default the re-read before detaching returns the same state; lenient because the tests that stop
            // before the re-read must still fail on any other unnecessary stubbing
            lenient().when(integrationService.findIntegrationById(integration.getId())).thenReturn(integration);
        }
        return integrations[0];
    }

    private Integration expiredDisabledIntegration() {
        return newIntegration(false, System.currentTimeMillis() - TTL_MS - 1);
    }

    private Integration newIntegration(boolean enabled, long disconnectedTime) {
        Integration integration = new Integration(UUID.randomUUID());
        integration.setName("test-integration");
        integration.setEnabled(enabled);
        integration.setDisconnectedTime(disconnectedTime);
        return integration;
    }

}
