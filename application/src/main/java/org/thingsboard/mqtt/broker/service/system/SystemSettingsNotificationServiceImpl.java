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
package org.thingsboard.mqtt.broker.service.system;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.service.install.data.MqttAuthSettings;
import org.thingsboard.mqtt.broker.service.notification.InternodeNotificationsService;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingsNotificationServiceImpl implements SystemSettingsNotificationService {

    private final InternodeNotificationsService internodeNotificationsService;

    @Override
    public void onMqttAuthSettingUpdate(MqttAuthSettings mqttAuthSettings) {
        internodeNotificationsService.broadcast(ProtoConverter.toMqttAuthSettingUpdateProto(mqttAuthSettings));
    }

}
