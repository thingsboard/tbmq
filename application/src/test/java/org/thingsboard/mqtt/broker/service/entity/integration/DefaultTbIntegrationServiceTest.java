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
package org.thingsboard.mqtt.broker.service.entity.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.dao.integration.IntegrationService;
import org.thingsboard.mqtt.broker.gen.queue.IntegrationLifecycleConfigProto;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.service.integration.PlatformIntegrationService;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.notification.InternodeNotificationsService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultTbIntegrationServiceTest {

    static final UUID INTEGRATION_ID = UUID.fromString("0198e1a0-1111-2222-3333-444455556666");

    @Mock
    IntegrationService integrationService;
    @Mock
    PlatformIntegrationService platformIntegrationService;
    @Mock
    RateLimitService rateLimitService;
    @Mock
    InternodeNotificationsService internodeNotificationsService;

    @InjectMocks
    DefaultTbIntegrationService service;

    /**
     * The topics are deleted for the whole cluster while the lifecycle event type cache is node-local, so the
     * eviction has to go out first. Nothing on the publish path checks whether the integration still exists - see
     * IntegrationLifecycleEventPublisherImpl.publish - so an event published by a node that still has the event
     * types cached recreates the events topic through the producer's createTopicIfNotExists. That leak is permanent:
     * the row is already gone, and the cleanup sweep only ever reaches topics through their row.
     */
    @Test
    void givenIntegration_whenDelete_thenBroadcastsTheEvictionBeforeDeletingTheTopics() {
        Integration integration = newIntegration();
        when(integrationService.deleteIntegration(integration)).thenReturn(true);

        service.delete(integration, null);

        ArgumentCaptor<InternodeNotificationProto> captor = ArgumentCaptor.forClass(InternodeNotificationProto.class);
        InOrder inOrder = inOrder(internodeNotificationsService, platformIntegrationService);
        inOrder.verify(internodeNotificationsService).broadcast(captor.capture());
        // processIntegrationDelete is what deletes both topics for a disabled integration.
        inOrder.verify(platformIntegrationService).processIntegrationDelete(integration, true);

        IntegrationLifecycleConfigProto broadcast = captor.getValue().getIntegrationLifecycleConfigProto();
        assertThat(broadcast.getIntegrationId()).isEqualTo(integration.getIdStr());
        assertThat(broadcast.getDeleted()).isTrue();
    }

    /**
     * The row being gone is what makes the eviction safe to send unconditionally: broadcasting ahead of the delete
     * would detach a still-live integration from the events stream if the delete then failed.
     */
    @Test
    void givenIntegration_whenDelete_thenBroadcastsOnlyOnceTheRowIsGone() {
        Integration integration = newIntegration();
        when(integrationService.deleteIntegration(integration)).thenReturn(true);

        service.delete(integration, null);

        InOrder inOrder = inOrder(integrationService, internodeNotificationsService);
        inOrder.verify(integrationService).deleteIntegration(integration);
        inOrder.verify(internodeNotificationsService).broadcast(any(InternodeNotificationProto.class));
    }

    private Integration newIntegration() {
        Integration integration = new Integration(INTEGRATION_ID);
        integration.setName("test-integration");
        return integration;
    }

}
