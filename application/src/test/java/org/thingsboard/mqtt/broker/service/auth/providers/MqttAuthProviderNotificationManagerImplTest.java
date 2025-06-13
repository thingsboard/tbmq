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
import org.thingsboard.mqtt.broker.common.data.security.basic.BasicMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.scram.ScramMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.ssl.SslMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthProviderEventProto;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthProviderProto;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthProviderTypeProto;
import org.thingsboard.mqtt.broker.service.auth.EnhancedAuthenticationService;
import org.thingsboard.mqtt.broker.service.auth.providers.basic.BasicMqttClientAuthProvider;
import org.thingsboard.mqtt.broker.service.auth.providers.jwt.JwtMqttClientAuthProvider;
import org.thingsboard.mqtt.broker.service.auth.providers.ssl.SslMqttClientAuthProvider;

import static org.mockito.BDDMockito.then;

@RunWith(MockitoJUnitRunner.class)
public class MqttAuthProviderNotificationManagerImplTest {

    @Mock
    private BasicMqttClientAuthProvider basicProvider;

    @Mock
    private SslMqttClientAuthProvider sslProvider;

    @Mock
    private JwtMqttClientAuthProvider jwtProvider;

    @Mock
    private EnhancedAuthenticationService enhancedAuthenticationService;

    @InjectMocks
    private MqttAuthProviderNotificationManagerImpl manager;

    @Test
    public void shouldEnableBasicProvider() {
        // given
        MqttAuthProviderProto notification = MqttAuthProviderProto.newBuilder()
                .setEventType(MqttAuthProviderEventProto.PROVIDER_ENABLED)
                .setProviderType(MqttAuthProviderTypeProto.MQTT_BASIC)
                .build();

        // when
        manager.handleProviderNotification(notification);

        // then
        then(basicProvider).should().enable();
    }

    @Test
    public void shouldDisableBasicProvider() {
        // given
        MqttAuthProviderProto notification = MqttAuthProviderProto.newBuilder()
                .setEventType(MqttAuthProviderEventProto.PROVIDER_DISABLED)
                .setProviderType(MqttAuthProviderTypeProto.MQTT_BASIC)
                .build();

        // when
        manager.handleProviderNotification(notification);

        // then
        then(basicProvider).should().disable();
    }

    @Test
    public void shouldUpdateBasicProvider() {
        // given
        var mockedConfiguration = new BasicMqttAuthProviderConfiguration();
        MqttAuthProviderProto notification = MqttAuthProviderProto.newBuilder()
                .setEventType(MqttAuthProviderEventProto.PROVIDER_UPDATED)
                .setProviderType(MqttAuthProviderTypeProto.MQTT_BASIC)
                .setEnabled(true)
                .setConfiguration(JacksonUtil.toString(mockedConfiguration))
                .build();

        // when
        manager.handleProviderNotification(notification);

        // then
        then(basicProvider).should().onProviderUpdate(true, mockedConfiguration);
    }

    @Test
    public void shouldEnableSslProvider() {
        // given
        MqttAuthProviderProto notification = MqttAuthProviderProto.newBuilder()
                .setEventType(MqttAuthProviderEventProto.PROVIDER_ENABLED)
                .setProviderType(MqttAuthProviderTypeProto.X_509)
                .build();

        // when
        manager.handleProviderNotification(notification);

        // then
        then(sslProvider).should().enable();
    }

    @Test
    public void shouldDisableSslProvider() {
        // given
        MqttAuthProviderProto notification = MqttAuthProviderProto.newBuilder()
                .setEventType(MqttAuthProviderEventProto.PROVIDER_DISABLED)
                .setProviderType(MqttAuthProviderTypeProto.X_509)
                .build();

        // when
        manager.handleProviderNotification(notification);

        // then
        then(sslProvider).should().disable();
    }

    @Test
    public void shouldUpdateSslProvider() {
        // given
        var mockedConfiguration = new SslMqttAuthProviderConfiguration();
        MqttAuthProviderProto notification = MqttAuthProviderProto.newBuilder()
                .setEventType(MqttAuthProviderEventProto.PROVIDER_UPDATED)
                .setProviderType(MqttAuthProviderTypeProto.X_509)
                .setEnabled(false)
                .setConfiguration(JacksonUtil.toString(mockedConfiguration))
                .build();

        // when
        manager.handleProviderNotification(notification);

        // then
        then(sslProvider).should().onProviderUpdate(false, mockedConfiguration);
    }

    @Test
    public void shouldEnableJwtProvider() {
        // given
        MqttAuthProviderProto notification = MqttAuthProviderProto.newBuilder()
                .setEventType(MqttAuthProviderEventProto.PROVIDER_ENABLED)
                .setProviderType(MqttAuthProviderTypeProto.JWT)
                .build();

        // when
        manager.handleProviderNotification(notification);

        // then
        then(jwtProvider).should().enable();
    }

    @Test
    public void shouldDisableJwtProvider() {
        // given
        MqttAuthProviderProto notification = MqttAuthProviderProto.newBuilder()
                .setEventType(MqttAuthProviderEventProto.PROVIDER_DISABLED)
                .setProviderType(MqttAuthProviderTypeProto.JWT)
                .build();

        // when
        manager.handleProviderNotification(notification);

        // then
        then(jwtProvider).should().disable();
    }

    @Test
    public void shouldUpdateJwtProvider() {
        // given
        var mockedConfiguration = new JwtMqttAuthProviderConfiguration();
        MqttAuthProviderProto notification = MqttAuthProviderProto.newBuilder()
                .setEventType(MqttAuthProviderEventProto.PROVIDER_UPDATED)
                .setProviderType(MqttAuthProviderTypeProto.JWT)
                .setEnabled(true)
                .setConfiguration(JacksonUtil.toString(mockedConfiguration))
                .build();

        // when
        manager.handleProviderNotification(notification);

        // then
        then(jwtProvider).should().onProviderUpdate(true, mockedConfiguration);
    }


    @Test
    public void shouldEnableScramProvider() {
        // given
        MqttAuthProviderProto notification = MqttAuthProviderProto.newBuilder()
                .setEventType(MqttAuthProviderEventProto.PROVIDER_ENABLED)
                .setProviderType(MqttAuthProviderTypeProto.SCRAM)
                .build();

        // when
        manager.handleProviderNotification(notification);

        // then
        then(enhancedAuthenticationService).should().enable();
    }

    @Test
    public void shouldDisableScramProvider() {
        // given
        MqttAuthProviderProto notification = MqttAuthProviderProto.newBuilder()
                .setEventType(MqttAuthProviderEventProto.PROVIDER_DISABLED)
                .setProviderType(MqttAuthProviderTypeProto.SCRAM)
                .build();

        // when
        manager.handleProviderNotification(notification);

        // then
        then(enhancedAuthenticationService).should().disable();
    }

    @Test
    public void shouldUpdateScramProvider() {
        // given
        var mockedConfiguration = new ScramMqttAuthProviderConfiguration();
        MqttAuthProviderProto notification = MqttAuthProviderProto.newBuilder()
                .setEventType(MqttAuthProviderEventProto.PROVIDER_UPDATED)
                .setProviderType(MqttAuthProviderTypeProto.SCRAM)
                .setEnabled(true)
                .setConfiguration(JacksonUtil.toString(mockedConfiguration))
                .build();

        // when
        manager.handleProviderNotification(notification);

        // then
        then(enhancedAuthenticationService).should().onProviderUpdate(true, mockedConfiguration);
    }

}
