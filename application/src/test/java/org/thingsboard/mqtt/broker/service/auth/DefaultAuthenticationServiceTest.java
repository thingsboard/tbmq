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

import io.netty.handler.ssl.SslHandler;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.exception.AuthenticationException;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.service.auth.providers.BasicMqttClientAuthProvider;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttAuthProviderManager;
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

    MqttAuthProviderManager mqttAuthProviderManager;
    DefaultAuthenticationService authenticationService;

    @Test
    public void testAuthenticateNoProviders() throws AuthenticationException {
        mqttAuthProviderManager = mock(MqttAuthProviderManager.class);
        authenticationService = spy(new DefaultAuthenticationService(mqttAuthProviderManager));

        AuthContext authContext = getAuthContext(null);
        AuthResponse authResponse = authenticationService.authenticate(authContext);

        Assert.assertTrue(authResponse.isSuccess());
        Assert.assertEquals(ClientType.DEVICE, authResponse.getClientType());
        Assert.assertNull(authResponse.getAuthRulePatterns());
    }

    @Test
    public void testAuthenticateFailureUsingBasic() throws AuthenticationException {
        mqttAuthProviderManager = mock(MqttAuthProviderManager.class);
        BasicMqttClientAuthProvider basicMqttClientAuthProvider = mock(BasicMqttClientAuthProvider.class);
        SslMqttClientAuthProvider sslMqttClientAuthProvider = mock(SslMqttClientAuthProvider.class);
        mockGetActiveAuthProviders(basicMqttClientAuthProvider, sslMqttClientAuthProvider);

        authenticationService = spy(new DefaultAuthenticationService(mqttAuthProviderManager));
        authenticationService.setAuthStrategy(AuthStrategy.SINGLE);

        when(basicMqttClientAuthProvider.authenticate(any())).thenReturn(new AuthResponse(false, null, null));

        AuthContext authContext = getAuthContext(null);
        AuthResponse authResponse = authenticationService.authenticate(authContext);

        Assert.assertFalse(authResponse.isSuccess());
        Assert.assertNull(authResponse.getClientType());
        Assert.assertNull(authResponse.getAuthRulePatterns());
    }

    @Test
    public void testAuthenticateFailureUsingSsl() throws AuthenticationException {
        SslHandler sslHandler = mock(SslHandler.class);
        mqttAuthProviderManager = mock(MqttAuthProviderManager.class);
        BasicMqttClientAuthProvider basicMqttClientAuthProvider = mock(BasicMqttClientAuthProvider.class);
        SslMqttClientAuthProvider sslMqttClientAuthProvider = mock(SslMqttClientAuthProvider.class);
        mockGetActiveAuthProviders(basicMqttClientAuthProvider, sslMqttClientAuthProvider);

        authenticationService = spy(new DefaultAuthenticationService(mqttAuthProviderManager));
        authenticationService.setAuthStrategy(AuthStrategy.SINGLE);

        when(sslMqttClientAuthProvider.authenticate(any())).thenReturn(new AuthResponse(false, null, null));

        AuthContext authContext = getAuthContext(sslHandler);
        AuthResponse authResponse = authenticationService.authenticate(authContext);

        Assert.assertFalse(authResponse.isSuccess());
        Assert.assertNull(authResponse.getClientType());
        Assert.assertNull(authResponse.getAuthRulePatterns());
    }

    @Test
    public void testAuthenticateSuccessUsingSsl() throws AuthenticationException {
        SslHandler sslHandler = mock(SslHandler.class);
        mqttAuthProviderManager = mock(MqttAuthProviderManager.class);
        BasicMqttClientAuthProvider basicMqttClientAuthProvider = mock(BasicMqttClientAuthProvider.class);
        SslMqttClientAuthProvider sslMqttClientAuthProvider = mock(SslMqttClientAuthProvider.class);
        mockGetActiveAuthProviders(basicMqttClientAuthProvider, sslMqttClientAuthProvider);

        authenticationService = spy(new DefaultAuthenticationService(mqttAuthProviderManager));
        authenticationService.setAuthStrategy(AuthStrategy.SINGLE);

        when(sslMqttClientAuthProvider.authenticate(any())).thenReturn(new AuthResponse(true, ClientType.APPLICATION, null));

        AuthContext authContext = getAuthContext(sslHandler);
        AuthResponse authResponse = authenticationService.authenticate(authContext);
        Assert.assertTrue(authResponse.isSuccess());
        Assert.assertEquals(ClientType.APPLICATION, authResponse.getClientType());
        Assert.assertNull(authResponse.getAuthRulePatterns());
    }

    @Test
    public void testAuthenticateSuccessUsingBasic() throws AuthenticationException {
        mqttAuthProviderManager = mock(MqttAuthProviderManager.class);
        BasicMqttClientAuthProvider basicMqttClientAuthProvider = mock(BasicMqttClientAuthProvider.class);
        SslMqttClientAuthProvider sslMqttClientAuthProvider = mock(SslMqttClientAuthProvider.class);
        mockGetActiveAuthProviders(basicMqttClientAuthProvider, sslMqttClientAuthProvider);

        authenticationService = spy(new DefaultAuthenticationService(mqttAuthProviderManager));
        authenticationService.setAuthStrategy(AuthStrategy.SINGLE);

        when(basicMqttClientAuthProvider.authenticate(any())).thenReturn(new AuthResponse(true, ClientType.DEVICE, null));

        AuthContext authContext = getAuthContext(null);
        AuthResponse authResponse = authenticationService.authenticate(authContext);
        Assert.assertTrue(authResponse.isSuccess());
        Assert.assertEquals(ClientType.DEVICE, authResponse.getClientType());
    }

    @Test
    public void testAuthenticateSuccessUsingBasicWhenBothAuthStrategySet() throws AuthenticationException {
        mqttAuthProviderManager = mock(MqttAuthProviderManager.class);
        BasicMqttClientAuthProvider basicMqttClientAuthProvider = mock(BasicMqttClientAuthProvider.class);
        SslMqttClientAuthProvider sslMqttClientAuthProvider = mock(SslMqttClientAuthProvider.class);
        mockGetActiveAuthProviders(basicMqttClientAuthProvider, sslMqttClientAuthProvider);

        authenticationService = spy(new DefaultAuthenticationService(mqttAuthProviderManager));
        authenticationService.setAuthStrategy(AuthStrategy.BOTH);

        when(basicMqttClientAuthProvider.authenticate(any())).thenReturn(new AuthResponse(true, ClientType.DEVICE, null));

        AuthContext authContext = getAuthContext(null);
        AuthResponse authResponse = authenticationService.authenticate(authContext);
        Assert.assertTrue(authResponse.isSuccess());
        Assert.assertEquals(ClientType.DEVICE, authResponse.getClientType());
    }

    @Test
    public void testAuthenticateSuccessUsingSslWhenBothAuthStrategySet() throws AuthenticationException {
        SslHandler sslHandler = mock(SslHandler.class);
        mqttAuthProviderManager = mock(MqttAuthProviderManager.class);
        BasicMqttClientAuthProvider basicMqttClientAuthProvider = mock(BasicMqttClientAuthProvider.class);
        SslMqttClientAuthProvider sslMqttClientAuthProvider = mock(SslMqttClientAuthProvider.class);
        mockGetActiveAuthProviders(basicMqttClientAuthProvider, sslMqttClientAuthProvider);

        authenticationService = spy(new DefaultAuthenticationService(mqttAuthProviderManager));
        authenticationService.setAuthStrategy(AuthStrategy.BOTH);

        when(basicMqttClientAuthProvider.authenticate(any())).thenReturn(new AuthResponse(false, ClientType.DEVICE, null));
        when(sslMqttClientAuthProvider.authenticate(any())).thenReturn(new AuthResponse(true, ClientType.APPLICATION, null));

        AuthContext authContext = getAuthContext(sslHandler);
        AuthResponse authResponse = authenticationService.authenticate(authContext);
        Assert.assertTrue(authResponse.isSuccess());
        Assert.assertEquals(ClientType.APPLICATION, authResponse.getClientType());
    }

    @Test
    public void testAuthenticateSuccessUsingBasicWhenBothAuthStrategySetAndSslContextUsed() throws AuthenticationException {
        SslHandler sslHandler = mock(SslHandler.class);
        mqttAuthProviderManager = mock(MqttAuthProviderManager.class);
        BasicMqttClientAuthProvider basicMqttClientAuthProvider = mock(BasicMqttClientAuthProvider.class);
        SslMqttClientAuthProvider sslMqttClientAuthProvider = mock(SslMqttClientAuthProvider.class);
        mockGetActiveAuthProviders(basicMqttClientAuthProvider, sslMqttClientAuthProvider);

        authenticationService = spy(new DefaultAuthenticationService(mqttAuthProviderManager));
        authenticationService.setAuthStrategy(AuthStrategy.BOTH);

        when(basicMqttClientAuthProvider.authenticate(any())).thenReturn(new AuthResponse(true, ClientType.DEVICE, null));

        AuthContext authContext = getAuthContext(sslHandler);
        AuthResponse authResponse = authenticationService.authenticate(authContext);
        Assert.assertTrue(authResponse.isSuccess());
        Assert.assertEquals(ClientType.DEVICE, authResponse.getClientType());
    }

    private void mockGetActiveAuthProviders(BasicMqttClientAuthProvider basicMqttClientAuthProvider,
                                            SslMqttClientAuthProvider sslMqttClientAuthProvider) {
        when(mqttAuthProviderManager.getActiveAuthProviders()).thenReturn(Map.of(
                MqttAuthProviderType.BASIC, basicMqttClientAuthProvider,
                MqttAuthProviderType.X_509, sslMqttClientAuthProvider
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
