package org.thingsboard.mqtt.broker.dao.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.dao.util.protocol.ProtocolUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@DaoSqlTest
public class MqttClientCredentialsServiceTest extends AbstractServiceTest {
    @Autowired
    private MqttClientCredentialsService mqttClientCredentialsService;

    @Test(expected = DataValidationException.class)
    public void testCreateDeviceCredentialsWithEmptyCredentialsType() {
        MqttClientCredentials clientCredentials = new MqttClientCredentials();
        clientCredentials.setClientId("TestClient");
        mqttClientCredentialsService.saveCredentials(clientCredentials);
    }

    @Test(expected = DataValidationException.class)
    public void testCreateNoClientAndUsername() {
        MqttClientCredentials clientCredentials = new MqttClientCredentials();
        clientCredentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        mqttClientCredentialsService.saveCredentials(clientCredentials);
    }

    @Test(expected = DataValidationException.class)
    public void testCreateNotValidCredentialsValue() {
        MqttClientCredentials clientCredentials = new MqttClientCredentials();
        clientCredentials.setClientId("TestClient");
        clientCredentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        clientCredentials.setCredentialsValue("NOT_VALID");
        mqttClientCredentialsService.saveCredentials(clientCredentials);
    }


    @Test(expected = DataValidationException.class)
    public void testCreateDuplicateCredentials() throws JsonProcessingException {
        MqttClientCredentials clientCredentials = null;
        try {
            clientCredentials = mqttClientCredentialsService.saveCredentials(validMqttClientCredentials("client", "user", null));
            mqttClientCredentialsService.saveCredentials(validMqttClientCredentials("client", "user", "password"));
        } finally {
            if (clientCredentials != null) {
                mqttClientCredentialsService.deleteCredentials(clientCredentials.getId());
            }
        }
    }

    @Test
    public void testFindMatchingMixed() throws JsonProcessingException {
        MqttClientCredentials client1Credentials = null, client2Credentials = null, client3Credentials = null;
        try {
            client1Credentials = mqttClientCredentialsService.saveCredentials(
                    validMqttClientCredentials("client1", "test1", "password1"));
            client2Credentials = mqttClientCredentialsService.saveCredentials(
                    validMqttClientCredentials("client2", "test1", null));
            client3Credentials = mqttClientCredentialsService.saveCredentials(
                    validMqttClientCredentials("client1", "test2", null));

            Assert.assertEquals(
                    Collections.singletonList(client1Credentials),
                    mqttClientCredentialsService.findMatchingCredentials(Collections.singletonList(
                            ProtocolUtil.mixedCredentialsId("test1", "client1")
                    )));
        } finally {
            if (client1Credentials != null) {
                mqttClientCredentialsService.deleteCredentials(client1Credentials.getId());
            }
            if (client2Credentials != null) {
                mqttClientCredentialsService.deleteCredentials(client2Credentials.getId());
            }
            if (client3Credentials != null) {
                mqttClientCredentialsService.deleteCredentials(client3Credentials.getId());
            }
        }
    }

    @Test
    public void testFindMatchingByUserName() throws JsonProcessingException {
        MqttClientCredentials client1Credentials = null, client2Credentials = null;
        try {
            client1Credentials = mqttClientCredentialsService.saveCredentials(
                    validMqttClientCredentials(null, "user1", null));
            client2Credentials = mqttClientCredentialsService.saveCredentials(
                    validMqttClientCredentials(null, "user2", null));

            Assert.assertEquals(
                    Collections.singletonList(client1Credentials),
                    mqttClientCredentialsService.findMatchingCredentials(Collections.singletonList(
                            ProtocolUtil.usernameCredentialsId("user1")
                    )));
        } finally {
            if (client1Credentials != null) {
                mqttClientCredentialsService.deleteCredentials(client1Credentials.getId());
            }
            if (client2Credentials != null) {
                mqttClientCredentialsService.deleteCredentials(client2Credentials.getId());
            }
        }
    }

    @Test
    public void testFindMatchingByClientId() throws JsonProcessingException {
        MqttClientCredentials client1Credentials = null, client2Credentials = null;
        try {
            client1Credentials = mqttClientCredentialsService.saveCredentials(
                    validMqttClientCredentials("client1", null, null));
            client2Credentials = mqttClientCredentialsService.saveCredentials(
                    validMqttClientCredentials("client2", null, null));

            Assert.assertEquals(
                    Collections.singletonList(client1Credentials),
                    mqttClientCredentialsService.findMatchingCredentials(Collections.singletonList(
                            ProtocolUtil.clientIdCredentialsId("client1")
                    )));
        } finally {
            if (client1Credentials != null) {
                mqttClientCredentialsService.deleteCredentials(client1Credentials.getId());
            }
            if (client2Credentials != null) {
                mqttClientCredentialsService.deleteCredentials(client2Credentials.getId());
            }
        }
    }

    private MqttClientCredentials validMqttClientCredentials(String clientId, String username, String password) throws JsonProcessingException {
        MqttClientCredentials clientCredentials = new MqttClientCredentials();
        clientCredentials.setClientId(clientId);
        clientCredentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        BasicMqttCredentials basicMqttCredentials = new BasicMqttCredentials(username, password);
        clientCredentials.setCredentialsValue(mapper.writeValueAsString(basicMqttCredentials));
        return clientCredentials;
    }

    private String getUserName(MqttClientCredentials mqttClientCredentials) throws JsonProcessingException {
        return mapper.readValue(mqttClientCredentials.getCredentialsValue(), BasicMqttCredentials.class).getUserName();
    }
}
