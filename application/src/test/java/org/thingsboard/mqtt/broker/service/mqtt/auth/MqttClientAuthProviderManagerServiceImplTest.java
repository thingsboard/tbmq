/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.auth;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.basic.BasicMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.service.notification.InternodeNotificationsService;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MqttClientAuthProviderManagerServiceImplTest {

    private final UUID PROVIDER_UUID = UUID.fromString("d24777b7-b490-4f86-bfaa-e052997c1e4d");

    @Mock
    private MqttAuthProviderService providerService;

    @Mock
    private InternodeNotificationsService internodeNotificationsService;

    private MqttAuthProviderManagerServiceImpl service;

    @Before
    public void setUp() {
        service = new MqttAuthProviderManagerServiceImpl(providerService, internodeNotificationsService);
    }

    @Test
    public void testSaveAuthProvider_shouldBroadcastUpdateNotification() {
        MqttAuthProvider authProviderMock = mock(MqttAuthProvider.class);
        MqttAuthProvider savedProvider = new MqttAuthProvider();
        savedProvider.setType(MqttAuthProviderType.BASIC);
        savedProvider.setConfiguration(new BasicMqttAuthProviderConfiguration());
        InternodeNotificationProto expectedProto = ProtoConverter.toMqttAuthProviderUpdatedEvent(savedProvider);

        when(providerService.saveAuthProvider(authProviderMock)).thenReturn(savedProvider);

        service.saveAuthProvider(authProviderMock);

        verify(internodeNotificationsService).broadcast(expectedProto);
    }

    @Test
    public void testEnableAuthProvider_shouldBroadcastEnableNotificationWhenPresent() {
        InternodeNotificationProto expectedProto = ProtoConverter.toMqttAuthProviderEnabledEvent(MqttAuthProviderType.JWT);

        when(providerService.enableAuthProvider(PROVIDER_UUID)).thenReturn(Optional.of(MqttAuthProviderType.JWT));

        service.enableAuthProvider(PROVIDER_UUID);

        verify(internodeNotificationsService).broadcast(expectedProto);
    }

    @Test
    public void testEnableAuthProvider_shouldDoNothingWhenNotPresent() {
        when(providerService.enableAuthProvider(PROVIDER_UUID)).thenReturn(Optional.empty());

        service.enableAuthProvider(PROVIDER_UUID);

        verifyNoInteractions(internodeNotificationsService);
    }

    @Test
    public void testDisableAuthProvider_shouldBroadcastDisableNotificationWhenPresent() {
        InternodeNotificationProto expectedProto = ProtoConverter.toMqttAuthProviderDisabledEvent(MqttAuthProviderType.X_509);

        when(providerService.disableAuthProvider(PROVIDER_UUID)).thenReturn(Optional.of(MqttAuthProviderType.X_509));

        service.disableAuthProvider(PROVIDER_UUID);

        verify(internodeNotificationsService).broadcast(expectedProto);
    }

    @Test
    public void testDisableAuthProvider_shouldDoNothingWhenNotPresent() {
        when(providerService.disableAuthProvider(PROVIDER_UUID)).thenReturn(Optional.empty());

        service.disableAuthProvider(PROVIDER_UUID);

        verifyNoInteractions(internodeNotificationsService);
    }
}