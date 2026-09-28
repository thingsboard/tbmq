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
package org.thingsboard.mqtt.broker.common.data.client.credentials;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.mqtt.broker.common.data.validation.NoXss;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Data
@NoArgsConstructor
public class SslMqttCredentials implements Serializable {

    @Serial
    private static final long serialVersionUID = 9061083999189901313L;

    @NoXss
    private String certCnPattern;
    private boolean certCnIsRegex;
    @NoXss
    private String clientIdPattern;
    private boolean clientIdIsRegex;
    private Map<String, PubSubAuthorizationRules> authRulesMapping;

    public SslMqttCredentials(String certCnPattern, Map<String, PubSubAuthorizationRules> authRulesMapping) {
        this(certCnPattern, false, null, false, authRulesMapping);
    }

    public SslMqttCredentials(String certCnPattern, boolean certCnIsRegex, String clientIdPattern,
                              boolean clientIdIsRegex, Map<String, PubSubAuthorizationRules> authRulesMapping) {
        this.certCnPattern = certCnPattern;
        this.certCnIsRegex = certCnIsRegex;
        this.clientIdPattern = clientIdPattern;
        this.clientIdIsRegex = clientIdIsRegex;
        this.authRulesMapping = authRulesMapping;
    }

    public static SslMqttCredentials newInstance(String certCommonName, String key, List<String> authRules) {
        return new SslMqttCredentials(certCommonName, false, null, false,
                Map.of(key, PubSubAuthorizationRules.newInstance(authRules)));
    }

    public boolean matchesClientId(String clientId) {
        if (clientIdPattern == null || clientIdPattern.isEmpty()) {
            return true;
        }
        if (clientId == null) {
            return false;
        }
        return clientIdIsRegex ? Pattern.matches(clientIdPattern, clientId) : clientIdPattern.equals(clientId);
    }
}
