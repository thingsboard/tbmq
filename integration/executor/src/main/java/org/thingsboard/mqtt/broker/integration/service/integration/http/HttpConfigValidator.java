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

import org.thingsboard.mqtt.broker.common.data.util.StringUtils;

import java.util.Set;

public class HttpConfigValidator {

    private static final Set<String> SUPPORTED_REQUEST_METHODS = Set.of("POST", "PUT", "GET", "DELETE");

    public static void validate(HttpIntegrationConfig httpIntegrationConfig) {
        if (StringUtils.isEmpty(httpIntegrationConfig.getRestEndpointUrl())) {
            throw new IllegalArgumentException("REST endpoint URL is required");
        }
        validateRequestMethod(httpIntegrationConfig.getRequestMethod());
        if (httpIntegrationConfig.getReadTimeoutMs() < 0) {
            throw new IllegalArgumentException("Read timeout (milliseconds) must not be less than 0");
        }
        if (httpIntegrationConfig.getMaxParallelRequestsCount() < 0) {
            throw new IllegalArgumentException("Max number of parallel requests must not be less than 0");
        }
        if (httpIntegrationConfig.getMaxInMemoryBufferSizeInKb() <= 0) {
            throw new IllegalArgumentException("Max response size (KB) must be greater than 0");
        }
    }

    private static void validateRequestMethod(String requestMethod) {
        if (!SUPPORTED_REQUEST_METHODS.contains(requestMethod)) {
            throw new IllegalArgumentException("Invalid request method. Allowed values: POST, PUT, GET, DELETE");
        }
    }

}
