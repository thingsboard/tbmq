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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.mqtt.broker.common.data.integration.ClientLifecycleEventTypeUtil;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.dao.integration.IntegrationService;
import org.thingsboard.mqtt.broker.service.integration.PlatformIntegrationService;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.notification.InternodeNotificationsService;
import org.thingsboard.mqtt.broker.service.queue.IntegrationTopicService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultTbIntegrationServiceTest {

    static final ObjectMapper MAPPER = new ObjectMapper();
    static final UUID INTEGRATION_ID = UUID.fromString("0198e1a0-1111-2222-3333-444455556666");

    @Mock
    IntegrationService integrationService;
    @Mock
    PlatformIntegrationService platformIntegrationService;
    @Mock
    RateLimitService rateLimitService;
    @Mock
    InternodeNotificationsService internodeNotificationsService;
    @Mock
    IntegrationTopicService integrationTopicService;

    @InjectMocks
    DefaultTbIntegrationService service;

    @Test
    void givenIntegrationOptedInForLifecycleEvents_whenSave_thenCreatesEventTopic() {
        Integration integration = newIntegration(true);
        when(integrationService.saveIntegration(integration)).thenReturn(integration);

        service.save(integration, null);

        verify(integrationTopicService).createEventTopic(INTEGRATION_ID.toString());
    }

    @Test
    void givenIntegrationWithoutLifecycleEvents_whenSave_thenDoesNotCreateEventTopic() {
        Integration integration = newIntegration(false);
        when(integrationService.saveIntegration(integration)).thenReturn(integration);

        service.save(integration, null);

        verify(integrationTopicService, never()).createEventTopic(any());
    }

    @Test
    void givenEventTopicCreationFails_whenSave_thenSaveStillSucceeds() {
        Integration integration = newIntegration(true);
        when(integrationService.saveIntegration(integration)).thenReturn(integration);
        when(integrationTopicService.createEventTopic(INTEGRATION_ID.toString()))
                .thenThrow(new RuntimeException("Kafka is down"));

        service.save(integration, null);

        verify(internodeNotificationsService).broadcast(any());
    }

    private Integration newIntegration(boolean optedInForLifecycleEvents) {
        Integration integration = new Integration(INTEGRATION_ID);
        ObjectNode configuration = MAPPER.createObjectNode();
        configuration.putArray("topicFilters").add("#");
        if (optedInForLifecycleEvents) {
            configuration.putArray(ClientLifecycleEventTypeUtil.LIFECYCLE_EVENT_TYPES_KEY).add("CLIENT_CONNECTED");
        }
        integration.setConfiguration(configuration);
        return integration;
    }

}
