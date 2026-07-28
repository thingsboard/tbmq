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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.subscription.IntegrationTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.dao.integration.IntegrationService;
import org.thingsboard.mqtt.broker.service.queue.IntegrationTopicService;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationCleanupServiceImplTest {

    static final String INTEGRATION_ID = "0198e1a0-1111-2222-3333-444455556666";
    static final long TTL_MS = TimeUnit.DAYS.toMillis(7);

    @Mock
    IntegrationService integrationService;
    @Mock
    IntegrationTopicService integrationTopicService;
    @Mock
    ClientSubscriptionService clientSubscriptionService;
    @Mock
    IntegrationLifecycleEventTypeCache lifecycleEventTypeCache;

    @InjectMocks
    IntegrationCleanupServiceImpl service;

    @Test
    void givenIntegrationId_whenDeleteIntegrationTopic_thenDeletesBothDataAndEventTopics() {
        service.deleteIntegrationTopic(INTEGRATION_ID);

        verify(integrationTopicService).deleteTopic(INTEGRATION_ID, CallbackUtil.EMPTY);
        verify(integrationTopicService).deleteEventTopic(INTEGRATION_ID, CallbackUtil.EMPTY);
    }

    @Test
    void givenExpiredDisabledIntegrationWithSubscriptions_whenCleanUp_thenStopsProducingIntoBothStreams() {
        Integration integration = givenExpiredDisabledIntegration();
        String integrationId = integration.getIdStr();
        when(clientSubscriptionService.getClientSubscriptions(integrationId))
                .thenReturn(Set.of(new IntegrationTopicSubscription("#")));

        service.cleanUp();

        verify(clientSubscriptionService).clearSubscriptionsAndPersist(integrationId);
        verify(lifecycleEventTypeCache).remove(integrationId);
        verify(integrationTopicService).deleteTopic(integrationId, CallbackUtil.EMPTY);
        verify(integrationTopicService).deleteEventTopic(integrationId, CallbackUtil.EMPTY);
    }

    @Test
    void givenAlreadyCleanedUpIntegration_whenCleanUp_thenDoesNotRepersistEmptySubscriptions() {
        Integration integration = givenExpiredDisabledIntegration();
        when(clientSubscriptionService.getClientSubscriptions(integration.getIdStr())).thenReturn(Set.of());

        service.cleanUp();

        verify(clientSubscriptionService, never()).clearSubscriptionsAndPersist(any());
    }

    @Test
    void givenEnabledIntegration_whenCleanUp_thenLeavesItAlone() {
        Integration integration = givenExpiredDisabledIntegration();
        integration.setEnabled(true);

        service.cleanUp();

        verifyNoInteractions(clientSubscriptionService, lifecycleEventTypeCache, integrationTopicService);
    }

    private Integration givenExpiredDisabledIntegration() {
        ReflectionTestUtils.setField(service, "ttlMs", TTL_MS);
        Integration integration = new Integration(UUID.randomUUID());
        integration.setName("test-integration");
        integration.setEnabled(false);
        integration.setDisconnectedTime(System.currentTimeMillis() - TTL_MS - 1);
        when(integrationService.findAllIntegrations()).thenReturn(List.of(integration));
        return integration;
    }

}
