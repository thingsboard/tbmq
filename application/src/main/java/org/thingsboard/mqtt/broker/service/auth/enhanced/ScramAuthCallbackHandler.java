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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.security.scram.ScramCredential;
import org.apache.kafka.common.security.scram.ScramCredentialCallback;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ScramMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.MqttClientCredentialsUtil;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.util.protocol.ProtocolUtil;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.util.List;

import static org.thingsboard.mqtt.broker.common.data.client.credentials.ScramMqttCredentials.ITERATIONS_COUNT;

@Slf4j
public class ScramAuthCallbackHandler implements CallbackHandler {

    private final MqttClientCredentialsService credentialsService;
    private final AuthorizationRuleService authorizationRuleService;

    private String username;
    @Getter
    private ClientType clientType;
    @Getter
    private AuthRulePatterns authRulePatterns;

    public ScramAuthCallbackHandler(MqttClientCredentialsService credentialsService, AuthorizationRuleService authorizationRuleService) {
        this.credentialsService = credentialsService;
        this.authorizationRuleService = authorizationRuleService;
    }

    @Override
    public void handle(Callback[] callbacks) throws UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            if (callback instanceof NameCallback nameCallback) {
                username = nameCallback.getDefaultName();
                if (username.isEmpty()) {
                    throw new RuntimeException("Failed to process SCRAM enhanced authentication due to missing username!");
                }
            } else if (callback instanceof ScramCredentialCallback scramCallback) {
                String credentialsId = ProtocolUtil.usernameCredentialsId(username);
                List<MqttClientCredentials> matchingCredentials = credentialsService.findMatchingCredentials(List.of(credentialsId));
                if (matchingCredentials.isEmpty()) {
                    throw new RuntimeException("Failed to find credentials for given username: " + username);
                }
                MqttClientCredentials credentials = matchingCredentials.get(0);
                if (!ClientCredentialsType.SCRAM.equals(credentials.getCredentialsType())) {
                    throw new RuntimeException("Failed to find SCRAM credentials for given username: " + username);
                }
                clientType = credentials.getClientType();
                var scramMqttCredentials = MqttClientCredentialsUtil.getMqttCredentials(credentials, ScramMqttCredentials.class);
                try {
                    authRulePatterns = authorizationRuleService.parseAuthorizationRule(scramMqttCredentials);
                } catch (AuthenticationException e) {
                    throw new RuntimeException("Failed to parse authorization rule for SCRAM credentials: " + credentialsId, e);
                }
                var scramCredential = new ScramCredential(scramMqttCredentials.getSalt(),
                        scramMqttCredentials.getStoredKey(), scramMqttCredentials.getServerKey(), ITERATIONS_COUNT);
                scramCallback.scramCredential(scramCredential);
            } else {
                throw new UnsupportedCallbackException(callback);
            }
        }
    }

}
