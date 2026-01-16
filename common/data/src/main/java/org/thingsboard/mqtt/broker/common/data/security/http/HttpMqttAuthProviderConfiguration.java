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
package org.thingsboard.mqtt.broker.common.data.security.http;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SinglePubSubAuthRulesAware;
import org.thingsboard.mqtt.broker.common.data.credentials.AnonymousCredentials;
import org.thingsboard.mqtt.broker.common.data.credentials.ClientCredentials;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.util.AuthRulesUtil;
import org.thingsboard.mqtt.broker.common.data.validation.NoXss;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HttpMqttAuthProviderConfiguration implements MqttAuthProviderConfiguration, SinglePubSubAuthRulesAware {

    @NoXss
    private String restEndpointUrl;
    @NoXss
    private String requestMethod;
    private ClientCredentials credentials;
    private Map<String, String> headers;
    private int readTimeoutMs;
    private int maxParallelRequestsCount;
    private int maxInMemoryBufferSizeInKb;

    private ClientType defaultClientType;
    private PubSubAuthorizationRules authRules;

    // omit for initial implementation
    // private boolean disconnectAfterExpiration;

    @Override
    public MqttAuthProviderType getType() {
        return MqttAuthProviderType.HTTP;
    }

    @Override
    public void validate() {
        if (ClientType.INTEGRATION.equals(defaultClientType)) {
            throw new DataValidationException("INTEGRATION client type is not supported!");
        }
        AuthRulesUtil.validateAndCompileAuthRules(authRules);
    }

    public static HttpMqttAuthProviderConfiguration defaultConfiguration() {
        return HttpMqttAuthProviderConfiguration.builder()
                .restEndpointUrl("http://localhost:8080")
                .requestMethod("POST")
                .credentials(new AnonymousCredentials())
                .headers(Map.of("Content-Type", "application/json"))
                .readTimeoutMs(5000)
                .maxParallelRequestsCount(100)
                .maxInMemoryBufferSizeInKb(256)
                .defaultClientType(ClientType.DEVICE)
                .authRules(PubSubAuthorizationRules.defaultInstance())
                .build();
    }
}
