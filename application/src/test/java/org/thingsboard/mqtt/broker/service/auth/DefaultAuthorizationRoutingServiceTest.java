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
package org.thingsboard.mqtt.broker.service.auth;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.SysAdminSettingType;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthProviderTypeProto;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthSettingsProto;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.service.auth.providers.basic.BasicMqttClientAuthProvider;
import org.thingsboard.mqtt.broker.service.auth.providers.jwt.JwtMqttClientAuthProvider;
import org.thingsboard.mqtt.broker.service.auth.providers.ssl.SslMqttClientAuthProvider;
import org.thingsboard.mqtt.broker.service.install.data.MqttAuthSettings;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;


@RunWith(MockitoJUnitRunner.class)
public class DefaultAuthorizationRoutingServiceTest {

    @Mock
    private BasicMqttClientAuthProvider basicMqttClientAuthProvider;

    @Mock
    private SslMqttClientAuthProvider sslMqttClientAuthProvider;

    @Mock
    private JwtMqttClientAuthProvider jwtMqttClientAuthProvider;

    @Mock
    private AuthContext authContext;

    @Mock
    private AdminSettingsService adminSettingsService;

    @InjectMocks
    private DefaultAuthorizationRoutingService service;

    @Test
    @SuppressWarnings({"unchecked", "ConstantConditions"})
    public void shouldApplyDefaultSettings_WhenNoAdminSettingsFound() {
        // given
        given(adminSettingsService.findAdminSettingsByKey(SysAdminSettingType.MQTT_AUTHORIZATION.getKey())).willReturn(null);

        // when
        service.init();

        // then
        List<MqttAuthProviderType> priorities =
                (List<MqttAuthProviderType>) ReflectionTestUtils.getField(service, "priorities");
        boolean useListenerBasedProviderOnly = (boolean) ReflectionTestUtils.getField(service, "useListenerBasedProviderOnly");

        assertThat(priorities).isEqualTo(MqttAuthProviderType.getDefaultPriorityList());
        assertThat(useListenerBasedProviderOnly).isFalse();
    }

    @Test
    @SuppressWarnings({"unchecked", "ConstantConditions"})
    public void shouldLoadAdminSettings_WhenPresent() {
        // given
        MqttAuthSettings mqttAuthSettings = new MqttAuthSettings();
        mqttAuthSettings.setUseListenerBasedProviderOnly(true);
        mqttAuthSettings.setPriorities(List.of(MqttAuthProviderType.JWT, MqttAuthProviderType.BASIC));

        AdminSettings mockSettings = new AdminSettings();
        mockSettings.setJsonValue(JacksonUtil.valueToTree(mqttAuthSettings));

        given(adminSettingsService.findAdminSettingsByKey(SysAdminSettingType.MQTT_AUTHORIZATION.getKey()))
                .willReturn(mockSettings);

        // when
        service.init();

        // then
        List<MqttAuthProviderType> priorities =
                (List<MqttAuthProviderType>) ReflectionTestUtils.getField(service, "priorities");
        boolean useListenerBasedProviderOnly =
                (boolean) ReflectionTestUtils.getField(service, "useListenerBasedProviderOnly");

        assertThat(priorities).containsExactly(MqttAuthProviderType.JWT, MqttAuthProviderType.BASIC);
        assertThat(useListenerBasedProviderOnly).isTrue();
    }

    @Test
    public void shouldAuthenticateSuccessfullyWithBasic() {
        // given
        MqttAuthSettingsProto settings = MqttAuthSettingsProto.newBuilder()
                .addPriorities(MqttAuthProviderTypeProto.BASIC)
                .setUseListenerBasedProviderOnly(false)
                .build();
        service.onMqttAuthSettingsUpdate(settings);

        given(basicMqttClientAuthProvider.isEnabled()).willReturn(true);
        given(basicMqttClientAuthProvider.authenticate(authContext))
                .willReturn(AuthResponse.defaultAuthResponse());

        // when
        AuthResponse result = service.executeAuthFlow(authContext);

        // then
        assertThat(result.isSuccess()).isTrue();
        then(basicMqttClientAuthProvider).should().authenticate(authContext);
        then(sslMqttClientAuthProvider).shouldHaveNoInteractions();
        then(jwtMqttClientAuthProvider).shouldHaveNoInteractions();
    }

    @Test
    public void shouldAuthenticateSuccessfullyWithSsl() {
        // given
        MqttAuthSettingsProto settings = MqttAuthSettingsProto.newBuilder()
                .addPriorities(MqttAuthProviderTypeProto.X_509)
                .setUseListenerBasedProviderOnly(false)
                .build();
        service.onMqttAuthSettingsUpdate(settings);

        given(sslMqttClientAuthProvider.isEnabled()).willReturn(true);
        given(sslMqttClientAuthProvider.authenticate(authContext))
                .willReturn(AuthResponse.defaultAuthResponse());

        // when
        AuthResponse result = service.executeAuthFlow(authContext);

        // then
        assertThat(result.isSuccess()).isTrue();
        then(sslMqttClientAuthProvider).should().authenticate(authContext);
        assertThat(then(basicMqttClientAuthProvider).should().isEnabled()).isFalse();
        then(jwtMqttClientAuthProvider).shouldHaveNoInteractions();
    }

