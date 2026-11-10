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
package org.thingsboard.mqtt.broker.service.auth.enhanced;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ScramAlgorithm;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ScramMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Credentials are built by {@link ScramMqttCredentials#newInstance} exactly as TBMQ 2.4.x stores them, then
 * authenticated by Kafka's SCRAM client against {@link TbScramSaslServer} through the real
 * {@link ScramAuthCallbackHandler}.
 */
class ScramStoredCredentialsCompatibilityTest {

    private static final String USERNAME = "tbmq-user";
    private static final String PASSWORD = "secret";

    @ParameterizedTest
    @EnumSource(ScramAlgorithm.class)
    void credentialStoredBy24xAuthenticates(ScramAlgorithm alg) throws Exception {
        ScramAuthCallbackHandler handler = handlerWithStoredCredential(alg);
        TbScramSaslServer server = TbScramSaslServer.create(alg.getMqttAlgorithmName(), handler).orElseThrow();
        SaslClient client = KafkaScramTestClient.create(USERNAME, PASSWORD, alg);

        KafkaScramTestClient.exchange(client, server);

        assertThat(server.isComplete()).isTrue();
        assertThat(client.isComplete()).isTrue();
        assertThat(handler.getClientType()).isEqualTo(ClientType.DEVICE);
        assertThat(handler.getUsername()).isEqualTo(USERNAME);
    }

    @ParameterizedTest
    @EnumSource(ScramAlgorithm.class)
    void wrongPasswordIsRejected(ScramAlgorithm alg) throws Exception {
        ScramAuthCallbackHandler handler = handlerWithStoredCredential(alg);
        TbScramSaslServer server = TbScramSaslServer.create(alg.getMqttAlgorithmName(), handler).orElseThrow();
        SaslClient client = KafkaScramTestClient.create(USERNAME, "wrong", alg);

        byte[] clientFirst = client.evaluateChallenge(new byte[0]);
        byte[] serverFirst = server.evaluateResponse(clientFirst);
        byte[] clientFinal = client.evaluateChallenge(serverFirst);

        assertThatThrownBy(() -> server.evaluateResponse(clientFinal)).isInstanceOf(SaslException.class);
        assertThat(server.isComplete()).isFalse();
    }

    private static ScramAuthCallbackHandler handlerWithStoredCredential(ScramAlgorithm alg) throws Exception {
        var stored = ScramMqttCredentials.newInstance(USERNAME, PASSWORD, alg, List.of(".*"));
        var credentials = new MqttClientCredentials();
        credentials.setName("scram credentials");
        credentials.setCredentialsType(ClientCredentialsType.SCRAM);
        credentials.setClientType(ClientType.DEVICE);
        credentials.setCredentialsValue(JacksonUtil.toString(stored));

        var credentialsService = mock(MqttClientCredentialsService.class);
        when(credentialsService.findMatchingCredentials(List.of("scram|" + USERNAME))).thenReturn(List.of(credentials));
        var ruleService = mock(AuthorizationRuleService.class);
        when(ruleService.parseAuthorizationRule(any())).thenReturn(mock(AuthRulePatterns.class));
        return new ScramAuthCallbackHandler(credentialsService, ruleService);
    }

}
