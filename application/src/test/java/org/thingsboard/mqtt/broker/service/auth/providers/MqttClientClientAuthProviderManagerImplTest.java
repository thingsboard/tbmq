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
package org.thingsboard.mqtt.broker.service.auth.providers;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.ssl.SslMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthProviderEventProto;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthProviderProto;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthProviderTypeProto;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@RunWith(MockitoJUnitRunner.class)
public class MqttClientClientAuthProviderManagerImplTest {

    @Mock
    private BasicMqttClientAuthProvider basicProvider;

    @Mock
    private SslMqttClientAuthProvider sslProvider;

    @Mock
    private JwtMqttClientAuthProvider jwtProvider;

    @Mock
    private MqttAuthProviderService providerService;

    @InjectMocks
    private MqttClientClientAuthProviderManagerImpl manager;

    @Test
    public void shouldInitializeProviderFlagsCorrectly() {
        // given
        MqttAuthProvider basicAuth = new MqttAuthProvider();
        basicAuth.setType(MqttAuthProviderType.BASIC);
        basicAuth.setEnabled(true);

        MqttAuthProvider sslAuth = new MqttAuthProvider();
        sslAuth.setType(MqttAuthProviderType.X_509);
        sslAuth.setEnabled(false);

        MqttAuthProvider jwtAuth = new MqttAuthProvider();
        jwtAuth.setType(MqttAuthProviderType.JWT);
        jwtAuth.setEnabled(true);

        given(providerService.getAuthProviders(new PageLink(10)))
                .willReturn(new PageData<>(List.of(basicAuth, sslAuth, jwtAuth), 1, 3, false));

        // when
        manager.init();

        // then
        assertThat(manager.isBasicEnabled()).isTrue();
        assertThat(manager.isSslEnabled()).isFalse();
        assertThat(manager.isJwtEnabled()).isTrue();
    }

    @Test
    public void shouldDoNothing_WhenNoAuthProvidersConfigured() {
        // given
        given(providerService.getAuthProviders(new PageLink(10)))
                .willReturn(new PageData<>(Collections.emptyList(), 1, 0, false));

        // when
        manager.init();

        assertThatNoException().isThrownBy(() -> manager.init());
    }

    @Test
    public void shouldEnableBasicProvider() {
        // given
        MqttAuthProviderProto notification = MqttAuthProviderProto.newBuilder()
                .setEventType(MqttAuthProviderEventProto.PROVIDER_ENABLED)
                .setProviderType(MqttAuthProviderTypeProto.BASIC)
                .build();

        // when
        manager.handleProviderNotification(notification);

        // then
        assertThat(manager.isBasicEnabled()).isTrue();
    }

    @Test
    public void shouldDisableBasicProvider() {
        // given
        MqttAuthProviderProto notification = MqttAuthProviderProto.newBuilder()
                .setEventType(MqttAuthProviderEventProto.PROVIDER_DISABLED)
                .setProviderType(MqttAuthProviderTypeProto.BASIC)
                .build();

        // when
        manager.handleProviderNotification(notification);

        // then
        assertThat(manager.isBasicEnabled()).isFalse();
    }

    @Test
    public void shouldUpdateBasicProvider() {
        // given
        MqttAuthProviderProto notification = MqttAuthProviderProto.newBuilder()
                .setEventType(MqttAuthProviderEventProto.PROVIDER_UPDATED)
                .setProviderType(MqttAuthProviderTypeProto.BASIC)
                .setEnabled(true)
                .setConfiguration("any configuration string")
                .build();

        // when
        manager.handleProviderNotification(notification);

        // then
        assertThat(manager.isBasicEnabled()).isTrue();
        then(basicProvider).should(never()).onConfigurationUpdate(any());
    }

    @Test
    public void shouldEnableSslProvider() {
        MqttAuthProviderProto notification = MqttAuthProviderProto.newBuilder()
                .setEventType(MqttAuthProviderEventProto.PROVIDER_ENABLED)
                .setProviderType(MqttAuthProviderTypeProto.X_509)
                .build();

        manager.handleProviderNotification(notification);

        assertThat(manager.isSslEnabled()).isTrue();
    }

    @Test
    public void shouldDisableSslProvider() {
        MqttAuthProviderProto notification = MqttAuthProviderProto.newBuilder()
                .setEventType(MqttAuthProviderEventProto.PROVIDER_DISABLED)
                .setProviderType(MqttAuthProviderTypeProto.X_509)
                .build();

        manager.handleProviderNotification(notification);

        assertThat(manager.isSslEnabled()).isFalse();
    }

    @Test
    public void shouldUpdateSslProvider() {
        // given
        SslMqttAuthProviderConfiguration configuration = new SslMqttAuthProviderConfiguration();
        MqttAuthProviderProto notification = MqttAuthProviderProto.newBuilder()
                .setEventType(MqttAuthProviderEventProto.PROVIDER_UPDATED)
                .setProviderType(MqttAuthProviderTypeProto.X_509)
                .setEnabled(true)
                .setConfiguration(JacksonUtil.toString(configuration))
                .build();

        // when
        manager.handleProviderNotification(notification);

        // then
        assertThat(manager.isSslEnabled()).isTrue();
        then(sslProvider).should().onConfigurationUpdate(new SslMqttAuthProviderConfiguration());
    }

    @Test
    public void shouldEnableJwtProvider() {
        MqttAuthProviderProto notification = MqttAuthProviderProto.newBuilder()
                .setEventType(MqttAuthProviderEventProto.PROVIDER_ENABLED)
                .setProviderType(MqttAuthProviderTypeProto.JWT)
                .build();

        manager.handleProviderNotification(notification);

        assertThat(manager.isJwtEnabled()).isTrue();
    }

    @Test
    public void shouldDisableJwtProvider() {
        MqttAuthProviderProto notification = MqttAuthProviderProto.newBuilder()
                .setEventType(MqttAuthProviderEventProto.PROVIDER_DISABLED)
                .setProviderType(MqttAuthProviderTypeProto.JWT)
                .build();

        manager.handleProviderNotification(notification);

        assertThat(manager.isJwtEnabled()).isFalse();
    }

    @Test
    public void shouldUpdateJwtProvider() {
        JwtMqttAuthProviderConfiguration mockedConfiguration = new JwtMqttAuthProviderConfiguration();
        MqttAuthProviderProto notification = MqttAuthProviderProto.newBuilder()
                .setEventType(MqttAuthProviderEventProto.PROVIDER_UPDATED)
                .setProviderType(MqttAuthProviderTypeProto.JWT)
                .setEnabled(true)
                .setConfiguration(JacksonUtil.toString(mockedConfiguration))
                .build();

        manager.handleProviderNotification(notification);

        assertThat(manager.isJwtEnabled()).isTrue();
        then(jwtProvider).should().onConfigurationUpdate(mockedConfiguration);
    }
}