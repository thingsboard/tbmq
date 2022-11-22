/**
 * Copyright © 2016-2022 The Thingsboard Authors
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

import io.netty.handler.ssl.SslHandler;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthProviderType;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.service.auth.providers.BasicMqttClientAuthProvider;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttClientAuthProviderManager;
import org.thingsboard.mqtt.broker.service.auth.providers.SslMqttClientAuthProvider;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class DefaultAuthenticationServiceTest {

    MqttClientAuthProviderManager mqttClientAuthProviderManager;
    DefaultAuthenticationService authenticationService;

    @Test
    public void testAuthenticateNoProviders() throws AuthenticationException {
        mqttClientAuthProviderManager = mock(MqttClientAuthProviderManager.class);
        authenticationService = spy(new DefaultAuthenticationService(mqttClientAuthProviderManager));

        AuthContext authContext = getAuthContext(null);
        AuthResponse authResponse = authenticationService.authenticate(authContext);

        Assert.assertTrue(authResponse.isSuccess());
        Assert.assertEquals(ClientType.DEVICE, authResponse.getClientType());
        Assert.assertNull(authResponse.getAuthRulePatterns());
    }

    @Test(expected = AuthenticationException.class)
    public void testAuthenticateFailureUsingBasic() throws AuthenticationException {
        mqttClientAuthProviderManager = mock(MqttClientAuthProviderManager.class);
        BasicMqttClientAuthProvider basicMqttClientAuthProvider = mock(BasicMqttClientAuthProvider.class);
        SslMqttClientAuthProvider sslMqttClientAuthProvider = mock(SslMqttClientAuthProvider.class);
        mockGetActiveAuthProviders(basicMqttClientAuthProvider, sslMqttClientAuthProvider);

        authenticationService = spy(new DefaultAuthenticationService(mqttClientAuthProviderManager));
        authenticationService.setAuthStrategy(AuthStrategy.SINGLE);

        when(basicMqttClientAuthProvider.authorize(any())).thenReturn(new AuthResponse(false, null, null));

        AuthContext authContext = getAuthContext(null);
        authenticationService.authenticate(authContext);
    }

    @Test(expected = AuthenticationException.class)
    public void testAuthenticateFailureUsingSsl() throws AuthenticationException {
        SslHandler sslHandler = mock(SslHandler.class);
        mqttClientAuthProviderManager = mock(MqttClientAuthProviderManager.class);
        BasicMqttClientAuthProvider basicMqttClientAuthProvider = mock(BasicMqttClientAuthProvider.class);
        SslMqttClientAuthProvider sslMqttClientAuthProvider = mock(SslMqttClientAuthProvider.class);
        mockGetActiveAuthProviders(basicMqttClientAuthProvider, sslMqttClientAuthProvider);

        authenticationService = spy(new DefaultAuthenticationService(mqttClientAuthProviderManager));
        authenticationService.setAuthStrategy(AuthStrategy.SINGLE);

        when(sslMqttClientAuthProvider.authorize(any())).thenReturn(new AuthResponse(false, null, null));

        AuthContext authContext = getAuthContext(sslHandler);
        authenticationService.authenticate(authContext);
    }

    @Test
    public void testAuthenticateSuccessUsingSsl() throws AuthenticationException {
        SslHandler sslHandler = mock(SslHandler.class);
        mqttClientAuthProviderManager = mock(MqttClientAuthProviderManager.class);
        BasicMqttClientAuthProvider basicMqttClientAuthProvider = mock(BasicMqttClientAuthProvider.class);
        SslMqttClientAuthProvider sslMqttClientAuthProvider = mock(SslMqttClientAuthProvider.class);
        mockGetActiveAuthProviders(basicMqttClientAuthProvider, sslMqttClientAuthProvider);

        authenticationService = spy(new DefaultAuthenticationService(mqttClientAuthProviderManager));
        authenticationService.setAuthStrategy(AuthStrategy.SINGLE);

        when(sslMqttClientAuthProvider.authorize(any())).thenReturn(new AuthResponse(true, ClientType.APPLICATION, null));

        AuthContext authContext = getAuthContext(sslHandler);
        AuthResponse authResponse = authenticationService.authenticate(authContext);
        Assert.assertTrue(authResponse.isSuccess());
        Assert.assertEquals(ClientType.APPLICATION, authResponse.getClientType());
        Assert.assertNull(authResponse.getAuthRulePatterns());
    }

    @Test
    public void testAuthenticateSuccessUsingBasic() throws AuthenticationException {
        mqttClientAuthProviderManager = mock(MqttClientAuthProviderManager.class);
        BasicMqttClientAuthProvider basicMqttClientAuthProvider = mock(BasicMqttClientAuthProvider.class);
        SslMqttClientAuthProvider sslMqttClientAuthProvider = mock(SslMqttClientAuthProvider.class);
        mockGetActiveAuthProviders(basicMqttClientAuthProvider, sslMqttClientAuthProvider);

        authenticationService = spy(new DefaultAuthenticationService(mqttClientAuthProviderManager));
        authenticationService.setAuthStrategy(AuthStrategy.SINGLE);

        when(basicMqttClientAuthProvider.authorize(any())).thenReturn(new AuthResponse(true, ClientType.DEVICE, null));

        AuthContext authContext = getAuthContext(null);
        AuthResponse authResponse = authenticationService.authenticate(authContext);
        Assert.assertTrue(authResponse.isSuccess());
        Assert.assertEquals(ClientType.DEVICE, authResponse.getClientType());
    }

    @Test
    public void testAuthenticateSuccessUsingBasicWhenBothAuthStrategySet() throws AuthenticationException {
        mqttClientAuthProviderManager = mock(MqttClientAuthProviderManager.class);
        BasicMqttClientAuthProvider basicMqttClientAuthProvider = mock(BasicMqttClientAuthProvider.class);
        SslMqttClientAuthProvider sslMqttClientAuthProvider = mock(SslMqttClientAuthProvider.class);
        mockGetActiveAuthProviders(basicMqttClientAuthProvider, sslMqttClientAuthProvider);

        authenticationService = spy(new DefaultAuthenticationService(mqttClientAuthProviderManager));
        authenticationService.setAuthStrategy(AuthStrategy.BOTH);

        when(basicMqttClientAuthProvider.authorize(any())).thenReturn(new AuthResponse(true, ClientType.DEVICE, null));

        AuthContext authContext = getAuthContext(null);
        AuthResponse authResponse = authenticationService.authenticate(authContext);
        Assert.assertTrue(authResponse.isSuccess());
        Assert.assertEquals(ClientType.DEVICE, authResponse.getClientType());
    }

    @Test
    public void testAuthenticateSuccessUsingSslWhenBothAuthStrategySet() throws AuthenticationException {
        SslHandler sslHandler = mock(SslHandler.class);
        mqttClientAuthProviderManager = mock(MqttClientAuthProviderManager.class);
        BasicMqttClientAuthProvider basicMqttClientAuthProvider = mock(BasicMqttClientAuthProvider.class);
        SslMqttClientAuthProvider sslMqttClientAuthProvider = mock(SslMqttClientAuthProvider.class);
        mockGetActiveAuthProviders(basicMqttClientAuthProvider, sslMqttClientAuthProvider);

        authenticationService = spy(new DefaultAuthenticationService(mqttClientAuthProviderManager));
        authenticationService.setAuthStrategy(AuthStrategy.BOTH);

        when(basicMqttClientAuthProvider.authorize(any())).thenReturn(new AuthResponse(false, ClientType.DEVICE, null));
        when(sslMqttClientAuthProvider.authorize(any())).thenReturn(new AuthResponse(true, ClientType.APPLICATION, null));

        AuthContext authContext = getAuthContext(sslHandler);
        AuthResponse authResponse = authenticationService.authenticate(authContext);
        Assert.assertTrue(authResponse.isSuccess());
        Assert.assertEquals(ClientType.APPLICATION, authResponse.getClientType());
    }

    @Test
    public void testAuthenticateSuccessUsingBasicWhenBothAuthStrategySetAndSslContextUsed() throws AuthenticationException {
        SslHandler sslHandler = mock(SslHandler.class);
        mqttClientAuthProviderManager = mock(MqttClientAuthProviderManager.class);
        BasicMqttClientAuthProvider basicMqttClientAuthProvider = mock(BasicMqttClientAuthProvider.class);
        SslMqttClientAuthProvider sslMqttClientAuthProvider = mock(SslMqttClientAuthProvider.class);
        mockGetActiveAuthProviders(basicMqttClientAuthProvider, sslMqttClientAuthProvider);

        authenticationService = spy(new DefaultAuthenticationService(mqttClientAuthProviderManager));
        authenticationService.setAuthStrategy(AuthStrategy.BOTH);

        when(basicMqttClientAuthProvider.authorize(any())).thenReturn(new AuthResponse(true, ClientType.DEVICE, null));

        AuthContext authContext = getAuthContext(sslHandler);
        AuthResponse authResponse = authenticationService.authenticate(authContext);
        Assert.assertTrue(authResponse.isSuccess());
        Assert.assertEquals(ClientType.DEVICE, authResponse.getClientType());
    }

    private void mockGetActiveAuthProviders(BasicMqttClientAuthProvider basicMqttClientAuthProvider,
                                            SslMqttClientAuthProvider sslMqttClientAuthProvider) {
        when(mqttClientAuthProviderManager.getActiveAuthProviders()).thenReturn(Map.of(
                AuthProviderType.BASIC, basicMqttClientAuthProvider,
                AuthProviderType.SSL, sslMqttClientAuthProvider
        ));
    }

    private AuthContext getAuthContext(SslHandler sslHandler) {
        return new AuthContext(
                "clientId",
                "userName",
                "password".getBytes(StandardCharsets.UTF_8),
                sslHandler);
    }
}