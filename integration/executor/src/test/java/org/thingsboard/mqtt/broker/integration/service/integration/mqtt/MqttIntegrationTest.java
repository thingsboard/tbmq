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
package org.thingsboard.mqtt.broker.integration.service.integration.mqtt;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.MqttClient;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationLifecycleMsg;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.integration.api.IntegrationContext;
import org.thingsboard.mqtt.broker.integration.api.TbIntegrationInitParams;
import org.thingsboard.mqtt.broker.integration.api.callback.IntegrationMsgCallback;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void testValidateConfiguration_eventsEnabledWithEmptyEventsTopic_throws() {
        config.setEventsTopicName("");
        IntegrationLifecycleMsg msg = lifecycleMsgWithEvents(config, "CLIENT_CONNECTED");

        assertThrows(ThingsboardException.class, () -> mqttIntegration.validateConfiguration(msg, true));
    }

    @Test
    void testValidateConfiguration_eventsOptedInViaNonArray_throwsOnEmptyEventsTopic() {
        config.setEventsTopicName("");
        ObjectNode configuration = JacksonUtil.newObjectNode();
        configuration.set("clientConfiguration", JacksonUtil.valueToTree(config));
        // a non-empty, non-array lifecycleEventTypes value: validation must still treat events as opted in,
        // matching the runtime IntegrationEventsOptInUtil.isOptedIn predicate (present + non-empty)
        configuration.putObject("lifecycleEventTypes").put("CLIENT_CONNECTED", true);
        IntegrationLifecycleMsg msg = IntegrationLifecycleMsg.builder().configuration(configuration).build();

        assertThrows(ThingsboardException.class, () -> mqttIntegration.validateConfiguration(msg, true));
    }

    @Test
    void testValidateConfiguration_eventsEnabledWithValidEventsTopic_doesNotThrow() {
        config.setEventsTopicName("tbmq/events");
        IntegrationLifecycleMsg msg = lifecycleMsgWithEvents(config, "CLIENT_CONNECTED");

        assertDoesNotThrow(() -> mqttIntegration.validateConfiguration(msg, true));
    }

    @Test
    void testValidateConfiguration_noEventsWithEmptyEventsTopic_doesNotThrow() {
        config.setEventsTopicName("");
        IntegrationLifecycleMsg msg = lifecycleMsgWithEvents(config); // no event types

        assertDoesNotThrow(() -> mqttIntegration.validateConfiguration(msg, true));
    }

    private static IntegrationLifecycleMsg lifecycleMsgWithEvents(MqttIntegrationConfig config, String... eventTypes) {
        ObjectNode configuration = JacksonUtil.newObjectNode();
        configuration.set("clientConfiguration", JacksonUtil.valueToTree(config));
        ArrayNode types = configuration.putArray("lifecycleEventTypes");
        for (String type : eventTypes) {
            types.add(type);
        }
        return IntegrationLifecycleMsg.builder().configuration(configuration).build();
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

    @Test
    @SuppressWarnings("unchecked")
    void testDoProcessLifecycleEvent_publishesToEventsTopicWithQos1AndNoRetain() {
        config.setEventsTopicName("tbmq/events");
        ReflectionTestUtils.setField(mqttIntegration, "config", config);
        Future<Void> future = mock(Future.class);
        when(mockClient.publish(anyString(), any(ByteBuf.class), any(MqttQoS.class), anyBoolean())).thenReturn(future);

        mqttIntegration.doProcessLifecycleEvent(JacksonUtil.newObjectNode(), mockCallback);

        verify(mockClient).publish(eq("tbmq/events"), any(ByteBuf.class), eq(MqttQoS.AT_LEAST_ONCE), eq(false));
    }

}
