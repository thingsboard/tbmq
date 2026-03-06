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
package org.thingsboard.mqtt.broker.service.entity.mqttauth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.service.entity.AbstractTbEntityService;
import org.thingsboard.mqtt.broker.service.mqtt.auth.MqttAuthProviderManagerService;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTbMqttAuthProviderService extends AbstractTbEntityService implements TbMqttAuthProviderService {

    private final MqttAuthProviderManagerService mqttAuthProviderManagerService;

    @Override
    public MqttAuthProvider save(MqttAuthProvider authProvider, User currentUser) {
        return mqttAuthProviderManagerService.saveAuthProvider(authProvider);
    }

    @Override
    public void enable(MqttAuthProvider authProvider, User currentUser) {
        mqttAuthProviderManagerService.enableAuthProvider(authProvider.getId());
    }

    @Override
    public void disable(MqttAuthProvider authProvider, User currentUser) {
        mqttAuthProviderManagerService.disableAuthProvider(authProvider.getId());
    }

}
