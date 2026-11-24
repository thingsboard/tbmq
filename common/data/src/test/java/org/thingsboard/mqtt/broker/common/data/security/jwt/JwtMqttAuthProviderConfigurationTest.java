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
package org.thingsboard.mqtt.broker.common.data.security.jwt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class JwtMqttAuthProviderConfigurationTest {

    @Test
    public void shouldThrowIfDefaultClientTypeIsIntegration() {
        JwtMqttAuthProviderConfiguration config = new JwtMqttAuthProviderConfiguration();
        config.setDefaultClientType(ClientType.INTEGRATION);
        config.setAuthRules(PubSubAuthorizationRules.newInstance(List.of(".*")));
        config.setJwtVerifierConfiguration(mock(JwtVerifierConfiguration.class));

        assertThatThrownBy(config::validate)
            .isInstanceOf(DataValidationException.class)
            .hasMessage("INTEGRATION client type is not supported!");
    }

    @Test
    public void shouldThrowIfJwtVerifierConfigurationIsNull() {
        JwtMqttAuthProviderConfiguration config = new JwtMqttAuthProviderConfiguration();
        config.setDefaultClientType(ClientType.DEVICE);
        config.setAuthRules(PubSubAuthorizationRules.newInstance(List.of(".*")));
        config.setJwtVerifierConfiguration(null);

        assertThatThrownBy(config::validate)
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Jwt verifier configuration should be specified!");
    }

    @Test
    public void shouldDelegateToVerifierConfigValidate() {
        JwtVerifierConfiguration verifierMock = mock(JwtVerifierConfiguration.class);
        JwtMqttAuthProviderConfiguration config = new JwtMqttAuthProviderConfiguration();
        config.setDefaultClientType(ClientType.DEVICE);
        config.setAuthRules(PubSubAuthorizationRules.newInstance(List.of(".*")));
        config.setJwtVerifierConfiguration(verifierMock);

        assertThatCode(config::validate).doesNotThrowAnyException();
        verify(verifierMock).validate();
    }

    @Test
    public void shouldPassWithValidDefaultConfiguration() {
        JwtMqttAuthProviderConfiguration config = JwtMqttAuthProviderConfiguration.defaultConfiguration();
        assertThatCode(config::validate).doesNotThrowAnyException();
    }

    @Test
    public void jwtDefaultConfigurationConfiguresDeviceOnly() {
        assertThat(JwtMqttAuthProviderConfiguration.defaultConfiguration().configuredClientTypes())
                .containsExactly(ClientType.DEVICE);
    }

    @Test
    public void jwtWithDeviceDefaultAndClaimsConfiguresBothTypes() {
        var config = JwtMqttAuthProviderConfiguration.defaultConfiguration();
        config.setClientTypeClaims(Map.of("role", "app"));

        assertThat(config.configuredClientTypes()).containsExactlyInAnyOrder(ClientType.DEVICE, ClientType.APPLICATION);
    }

    @Test
    public void jwtWithApplicationDefaultAndClaimsConfiguresBothTypes() {
        var config = JwtMqttAuthProviderConfiguration.defaultConfiguration();
        config.setDefaultClientType(ClientType.APPLICATION);
        config.setClientTypeClaims(Map.of("role", "dev"));

        assertThat(config.configuredClientTypes()).containsExactlyInAnyOrder(ClientType.DEVICE, ClientType.APPLICATION);
    }

    @Test
    public void jwtWithApplicationDefaultAndNoClaimsConfiguresApplicationOnly() {
        var config = JwtMqttAuthProviderConfiguration.defaultConfiguration();
        config.setDefaultClientType(ClientType.APPLICATION);

        assertThat(config.configuredClientTypes()).containsExactly(ClientType.APPLICATION);
    }

    @Test
    public void jwtWithNullDefaultIsDevice() {
        var config = JwtMqttAuthProviderConfiguration.defaultConfiguration();
        config.setDefaultClientType(null);

        assertThat(config.configuredClientTypes()).containsExactly(ClientType.DEVICE);
    }

    @Test
    public void configuredClientTypesIsNotSerialized() throws JsonProcessingException {
        assertThat(new ObjectMapper().writeValueAsString(JwtMqttAuthProviderConfiguration.defaultConfiguration()))
                .doesNotContain("configuredClientTypes");
    }

}
