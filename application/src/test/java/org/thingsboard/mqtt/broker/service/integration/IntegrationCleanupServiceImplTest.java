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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.integration.IntegrationSubscriptionUpdateService;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.dao.integration.IntegrationService;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.service.notification.InternodeNotificationsService;
import org.thingsboard.mqtt.broker.service.queue.IntegrationTopicService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

    @Test
    void givenExpiredDisabledIntegrationWithSubscriptions_whenCleanUp_thenDetachesAndDeletesBothTopics() {
        Integration integration = givenIntegrations(expiredDisabledIntegration());
        String integrationId = integration.getIdStr();
        when(integrationSubscriptionUpdateService.clearSubscriptions(integrationId)).thenReturn(true);

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
        assertThat(captor.getValue().getIntegrationLifecycleConfigProto().getIntegrationId()).isEqualTo(integration.getIdStr());
        assertThat(captor.getValue().getIntegrationLifecycleConfigProto().getDeleted()).isTrue();
    }

    @Test
    void givenNothingCachedForIntegration_whenCleanUp_thenDoesNotBroadcast() {
        Integration integration = givenIntegrations(expiredDisabledIntegration());
        when(integrationSubscriptionUpdateService.clearSubscriptions(integration.getIdStr())).thenReturn(true);
        // lifecycleEventTypeCache.remove returns false: nothing was cached on this node

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

        verify(integrationSubscriptionUpdateService).clearSubscriptions(integration.getIdStr());
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

    @Test
    void givenFailingIntegration_whenCleanUp_thenKeepsSweepingTheRest() {
        Integration failing = expiredDisabledIntegration();
        Integration next = expiredDisabledIntegration();
        givenIntegrations(failing, next);
        doThrow(new RuntimeException("kafka admin timeout"))
                .when(integrationSubscriptionUpdateService).clearSubscriptions(failing.getIdStr());
        when(integrationSubscriptionUpdateService.clearSubscriptions(next.getIdStr())).thenReturn(true);

        service.cleanUp();

        verify(integrationTopicService, never()).deleteTopic(eq(failing.getIdStr()), any(BasicCallback.class));
        verify(integrationTopicService).deleteTopic(eq(next.getIdStr()), any(BasicCallback.class));
        verify(integrationTopicService).deleteEventTopic(eq(next.getIdStr()), any(BasicCallback.class));
    }

    private Integration givenIntegrations(Integration... integrations) {
        when(integrationService.findIntegrations(any(PageLink.class)))
                .thenReturn(new PageData<>(List.of(integrations), 1, integrations.length, false));
        for (Integration integration : integrations) {
            // by default the re-read before detaching returns the same state
            when(integrationService.findIntegrationById(integration.getId())).thenReturn(integration);
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
