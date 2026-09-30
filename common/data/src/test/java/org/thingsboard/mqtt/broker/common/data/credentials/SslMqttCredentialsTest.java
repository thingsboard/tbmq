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
package org.thingsboard.mqtt.broker.common.data.credentials;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SslMqttCredentials;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class SslMqttCredentialsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void givenNoClientIdConstraint_whenSerialize_thenKeepPreClientIdJsonShape() throws Exception {
        SslMqttCredentials credentials = new SslMqttCredentials("cn", Map.of(".*", PubSubAuthorizationRules.defaultInstance()));

        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(credentials));

        Assertions.assertEquals(Set.of("certCnPattern", "certCnIsRegex", "authRulesMapping"), fieldNames(json));
    }

    @Test
    public void givenEmptyClientIdPattern_whenSerialize_thenOmitClientIdFields() throws Exception {
        SslMqttCredentials credentials = new SslMqttCredentials("cn", false, "", false,
                Map.of(".*", PubSubAuthorizationRules.defaultInstance()));

        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(credentials));

        Assertions.assertFalse(json.has("clientIdPattern"));
        Assertions.assertFalse(json.has("clientIdIsRegex"));
    }

    @Test
    public void givenClientIdConstraint_whenSerialize_thenIncludeClientIdFields() throws Exception {
        SslMqttCredentials credentials = new SslMqttCredentials("cn", false, "device-[0-9]+", true,
                Map.of(".*", PubSubAuthorizationRules.defaultInstance()));

        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(credentials));

        Assertions.assertEquals("device-[0-9]+", json.get("clientIdPattern").asText());
        Assertions.assertTrue(json.get("clientIdIsRegex").asBoolean());
    }

    private static Set<String> fieldNames(JsonNode json) {
        Set<String> names = new HashSet<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }

}
