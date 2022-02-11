/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.controller;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;

import java.util.Collections;
import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
// TODO: 11.02.22 implement real tests
public abstract class BaseMqttClientCredentialsControllerTest extends AbstractControllerTest {

    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();
    }

    @After
    public void afterTest() throws Exception {
        loginSysAdmin();
    }

    @Test
    public void saveMqttClientCredentialsTest() throws Exception {
        BasicMqttCredentials basicMqttCredentials = getBasicMqttCredentials(null);
        MqttClientCredentials mqttClientCredentials = getMqttClientCredentials(ClientCredentialsType.MQTT_BASIC, basicMqttCredentials);

        MqttClientCredentials savedMqttClientCredentials = doPost("/api/mqtt/client/credentials", mqttClientCredentials, MqttClientCredentials.class);

        Assert.assertNotNull(savedMqttClientCredentials);
        Assert.assertNotNull(savedMqttClientCredentials.getId());
        Assert.assertTrue(savedMqttClientCredentials.getCreatedTime() > 0);
        Assert.assertNotNull(savedMqttClientCredentials.getCredentialsId());
    }

    @Test
    public void saveMqttClientCredentialsWithNullCredentialsTypeTest() throws Exception {
        BasicMqttCredentials basicMqttCredentials = getBasicMqttCredentials(null);
        MqttClientCredentials mqttClientCredentials = getMqttClientCredentials(null, basicMqttCredentials);

        doPost("/api/mqtt/client/credentials", mqttClientCredentials).andExpect(status().isBadRequest());
    }

    @Test
    public void saveMqttClientCredentialsWithInvalidAuthRulePatternsTest() throws Exception {
        BasicMqttCredentials basicMqttCredentials = getBasicMqttCredentials(Collections.singletonList("not_closed"));
        MqttClientCredentials mqttClientCredentials = getMqttClientCredentials(ClientCredentialsType.MQTT_BASIC, basicMqttCredentials);

        doPost("/api/mqtt/client/credentials", mqttClientCredentials).andExpect(status().isBadRequest());
    }

    @Test
    public void getMqttClientCredentialsTest() {
        Assert.assertEquals(1, 1);
    }

    @Test
    public void deleteCredentialsTest() {
        Assert.assertEquals(1, 1);
    }

    private MqttClientCredentials getMqttClientCredentials(ClientCredentialsType mqttBasic, BasicMqttCredentials basicMqttCredentials) {
        MqttClientCredentials mqttClientCredentials = new MqttClientCredentials();
        mqttClientCredentials.setCredentialsType(mqttBasic);
        mqttClientCredentials.setCredentialsValue(JacksonUtil.toString(basicMqttCredentials));
        mqttClientCredentials.setName("name");
        return mqttClientCredentials;
    }

    private BasicMqttCredentials getBasicMqttCredentials(List<String> authorizationRulePatterns) {
        return new BasicMqttCredentials("clientId", "username", "password", authorizationRulePatterns);
    }
}
