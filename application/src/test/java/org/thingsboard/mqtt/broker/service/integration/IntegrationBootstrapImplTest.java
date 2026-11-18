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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.thingsboard.mqtt.broker.common.data.integration.ClientLifecycleEventType;
import org.thingsboard.mqtt.broker.common.data.integration.ClientLifecycleEventTypeUtil;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.integration.IntegrationService;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IntegrationBootstrapImplTest {

    @Mock
    IntegrationService integrationService;
    @Mock
    IntegrationLifecycleEventTypeCache lifecycleEventTypeCache;
    @Mock
    IntegrationExpiryChecker expiryChecker;

    IntegrationBootstrapImpl integrationBootstrap;

    @BeforeEach
    void setUp() {
        integrationBootstrap = new IntegrationBootstrapImpl(integrationService, lifecycleEventTypeCache, expiryChecker);
    }

    @Test
    void integrationsCountComesFromTheIntegrationService() {
        doReturn(7).when(integrationService).findIntegrationsCount();

        assertThat(integrationBootstrap.integrationsCount()).isEqualTo(7);
    }

    @Test
    void testLoadLifecycleEventCacheCachesOptedInIntegration() {
        Integration integration = newIntegration("CLIENT_CONNECTED");
        doReturn(List.of(integration)).when(integrationService).findAllIntegrations();

        integrationBootstrap.loadLifecycleEventCache();

        verify(lifecycleEventTypeCache).put(integration.getIdStr(), Set.of(ClientLifecycleEventType.CLIENT_CONNECTED));
    }

    @Test
    void testLoadLifecycleEventCacheSkipsIntegrationWithoutEventTypes() {
        Integration integration = newIntegration();
        doReturn(List.of(integration)).when(integrationService).findAllIntegrations();

        integrationBootstrap.loadLifecycleEventCache();

        verify(lifecycleEventTypeCache, never()).put(any(), any());
    }

    /**
     * The integration is opted in as far as the predicate is concerned, but none of its names maps to a known type,
     * so the cache put must be suppressed - the one branch where the opt-in check and the parsed event types
     * disagree.
     */
    @Test
    void testLoadLifecycleEventCacheSkipsIntegrationWithOnlyUnknownEventTypes() {
        Integration integration = newIntegration("NOT_A_REAL_TYPE");
        doReturn(List.of(integration)).when(integrationService).findAllIntegrations();

        integrationBootstrap.loadLifecycleEventCache();

        verify(lifecycleEventTypeCache, never()).put(any(), any());
    }

    /**
     * An integration the cleanup sweep considers expired must not be re-attached to the events stream, otherwise a
     * restart would resume its lifecycle events until the next sweep and make that sweep redo its full work.
     */
    @Test
    void testLoadLifecycleEventCacheSkipsExpiredIntegration() {
        Integration integration = newIntegration("CLIENT_CONNECTED");
        doReturn(List.of(integration)).when(integrationService).findAllIntegrations();
        doReturn(true).when(expiryChecker).isExpired(integration);

        integrationBootstrap.loadLifecycleEventCache();

        verify(lifecycleEventTypeCache, never()).put(any(), any());
    }

    private Integration newIntegration(String... lifecycleEventTypes) {
        Integration integration = new Integration(UUID.randomUUID());
        ObjectNode configuration = JacksonUtil.newObjectNode();
        ArrayNode eventTypes = configuration.putArray(ClientLifecycleEventTypeUtil.LIFECYCLE_EVENT_TYPES_KEY);
        for (String eventType : lifecycleEventTypes) {
            eventTypes.add(eventType);
        }
        integration.setConfiguration(configuration);
        return integration;
    }

}
