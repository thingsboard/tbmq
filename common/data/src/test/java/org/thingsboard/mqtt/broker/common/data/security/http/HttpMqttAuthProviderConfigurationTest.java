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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.ClientType;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpMqttAuthProviderConfigurationTest {

    @Test
    public void httpDefaultConfigurationConfiguresDeviceOnly() {
        assertThat(HttpMqttAuthProviderConfiguration.defaultConfiguration().configuredClientTypes())
                .containsExactly(ClientType.DEVICE);
    }

    @Test
    public void httpNullDefaultIsDevice() {
        var config = HttpMqttAuthProviderConfiguration.defaultConfiguration();
        config.setDefaultClientType(null);

        assertThat(config.configuredClientTypes()).containsExactly(ClientType.DEVICE);
    }

    @Test
    public void httpApplicationDefaultConfiguresApplication() {
        var config = HttpMqttAuthProviderConfiguration.defaultConfiguration();
        config.setDefaultClientType(ClientType.APPLICATION);

        assertThat(config.configuredClientTypes()).containsExactly(ClientType.APPLICATION);
    }

    @Test
    public void configuredClientTypesIsNotSerialized() throws JsonProcessingException {
        assertThat(new ObjectMapper().writeValueAsString(HttpMqttAuthProviderConfiguration.defaultConfiguration()))
                .doesNotContain("configuredClientTypes");
    }

}
