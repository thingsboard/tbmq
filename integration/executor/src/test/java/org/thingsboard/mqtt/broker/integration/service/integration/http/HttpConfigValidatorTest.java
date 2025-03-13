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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpConfigValidatorTest {

    @Test
    void testValidConfig() {
        HttpIntegrationConfig config = buildValidConfig();
        assertDoesNotThrow(() -> HttpConfigValidator.validate(config));
    }

    @Test
    void testMissingRestEndpointUrl() {
        HttpIntegrationConfig config = buildValidConfig();
        config.setRestEndpointUrl("");

        Exception e = assertThrows(IllegalArgumentException.class, () -> HttpConfigValidator.validate(config));
        assertEquals("REST endpoint URL is required", e.getMessage());
    }

    @Test
    void testInvalidRequestMethod() {
        HttpIntegrationConfig config = buildValidConfig();
        config.setRequestMethod("PATCH");

        Exception e = assertThrows(IllegalArgumentException.class, () -> HttpConfigValidator.validate(config));
        assertEquals("Invalid request method. Allowed values: POST, PUT, GET, DELETE", e.getMessage());
    }

    @Test
    void testNegativeReadTimeout() {
        HttpIntegrationConfig config = buildValidConfig();
        config.setReadTimeoutMs(-1);

        Exception e = assertThrows(IllegalArgumentException.class, () -> HttpConfigValidator.validate(config));
        assertEquals("Read timeout (milliseconds) must not be less than 0", e.getMessage());
    }

    @Test
    void testNegativeMaxParallelRequests() {
        HttpIntegrationConfig config = buildValidConfig();
        config.setMaxParallelRequestsCount(-5);

        Exception e = assertThrows(IllegalArgumentException.class, () -> HttpConfigValidator.validate(config));
        assertEquals("Max number of parallel requests must not be less than 0", e.getMessage());
    }

    @Test
    void testZeroBufferSize() {
        HttpIntegrationConfig config = buildValidConfig();
        config.setMaxInMemoryBufferSizeInKb(0);

        Exception e = assertThrows(IllegalArgumentException.class, () -> HttpConfigValidator.validate(config));
        assertEquals("Max response size (KB) must be greater than 0", e.getMessage());
    }

    private HttpIntegrationConfig buildValidConfig() {
        HttpIntegrationConfig config = new HttpIntegrationConfig();
        config.setRestEndpointUrl("http://localhost:8080/api");
        config.setRequestMethod("POST");
        config.setReadTimeoutMs(1000);
        config.setMaxParallelRequestsCount(10);
        config.setMaxInMemoryBufferSizeInKb(1024);
        return config;
    }
}
