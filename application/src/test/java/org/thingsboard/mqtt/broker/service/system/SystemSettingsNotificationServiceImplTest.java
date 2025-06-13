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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.service.install.data.MqttAuthSettings;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.service.notification.InternodeNotificationsService;

import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class SystemSettingsNotificationServiceImplTest {

    @Mock
    private InternodeNotificationsService internodeNotificationsService;

    private SystemSettingsNotificationServiceImpl service;

    @Before
    public void setUp() {
        service = new SystemSettingsNotificationServiceImpl(internodeNotificationsService);
    }

    @Test
    public void testOnMqttAuthSettingUpdate_shouldBroadcastConvertedProto() {
        MqttAuthSettings settings = new MqttAuthSettings();
        InternodeNotificationProto mqttAuthSettingUpdateProto = ProtoConverter.toMqttAuthSettingUpdateProto(settings);

        service.onMqttAuthSettingUpdate(settings);

        verify(internodeNotificationsService).broadcast(mqttAuthSettingUpdateProto);
    }
}