    @Test
    public void shouldAuthenticateSuccessfullyWithJwt() {
        // given
        MqttAuthSettingsProto settings = MqttAuthSettingsProto.newBuilder()
                .addPriorities(MqttAuthProviderTypeProto.JWT)
                .setUseListenerBasedProviderOnly(false)
                .build();
        service.onMqttAuthSettingsUpdate(settings);

        given(jwtMqttClientAuthProvider.isEnabled()).willReturn(true);
        given(jwtMqttClientAuthProvider.authenticate(authContext))
                .willReturn(AuthResponse.defaultAuthResponse());

        // when
        AuthResponse result = service.executeAuthFlow(authContext);

        // then
        assertThat(result.isSuccess()).isTrue();
        then(jwtMqttClientAuthProvider).should().authenticate(authContext);
        assertThat(then(basicMqttClientAuthProvider).should().isEnabled()).isFalse();
        assertThat(then(sslMqttClientAuthProvider).should().isEnabled()).isFalse();
    }

    @Test
    public void shouldRespectListenerBasedFiltering_SecurePort_RemovesBasic() {
        // given
        MqttAuthSettingsProto settings = MqttAuthSettingsProto.newBuilder()
                .addPriorities(MqttAuthProviderTypeProto.BASIC)
                .addPriorities(MqttAuthProviderTypeProto.X_509)
                .setUseListenerBasedProviderOnly(true)
                .build();
        service.onMqttAuthSettingsUpdate(settings);

        given(sslMqttClientAuthProvider.isEnabled()).willReturn(true);
        given(authContext.isSecurePortUsed()).willReturn(true);
        given(sslMqttClientAuthProvider.authenticate(authContext))
                .willReturn(AuthResponse.defaultAuthResponse());

        // when
        AuthResponse result = service.executeAuthFlow(authContext);

        // then
        assertThat(result.isSuccess()).isTrue();
        then(sslMqttClientAuthProvider).should().authenticate(authContext);
        assertThat(then(basicMqttClientAuthProvider).should().isEnabled()).isFalse();
        then(jwtMqttClientAuthProvider).shouldHaveNoInteractions();
    }

    @Test
    public void shouldRespectListenerBasedFiltering_NonSecurePort_RemovesSsl() {
        // given
        MqttAuthSettingsProto settings = MqttAuthSettingsProto.newBuilder()
                .addPriorities(MqttAuthProviderTypeProto.BASIC)
                .addPriorities(MqttAuthProviderTypeProto.X_509)
                .setUseListenerBasedProviderOnly(true)
                .build();
        service.onMqttAuthSettingsUpdate(settings);

        given(basicMqttClientAuthProvider.isEnabled()).willReturn(true);
        given(authContext.isSecurePortUsed()).willReturn(false);
        given(basicMqttClientAuthProvider.authenticate(authContext))
                .willReturn(AuthResponse.defaultAuthResponse());

        // when
        AuthResponse result = service.executeAuthFlow(authContext);

        // then
        assertThat(result.isSuccess()).isTrue();
        then(basicMqttClientAuthProvider).should().authenticate(authContext);
        then(sslMqttClientAuthProvider).shouldHaveNoInteractions();
        then(jwtMqttClientAuthProvider).shouldHaveNoInteractions();
    }

    @Test
    public void shouldAggregateFailures_WhenAllProvidersFail() {
        // given
        MqttAuthSettingsProto settings = MqttAuthSettingsProto.newBuilder()
                .addPriorities(MqttAuthProviderTypeProto.BASIC)
                .addPriorities(MqttAuthProviderTypeProto.X_509)
                .addPriorities(MqttAuthProviderTypeProto.JWT)
                .setUseListenerBasedProviderOnly(false)
                .build();
        service.onMqttAuthSettingsUpdate(settings);

        given(basicMqttClientAuthProvider.isEnabled()).willReturn(true);

        given(basicMqttClientAuthProvider.authenticate(authContext))
                .willReturn(AuthResponse.failure("basic failed"));
        given(sslMqttClientAuthProvider.authenticate(authContext))
                .willReturn(AuthResponse.failure("ssl failed"));
        given(jwtMqttClientAuthProvider.authenticate(authContext))
                .willReturn(AuthResponse.failure("jwt failed"));

        // when
        AuthResponse result = service.executeAuthFlow(authContext);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getReason())
                .contains("basic failed")
                .contains("ssl failed")
                .contains("jwt failed");

        then(basicMqttClientAuthProvider).should().authenticate(authContext);
        then(sslMqttClientAuthProvider).should().authenticate(authContext);
        then(jwtMqttClientAuthProvider).should().authenticate(authContext);
    }

}