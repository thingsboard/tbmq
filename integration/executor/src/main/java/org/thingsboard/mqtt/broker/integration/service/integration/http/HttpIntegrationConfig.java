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
package org.thingsboard.mqtt.broker.integration.service.integration.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.integration.api.data.ContentType;
import org.thingsboard.mqtt.broker.integration.service.integration.credentials.AnonymousCredentials;
import org.thingsboard.mqtt.broker.integration.service.integration.credentials.ClientCredentials;

import java.util.Map;
import java.util.Objects;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
public class HttpIntegrationConfig {

    private String restEndpointUrl;
    private String requestMethod;
    private Map<String, String> headers;
    private int readTimeoutMs;
    private int maxParallelRequestsCount;
    private ClientCredentials credentials;
    private int maxInMemoryBufferSizeInKb;
    private ContentType payloadContentType;
    private boolean sendBinaryOnParseFailure;

    public HttpIntegrationConfig(String restEndpointUrl) {
        this(restEndpointUrl, "POST", Map.of("Content-Type", "application/json"), 0, 0, null, 256, ContentType.BINARY, true);
    }

    public ClientCredentials getCredentials() {
        return Objects.requireNonNullElseGet(this.credentials, AnonymousCredentials::new);
    }

    public Map<String, String> getHeaders() {
        return CollectionUtils.isEmpty(headers) ? Map.of() : headers;
    }

    public ContentType getPayloadContentType() {
        return Objects.requireNonNullElse(payloadContentType, ContentType.BINARY);
    }
}
