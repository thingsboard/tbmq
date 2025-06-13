/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.common.data.security.jwt;

import lombok.Data;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.credentials.AnonymousCredentials;
import org.thingsboard.mqtt.broker.common.data.credentials.ClientCredentials;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.data.validation.NoXss;

import java.util.Map;
import java.util.Objects;

@Data
public class JwksVerifierConfiguration implements JwtVerifierConfiguration {

    private static final int MIN_REFRESH_INTERVAL_IN_SECONDS = 300;

    @NoXss
    private String endpoint;
    private Map<String, String> headers;

    private int refreshInterval;

    private ClientCredentials credentials;

    @Override
    public JwtVerifierType getJwtVerifierType() {
        return JwtVerifierType.JWKS;
    }

    @Override
    public void validate() {
        // TODO: add validation
    }

    public String getEndpoint() {
        return StringUtils.isNotBlank(endpoint) ? endpoint : "http://127.0.0.1:8080";
    }

    public int getRefreshInterval() {
        return Math.max(MIN_REFRESH_INTERVAL_IN_SECONDS, refreshInterval);
    }

    public ClientCredentials getCredentials() {
        return Objects.requireNonNullElseGet(this.credentials, AnonymousCredentials::new);
    }

    public Map<String, String> getHeaders() {
        return CollectionUtils.isEmpty(headers) ? Map.of("Content-Type", "application/json") : headers;
    }

}
