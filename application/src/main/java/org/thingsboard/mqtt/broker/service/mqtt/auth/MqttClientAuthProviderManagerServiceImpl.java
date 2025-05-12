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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientAuthProviderDto;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttClientAuthProviderService;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.service.notification.InternodeNotificationsService;

import java.util.UUID;

// TODO: should we send notification if provider not deleted/enabled/disabled?
@Service
@RequiredArgsConstructor
public class MqttClientAuthProviderManagerServiceImpl implements MqttClientAuthProviderManagerService {

    private final MqttClientAuthProviderService providerService;
    private final InternodeNotificationsService internodeNotificationsService;

    @Override
    public MqttClientAuthProviderDto saveAuthProvider(MqttClientAuthProviderDto authProvider) {
        MqttClientAuthProviderDto saved = providerService.saveAuthProvider(authProvider);
        InternodeNotificationProto notificationProto = authProvider.getId() == null ?
                ProtoConverter.toMqttAuthProviderCreatedEvent(saved) :
                ProtoConverter.toMqttAuthProviderUpdatedEvent(saved);
        internodeNotificationsService.broadcast(notificationProto);
        return saved;
    }

    @Override
    public void deleteAuthProvider(UUID id) {
        boolean deleted = providerService.deleteAuthProvider(id);
        if (deleted) {
            InternodeNotificationProto notificationProto = ProtoConverter.toMqttAuthProviderDeletedEvent(id);
            internodeNotificationsService.broadcast(notificationProto);
        }
    }

    @Override
    public void enableAuthProvider(UUID id) {
        boolean enabled = providerService.enableAuthProvider(id);
        if (enabled) {
            InternodeNotificationProto notificationProto = ProtoConverter.toMqttAuthProviderEnabledEvent(id);
            internodeNotificationsService.broadcast(notificationProto);
        }
    }

    @Override
    public void disableAuthProvider(UUID id) {
        boolean disabled = providerService.disableAuthProvider(id);
        if (disabled) {
            InternodeNotificationProto notificationProto = ProtoConverter.toMqttAuthProviderDisabledEvent(id);
            internodeNotificationsService.broadcast(notificationProto);
        }
    }

}
