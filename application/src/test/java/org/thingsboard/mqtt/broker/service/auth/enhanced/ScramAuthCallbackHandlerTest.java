/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.auth.enhanced;

import org.apache.kafka.common.security.scram.ScramCredential;
import org.apache.kafka.common.security.scram.ScramCredentialCallback;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ScramAlgorithm;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ScramMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.util.protocol.ProtocolUtil;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.thingsboard.mqtt.broker.common.data.client.credentials.ScramMqttCredentials.ITERATIONS_COUNT;

@RunWith(MockitoJUnitRunner.class)
public class ScramAuthCallbackHandlerTest {

    private ScramAuthCallbackHandler callbackHandler;
    private MqttClientCredentialsService credentialsServiceMock;
    private AuthorizationRuleService authorizationRuleServiceMock;

    @Before
    public void setup() {
        credentialsServiceMock = mock(MqttClientCredentialsService.class);
        authorizationRuleServiceMock = mock(AuthorizationRuleService.class);
        callbackHandler = new ScramAuthCallbackHandler(credentialsServiceMock, authorizationRuleServiceMock);
    }

    @Test
    public void givenMissingNameCallback_whenHandle_thenThrowException() {
        ScramCredentialCallback scramCredentialCallback = new ScramCredentialCallback();
        Callback[] callbacks = new Callback[]{scramCredentialCallback};

        assertThatThrownBy(() -> callbackHandler.handle(callbacks))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to process SCRAM enhanced authentication due to missing NameCallback!");
    }

    @Test
    public void givenNameCallbackWithMissingDefaultName_whenHandle_thenThrowException() {
        NameCallback nameCallback = new NameCallback("prompt");
        ScramCredentialCallback scramCredentialCallback = new ScramCredentialCallback();
        Callback[] callbacks = new Callback[]{nameCallback, scramCredentialCallback};

        assertThatThrownBy(() -> callbackHandler.handle(callbacks))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to process SCRAM enhanced authentication due to missing username!");
    }

    @Test
    public void givenMissingScramCredentialCallback_whenHandle_thenThrowException() {
        NameCallback nameCallback = new NameCallback("prompt", "username");
        Callback[] callbacks = new Callback[]{nameCallback};

        assertThatThrownBy(() -> callbackHandler.handle(callbacks))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to process SCRAM enhanced authentication due to missing ScramCredentialCallback!");
    }

    @Test
    public void givenMissingMqttClientCredentialsForGivenUsername_whenHandle_thenThrowException() {
        String username = "username";
        NameCallback nameCallback = new NameCallback("prompt", username);
        ScramCredentialCallback scramCredentialCallback = new ScramCredentialCallback();
        Callback[] callbacks = new Callback[]{nameCallback, scramCredentialCallback};

        when(credentialsServiceMock.findMatchingCredentials(any())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> callbackHandler.handle(callbacks))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to find credentials for given credentialsId: " + ProtocolUtil.scramCredentialsId(username));
    }

    @Test
    public void givenNonScramCredentials_whenHandle_thenThrowException() {
        String username = "username";
        NameCallback nameCallback = new NameCallback("prompt", username);
        ScramCredentialCallback scramCredentialCallback = new ScramCredentialCallback();
        Callback[] callbacks = new Callback[]{nameCallback, scramCredentialCallback};

        var basicMqttCredentials = BasicMqttCredentials.newInstance("username");
        var mqttClientCredentials = new MqttClientCredentials();
        mqttClientCredentials.setName("basic mqtt credentials");
        mqttClientCredentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        mqttClientCredentials.setCredentialsValue(JacksonUtil.toString(basicMqttCredentials));

        when(credentialsServiceMock.findMatchingCredentials(any())).thenReturn(List.of(mqttClientCredentials));

        assertThatThrownBy(() -> callbackHandler.handle(callbacks))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to find SCRAM credentials for given credentialsId: " + ProtocolUtil.scramCredentialsId(username));
    }

    @Test
    public void givenScramCredentialsWithInvalidAuthRulePatterns_whenHandle_thenThrowException() throws Exception {
        String username = "username";
        NameCallback nameCallback = new NameCallback("prompt", username);
        ScramCredentialCallback scramCredentialCallback = new ScramCredentialCallback();
        Callback[] callbacks = new Callback[]{nameCallback, scramCredentialCallback};

        var scramMqttCredentials = ScramMqttCredentials
                .newInstance("username", "password", ScramAlgorithm.SHA_256, null);
        var mqttClientCredentials = new MqttClientCredentials();
        mqttClientCredentials.setName("scram mqtt credentials");
        mqttClientCredentials.setCredentialsType(ClientCredentialsType.SCRAM);
        mqttClientCredentials.setCredentialsValue(JacksonUtil.toString(scramMqttCredentials));

        when(credentialsServiceMock.findMatchingCredentials(any())).thenReturn(List.of(mqttClientCredentials));
        when(authorizationRuleServiceMock.parseAuthorizationRule(any())).thenThrow(AuthenticationException.class);

        assertThatThrownBy(() -> callbackHandler.handle(callbacks))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to parse authorization rule for SCRAM credentials: " + ProtocolUtil.scramCredentialsId(username))
                .hasCauseInstanceOf(AuthenticationException.class);
    }

    @Test
    public void givenScramCredentials_whenHandle_thenSetScramCredentialToCallback() throws Exception {
        String username = "username";
        NameCallback nameCallback = new NameCallback("prompt", username);
        ScramCredentialCallback scramCredentialCallbackMock = mock(ScramCredentialCallback.class);
        Callback[] callbacks = new Callback[]{nameCallback, scramCredentialCallbackMock};

        var scramMqttCredentials = ScramMqttCredentials
                .newInstance("username", "password", ScramAlgorithm.SHA_256, null);
        var mqttClientCredentials = new MqttClientCredentials();
        mqttClientCredentials.setName("scram mqtt credentials");
        mqttClientCredentials.setCredentialsType(ClientCredentialsType.SCRAM);
        mqttClientCredentials.setClientType(ClientType.DEVICE);
        mqttClientCredentials.setCredentialsValue(JacksonUtil.toString(scramMqttCredentials));

        when(credentialsServiceMock.findMatchingCredentials(any())).thenReturn(List.of(mqttClientCredentials));
        AuthRulePatterns authRulePatternsMock = mock(AuthRulePatterns.class);
        when(authorizationRuleServiceMock.parseAuthorizationRule(any())).thenReturn(authRulePatternsMock);

        callbackHandler.handle(callbacks);

        var scramCredentialCaptor = ArgumentCaptor.forClass(ScramCredential.class);

        verify(scramCredentialCallbackMock).scramCredential(scramCredentialCaptor.capture());

        ScramCredential scramCredential = scramCredentialCaptor.getValue();

        assertThat(scramCredential.salt()).isEqualTo(scramMqttCredentials.getSalt());
        assertThat(scramCredential.serverKey()).isEqualTo(scramMqttCredentials.getServerKey());
        assertThat(scramCredential.storedKey()).isEqualTo(scramMqttCredentials.getStoredKey());
        assertThat(scramCredential.iterations()).isEqualTo(ITERATIONS_COUNT);

        assertThat(callbackHandler.getClientType()).isEqualTo(ClientType.DEVICE);
        assertThat(callbackHandler.getAuthRulePatterns()).isEqualTo(authRulePatternsMock);
    }

}
