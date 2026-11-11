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
package org.thingsboard.mqtt.broker.service.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.mqtt.broker.common.data.integration.ClientLifecycleEventType;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionStatsCleanupProto;
import org.thingsboard.mqtt.broker.gen.queue.IntegrationLifecycleConfigProto;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthProviderProto;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthSettingsProto;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRoutingService;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttAuthProviderNotificationManager;
import org.thingsboard.mqtt.broker.service.integration.IntegrationLifecycleEventTypeCache;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionStatsCleanupProcessor;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class InternodeNotificationsApplierTest {

    private static final String SERVICE_ID = "nodeA";

    @Mock
    private MqttAuthProviderNotificationManager mqttAuthProviderNotificationManager;
    @Mock
    private ClientSessionStatsCleanupProcessor clientSessionStatsCleanupProcessor;
    @Mock
    private AuthorizationRoutingService authorizationRoutingService;
    @Mock
    private IntegrationLifecycleEventTypeCache integrationLifecycleEventTypeCache;

    private InternodeNotificationsApplier applier;

    @BeforeEach
    void setUp() {
        applier = new InternodeNotificationsApplier(
                mqttAuthProviderNotificationManager,
                clientSessionStatsCleanupProcessor,
                authorizationRoutingService,
                integrationLifecycleEventTypeCache);
    }

    @Test
    void givenMqttAuthSettings_whenApply_thenForwardedToAuthorizationRoutingService() {
        InternodeNotificationProto proto = InternodeNotificationProto.newBuilder()
                .setMqttAuthSettingsProto(MqttAuthSettingsProto.getDefaultInstance())
                .build();

        applier.apply(SERVICE_ID, proto);

        verify(authorizationRoutingService).onMqttAuthSettingsUpdate(proto.getMqttAuthSettingsProto());
        verifyNoMoreInteractions(authorizationRoutingService);
        verifyNoInteractions(mqttAuthProviderNotificationManager, clientSessionStatsCleanupProcessor, integrationLifecycleEventTypeCache);
    }

    @Test
    void givenMqttAuthProvider_whenApply_thenForwardedToProviderNotificationManager() {
        InternodeNotificationProto proto = InternodeNotificationProto.newBuilder()
                .setMqttAuthProviderProto(MqttAuthProviderProto.getDefaultInstance())
                .build();

        applier.apply(SERVICE_ID, proto);

        verify(mqttAuthProviderNotificationManager).handleProviderNotification(proto.getMqttAuthProviderProto());
        verifyNoMoreInteractions(mqttAuthProviderNotificationManager);
        verifyNoInteractions(authorizationRoutingService, clientSessionStatsCleanupProcessor, integrationLifecycleEventTypeCache);
    }

    @Test
    void givenClientSessionStatsCleanup_whenApply_thenForwardedToStatsCleanupProcessor() {
        InternodeNotificationProto proto = InternodeNotificationProto.newBuilder()
                .setClientSessionStatsCleanupProto(ClientSessionStatsCleanupProto.getDefaultInstance())
                .build();

        applier.apply(SERVICE_ID, proto);

        verify(clientSessionStatsCleanupProcessor).processClientSessionStatsCleanup(proto.getClientSessionStatsCleanupProto());
        verifyNoMoreInteractions(clientSessionStatsCleanupProcessor);
        verifyNoInteractions(authorizationRoutingService, mqttAuthProviderNotificationManager, integrationLifecycleEventTypeCache);
    }

    @Test
    void givenIntegrationLifecycleConfig_whenApply_thenForwardedToEventTypeCache() {
        IntegrationLifecycleConfigProto configProto = IntegrationLifecycleConfigProto.newBuilder()
                .setIntegrationId("integration-1")
                .addLifecycleEventTypes(ClientLifecycleEventType.CLIENT_CONNECTED.name())
                .build();
        InternodeNotificationProto proto = InternodeNotificationProto.newBuilder()
                .setIntegrationLifecycleConfigProto(configProto)
                .build();

        applier.apply(SERVICE_ID, proto);

        verify(integrationLifecycleEventTypeCache).processIntegrationLifecycleConfig(configProto);
        verifyNoMoreInteractions(integrationLifecycleEventTypeCache);
        verifyNoInteractions(authorizationRoutingService, mqttAuthProviderNotificationManager, clientSessionStatsCleanupProcessor);
    }

    @Test
    void givenDeletedIntegrationLifecycleConfig_whenApply_thenForwardedToEventTypeCache() {
        IntegrationLifecycleConfigProto configProto = IntegrationLifecycleConfigProto.newBuilder()
                .setIntegrationId("integration-1")
                .setDeleted(true)
                .build();
        InternodeNotificationProto proto = InternodeNotificationProto.newBuilder()
                .setIntegrationLifecycleConfigProto(configProto)
                .build();

        applier.apply(SERVICE_ID, proto);

        verify(integrationLifecycleEventTypeCache).processIntegrationLifecycleConfig(configProto);
        verifyNoMoreInteractions(integrationLifecycleEventTypeCache);
        verifyNoInteractions(authorizationRoutingService, mqttAuthProviderNotificationManager, clientSessionStatsCleanupProcessor);
    }

    @Test
    void givenNoFieldSet_whenApply_thenNoHandlerIsCalled() {
        applier.apply(SERVICE_ID, InternodeNotificationProto.getDefaultInstance());

        verifyNoInteractions(authorizationRoutingService, mqttAuthProviderNotificationManager,
                clientSessionStatsCleanupProcessor, integrationLifecycleEventTypeCache);
    }

}
