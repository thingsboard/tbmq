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
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.service.notification.InternodeNotificationsService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MqttAuthProviderManagerServiceImpl implements MqttAuthProviderManagerService {

    private final MqttAuthProviderService providerService;
    private final InternodeNotificationsService internodeNotificationsService;

    @Override
    public MqttAuthProvider saveAuthProvider(MqttAuthProvider authProvider) {
        MqttAuthProvider saved = providerService.saveAuthProvider(authProvider);
        InternodeNotificationProto notificationProto = ProtoConverter.toMqttAuthProviderUpdatedEvent(saved);
        internodeNotificationsService.broadcast(notificationProto);
        return saved;
    }

    @Override
    public void enableAuthProvider(UUID id) {
        providerService.enableAuthProvider(id).ifPresent(type -> {
            InternodeNotificationProto notificationProto = ProtoConverter.toMqttAuthProviderEnabledEvent(type);
            internodeNotificationsService.broadcast(notificationProto);
        });
    }

    @Override
    public void disableAuthProvider(UUID id) {
        providerService.disableAuthProvider(id).ifPresent(type -> {
            InternodeNotificationProto notificationProto = ProtoConverter.toMqttAuthProviderDisabledEvent(type);
            internodeNotificationsService.broadcast(notificationProto);
        });
    }

}
