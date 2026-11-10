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

import org.apache.kafka.common.security.scram.internals.ScramSaslClientProvider;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ScramAlgorithm;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import static org.thingsboard.mqtt.broker.service.auth.DefaultEnhancedAuthenticationService.SCRAM_SASL_PROPS;
import static org.thingsboard.mqtt.broker.service.auth.DefaultEnhancedAuthenticationService.SCRAM_SASL_PROTOCOL;

/**
 * Test helper that drives Kafka's SCRAM SASL client, used as an independent reference implementation
 * against {@link TbScramSaslServer}.
 */
final class KafkaScramTestClient {

    private KafkaScramTestClient() {
    }

    static SaslClient create(String username, String password, ScramAlgorithm algorithm) throws SaslException {
        ScramSaslClientProvider.initialize();
        return Sasl.createSaslClient(
                new String[]{algorithm.getMqttAlgorithmName()},
                null,
                SCRAM_SASL_PROTOCOL,
                null,
                SCRAM_SASL_PROPS,
                new ScramClientCallbackHandler(username, password));
    }

    /**
     * Runs the full client-first, server-first, client-final, server-final exchange.
     * Any {@link SaslException} from either side propagates to the caller.
     */
    static void exchange(SaslClient client, SaslServer server) throws SaslException {
        byte[] clientFirst = client.evaluateChallenge(new byte[0]);
        byte[] serverFirst = server.evaluateResponse(clientFirst);
        byte[] clientFinal = client.evaluateChallenge(serverFirst);
        byte[] serverFinal = server.evaluateResponse(clientFinal);
        client.evaluateChallenge(serverFinal);
    }

    private static class ScramClientCallbackHandler implements CallbackHandler {

        private final String username;
        private final char[] password;

        ScramClientCallbackHandler(String username, String password) {
            this.username = username;
            this.password = password.toCharArray();
        }

        @Override
        public void handle(Callback[] callbacks) throws UnsupportedCallbackException {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback nameCallback) {
                    nameCallback.setName(username);
                } else if (callback instanceof PasswordCallback passwordCallback) {
                    passwordCallback.setPassword(password);
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        }
    }

}
