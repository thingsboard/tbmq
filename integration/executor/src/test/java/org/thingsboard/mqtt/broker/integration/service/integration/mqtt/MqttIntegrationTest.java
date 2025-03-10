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
package org.thingsboard.mqtt.broker.integration.service.integration.mqtt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.mqtt.MqttClient;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.integration.api.IntegrationContext;
import org.thingsboard.mqtt.broker.integration.api.TbIntegrationInitParams;
import org.thingsboard.mqtt.broker.integration.api.callback.IntegrationMsgCallback;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MqttIntegrationTest {

    @Mock
    private MqttClient mockClient;

    @Mock
    private IntegrationContext mockContext;

    @Mock
    private TbIntegrationInitParams mockParams;

    @Mock
    private IntegrationMsgCallback mockCallback;

    @InjectMocks
    private MqttIntegration mqttIntegration;

    private MqttIntegrationConfig config;

    @BeforeEach
    void setUp() {
        config = new MqttIntegrationConfig();
        config.setHost("localhost");
        config.setPort(1883);
        config.setTopicName("test/topic");
        config.setClientId("testClient");
        config.setConnectTimeoutSec(10);
        config.setReconnectPeriodSec(1);
        config.setMqttVersion(3);
        config.setQos(1);
        config.setRetained(true);
        config.setKeepAliveSec(60);
    }

    @Test
    void testDoValidateConfiguration_InvalidConfig() {
        config.setTopicName("test/topic/#");
        assertThrows(ThingsboardException.class, () -> mqttIntegration.doValidateConfiguration(JacksonUtil.valueToTree(config), true));
    }

    @Test
    void testDoCheckConnection_Failure() {
        Integration integration = new Integration();
        assertThrows(ThingsboardException.class, () -> mqttIntegration.doCheckConnection(integration, mockContext));
    }

    @Test
    void testDoStopClient() {
        mqttIntegration.doStopClient();
        verify(mockClient, times(1)).disconnect();
    }

}
