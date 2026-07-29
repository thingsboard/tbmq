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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.integration.IntegrationSubscriptionUpdateService;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.dao.integration.IntegrationService;
import org.thingsboard.mqtt.broker.service.queue.IntegrationTopicService;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

    @InjectMocks
    IntegrationCleanupServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "ttlMs", TTL_MS);
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
        Integration integration = expiredDisabledIntegration();
        String integrationId = integration.getIdStr();
        givenIntegrations(integration);
        when(integrationSubscriptionUpdateService.processSubscriptionsUpdate(integrationId, Collections.emptySet())).thenReturn(true);

        service.cleanUp();

        verify(integrationSubscriptionUpdateService).processSubscriptionsUpdate(integrationId, Collections.emptySet());
        verify(lifecycleEventTypeCache).remove(integrationId);
        verify(integrationTopicService).deleteTopic(eq(integrationId), any(BasicCallback.class));
        verify(integrationTopicService).deleteEventTopic(eq(integrationId), any(BasicCallback.class));
    }

    @Test
    void givenExpiredDisabledEventsOnlyIntegration_whenCleanUp_thenDetachesAndDeletesBothTopics() {
        Integration integration = expiredDisabledIntegration();
        String integrationId = integration.getIdStr();
        givenIntegrations(integration);
        // nothing to unsubscribe, but the events stream is still attached
        when(lifecycleEventTypeCache.remove(integrationId)).thenReturn(true);

        service.cleanUp();

        verify(integrationTopicService).deleteTopic(eq(integrationId), any(BasicCallback.class));
        verify(integrationTopicService).deleteEventTopic(eq(integrationId), any(BasicCallback.class));
    }

    @Test
    void givenAlreadyDetachedIntegration_whenCleanUp_thenSkipsTopicDeletion() {
        Integration integration = expiredDisabledIntegration();
        givenIntegrations(integration);
        // both report nothing detached: an earlier sweep already did the work

        service.cleanUp();

        verify(integrationSubscriptionUpdateService).processSubscriptionsUpdate(integration.getIdStr(), Collections.emptySet());
        verify(lifecycleEventTypeCache).remove(integration.getIdStr());
        verifyNoInteractions(integrationTopicService);
    }

    @Test
    void givenEnabledIntegration_whenCleanUp_thenLeavesItAlone() {
        Integration integration = newIntegration(true, System.currentTimeMillis() - TTL_MS - 1);
        givenIntegrations(integration);

        service.cleanUp();

        verifyNoInteractions(integrationSubscriptionUpdateService, lifecycleEventTypeCache, integrationTopicService);
    }

    @Test
    void givenNotYetExpiredDisabledIntegration_whenCleanUp_thenLeavesItAlone() {
        Integration integration = newIntegration(false, System.currentTimeMillis() - TTL_MS + TimeUnit.HOURS.toMillis(1));
        givenIntegrations(integration);

        service.cleanUp();

        verifyNoInteractions(integrationSubscriptionUpdateService, lifecycleEventTypeCache, integrationTopicService);
    }

    @Test
    void givenCleanupDisabled_whenCleanUp_thenDoesNothing() {
        ReflectionTestUtils.setField(service, "ttlMs", 0L);

        service.cleanUp();

        verifyNoInteractions(integrationService, integrationSubscriptionUpdateService, lifecycleEventTypeCache, integrationTopicService);
    }

    @Test
    void givenFailingIntegration_whenCleanUp_thenKeepsSweepingTheRest() {
        Integration failing = expiredDisabledIntegration();
        Integration next = expiredDisabledIntegration();
        givenIntegrations(failing, next);
        doThrow(new RuntimeException("kafka admin timeout"))
                .when(integrationSubscriptionUpdateService).processSubscriptionsUpdate(failing.getIdStr(), Collections.emptySet());
        when(integrationSubscriptionUpdateService.processSubscriptionsUpdate(next.getIdStr(), Collections.emptySet())).thenReturn(true);

        service.cleanUp();

        verify(integrationTopicService, never()).deleteTopic(eq(failing.getIdStr()), any(BasicCallback.class));
        verify(integrationTopicService).deleteTopic(eq(next.getIdStr()), any(BasicCallback.class));
        verify(integrationTopicService).deleteEventTopic(eq(next.getIdStr()), any(BasicCallback.class));
    }

    /**
     * Every integration is re-read immediately before it is examined, so that one enabled while the sweep is running
     * is not detached on the strength of a stale reading.
     */
    @Test
    void givenSeveralIntegrations_whenCleanUp_thenFetchesThemOneAtATime() {
        givenIntegrations(expiredDisabledIntegration(), expiredDisabledIntegration());

        service.cleanUp();

        verify(integrationService, times(2)).findIntegrations(any(PageLink.class));
    }

    private void givenIntegrations(Integration... integrations) {
        // one integration per page, mirroring CLEANUP_PAGE_SIZE
        var stubbing = when(integrationService.findIntegrations(any(PageLink.class)));
        for (int i = 0; i < integrations.length; i++) {
            boolean hasNext = i < integrations.length - 1;
            stubbing = stubbing.thenReturn(new PageData<>(List.of(integrations[i]), integrations.length, integrations.length, hasNext));
        }
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
