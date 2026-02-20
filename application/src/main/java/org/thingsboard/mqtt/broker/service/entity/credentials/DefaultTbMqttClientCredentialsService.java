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
package org.thingsboard.mqtt.broker.service.entity.credentials;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.entity.AbstractTbEntityService;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTbMqttClientCredentialsService extends AbstractTbEntityService implements TbMqttClientCredentialsService {

    private final MqttClientCredentialsService mqttClientCredentialsService;

    @Override
    public MqttClientCredentials save(MqttClientCredentials credentials, User currentUser) {
        return mqttClientCredentialsService.saveCredentials(credentials);
    }

    @Override
    public void delete(MqttClientCredentials credentials, User currentUser) {
        mqttClientCredentialsService.deleteCredentials(credentials.getId());
    }

}
