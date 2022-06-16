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

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.dto.ShortMqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.common.util.MqttClientCredentialsUtil;
import org.thingsboard.mqtt.broker.dao.service.AbstractServiceTest.IdComparator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
public abstract class BaseMqttClientCredentialsControllerTest extends AbstractControllerTest {

    private final IdComparator<ShortMqttClientCredentials> idComparator = new IdComparator<>();

    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();
    }

    @After
    public void afterTest() throws Exception {
        loginSysAdmin();

        PageData<MqttClientCredentials> pageData = doGetTypedWithPageLink("/api/mqtt/client/credentials?",
                new TypeReference<>() {
                }, new PageLink(10_000));
        List<MqttClientCredentials> loadedMqttClientCredentialsList = new ArrayList<>(pageData.getData());
        for (MqttClientCredentials mqttClientCredentials : loadedMqttClientCredentialsList) {
            doDelete("/api/mqtt/client/credentials/" + mqttClientCredentials.getId()).andExpect(status().isOk());
        }
    }

    @Test
    public void saveMqttClientCredentialsTest() throws Exception {
        BasicMqttCredentials basicMqttCredentials = newBasicMqttCredentials(null);
        MqttClientCredentials mqttClientCredentials = newMqttClientCredentials(ClientCredentialsType.MQTT_BASIC, basicMqttCredentials);

        MqttClientCredentials savedMqttCredentials = doPost("/api/mqtt/client/credentials", mqttClientCredentials, MqttClientCredentials.class);

        Assert.assertNotNull(savedMqttCredentials);
        Assert.assertNotNull(savedMqttCredentials.getId());
        Assert.assertTrue(savedMqttCredentials.getCreatedTime() > 0);
        Assert.assertNotNull(savedMqttCredentials.getCredentialsId());
        Assert.assertEquals(ClientType.DEVICE, savedMqttCredentials.getClientType());

        MqttClientCredentials foundMqttCredentials = doGet("/api/mqtt/client/credentials/" + savedMqttCredentials.getId().toString(), MqttClientCredentials.class);
        Assert.assertEquals(foundMqttCredentials.getName(), savedMqttCredentials.getName());
        Assert.assertEquals(foundMqttCredentials.getCredentialsId(), savedMqttCredentials.getCredentialsId());
    }

    @Test
    public void saveMqttClientCredentialsWithNullNameTest() throws Exception {
        BasicMqttCredentials basicMqttCredentials = newBasicMqttCredentials(null);
        MqttClientCredentials mqttClientCredentials = newMqttClientCredentials(ClientCredentialsType.MQTT_BASIC, basicMqttCredentials, null);

        doPost("/api/mqtt/client/credentials", mqttClientCredentials).andExpect(status().isBadRequest());
    }

    @Test
    public void saveMqttClientCredentialsWithNullCredentialsValueTest() throws Exception {
        MqttClientCredentials mqttClientCredentials = newMqttClientCredentials(ClientCredentialsType.MQTT_BASIC, null);

        doPost("/api/mqtt/client/credentials", mqttClientCredentials).andExpect(status().isBadRequest());
    }

    @Test
    public void saveMqttClientCredentialsWithNullCredentialsTypeTest() throws Exception {
        BasicMqttCredentials basicMqttCredentials = newBasicMqttCredentials(null);
        MqttClientCredentials mqttClientCredentials = newMqttClientCredentials(null, basicMqttCredentials);

        doPost("/api/mqtt/client/credentials", mqttClientCredentials).andExpect(status().isBadRequest());
    }

    @Test
    public void saveMqttClientCredentialsWithInvalidAuthRulePatternsTest() throws Exception {
        BasicMqttCredentials basicMqttCredentials = newBasicMqttCredentials(Collections.singletonList("["));
        MqttClientCredentials mqttClientCredentials = newMqttClientCredentials(ClientCredentialsType.MQTT_BASIC, basicMqttCredentials);

        doPost("/api/mqtt/client/credentials", mqttClientCredentials).andExpect(status().isBadRequest());
    }

    @Test
    public void getMqttClientCredentialsTest() throws Exception {
        List<ShortMqttClientCredentials> mqttClientCredentialsList = new ArrayList<>();
        for (int i = 0; i < 178; i++) {
            BasicMqttCredentials basicMqttCredentials = newBasicMqttCredentials(
                    "clientId" + i,
                    "userName" + i,
                    "pass" + i,
                    null);
            MqttClientCredentials mqttClientCredentials = newMqttClientCredentials(ClientCredentialsType.MQTT_BASIC, basicMqttCredentials, "name" + i);
            MqttClientCredentials savedMqttCredentials = doPost("/api/mqtt/client/credentials", mqttClientCredentials, MqttClientCredentials.class);
            mqttClientCredentialsList.add(MqttClientCredentialsUtil.toShortMqttClientCredentials(savedMqttCredentials));
        }
        List<ShortMqttClientCredentials> loadedMqttClientCredentialsList = new ArrayList<>();
        PageLink pageLink = new PageLink(23);
        PageData<ShortMqttClientCredentials> pageData;
        do {
            pageData = doGetTypedWithPageLink("/api/mqtt/client/credentials?",
                    new TypeReference<>() {
                    }, pageLink);

            loadedMqttClientCredentialsList.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        mqttClientCredentialsList.sort(idComparator);
        loadedMqttClientCredentialsList.sort(idComparator);

        Assert.assertEquals(mqttClientCredentialsList, loadedMqttClientCredentialsList);
    }

    @Test
    public void deleteCredentialsTest() throws Exception {
        BasicMqttCredentials basicMqttCredentials = newBasicMqttCredentials(null);
        MqttClientCredentials mqttClientCredentials = newMqttClientCredentials(ClientCredentialsType.MQTT_BASIC, basicMqttCredentials);

        MqttClientCredentials savedMqttClientCredentials = doPost("/api/mqtt/client/credentials", mqttClientCredentials, MqttClientCredentials.class);
        Assert.assertNotNull(savedMqttClientCredentials);

        doDelete("/api/mqtt/client/credentials/" + savedMqttClientCredentials.getId())
                .andExpect(status().isOk());
        doDelete("/api/mqtt/client/credentials/" + savedMqttClientCredentials.getId())
                .andExpect(status().is5xxServerError());
    }

    private MqttClientCredentials newMqttClientCredentials(ClientCredentialsType credentialsType, BasicMqttCredentials basicMqttCredentials) {
        return newMqttClientCredentials(credentialsType, basicMqttCredentials, "name");
    }

    private MqttClientCredentials newMqttClientCredentials(ClientCredentialsType credentialsType, BasicMqttCredentials basicMqttCredentials, String name) {
        MqttClientCredentials mqttClientCredentials = new MqttClientCredentials();
        mqttClientCredentials.setCredentialsType(credentialsType);
        mqttClientCredentials.setCredentialsValue(JacksonUtil.toString(basicMqttCredentials));
        mqttClientCredentials.setName(name);
        return mqttClientCredentials;
    }

    private BasicMqttCredentials newBasicMqttCredentials(List<String> authorizationRulePatterns) {
        return newBasicMqttCredentials("clientId", "username", "password", authorizationRulePatterns);
    }

    private BasicMqttCredentials newBasicMqttCredentials(String clientId, String username, String password, List<String> authorizationRulePatterns) {
        return new BasicMqttCredentials(clientId, username, password, authorizationRulePatterns);
    }
}
