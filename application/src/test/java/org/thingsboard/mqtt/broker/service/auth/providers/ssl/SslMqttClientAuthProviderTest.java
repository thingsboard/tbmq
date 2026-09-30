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
package org.thingsboard.mqtt.broker.service.auth.providers.ssl;

import io.netty.handler.ssl.SslHandler;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.TbCacheOps;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ClientTypeSslMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.client.credentials.SslMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.security.ssl.SslMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.client.credentials.SslCredentialsCacheValue;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.dao.util.protocol.ProtocolUtil;
import org.thingsboard.mqtt.broker.server.MqttHandlerCtx;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthContext;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class SslMqttClientAuthProviderTest {

    private static final String CERT_CN = "shared-cert";

    private static X509Certificate certificate;

    @Mock
    private MqttClientCredentialsService clientCredentialsService;
    @Mock
    private AuthorizationRuleService authorizationRuleService;
    @Mock
    private MqttAuthProviderService mqttAuthProviderService;
    @Mock
    private MqttHandlerCtx mqttHandlerCtx;
    @Mock
    private TbCacheOps cacheOps;

    private SslMqttClientAuthProvider provider;

    @BeforeClass
    public static void generateCertificate() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        X500Name subject = new X500Name("CN=" + CERT_CN);
        long now = System.currentTimeMillis();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(subject, BigInteger.ONE,
                new Date(now - TimeUnit.DAYS.toMillis(1)), new Date(now + TimeUnit.DAYS.toMillis(1)), subject, keyPair.getPublic());
        certificate = new JcaX509CertificateConverter()
                .getCertificate(builder.build(new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate())));
    }

    @Before
    public void setUp() throws Exception {
        provider = new SslMqttClientAuthProvider(clientCredentialsService, authorizationRuleService,
                mqttAuthProviderService, mqttHandlerCtx, cacheOps);
        ReflectionTestUtils.setField(provider, "enabled", true);
        ReflectionTestUtils.setField(provider, "configuration", new SslMqttAuthProviderConfiguration());
        when(authorizationRuleService.parseSslAuthorizationRule(any(), eq(CERT_CN), any())).thenReturn(Collections.emptyList());
    }

    @Test
    public void givenRegexCredentialsMatchingCnButNotClientId_whenAuthenticate_thenNextMatchingCredentialsAreUsed() {
        mockRegexCredentials(
                regexCredentials("restricted", "device-42"),
                regexCredentials("fallback", null));

        AuthResponse response = provider.authenticate(authContext("device-43"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getAuthDetails()).isEqualTo("fallback");
    }

    @Test
    public void givenOnlyRegexCredentialsMatchingCnButNotClientId_whenAuthenticate_thenNotAuthenticated() {
        mockRegexCredentials(regexCredentials("restricted", "device-42"));
        when(clientCredentialsService.findMatchingCredentials(List.of(ProtocolUtil.sslCredentialsId(CERT_CN))))
                .thenReturn(Collections.emptyList());

        AuthResponse response = provider.authenticate(authContext("device-43"));

        assertThat(response.isSuccess()).isFalse();
    }

    @Test
    public void givenExactCnCredentialsWithMismatchingClientId_whenAuthenticate_thenNotAuthenticated() {
        mockRegexCredentials();
        mockExactCnCredentials("exact", "device-42");

        AuthResponse response = provider.authenticate(authContext("device-43"));

        assertThat(response.isSuccess()).isFalse();
    }

    @Test
    public void givenExactCnCredentialsWithMatchingClientId_whenAuthenticate_thenAuthenticated() {
        mockRegexCredentials();
        mockExactCnCredentials("exact", "device-42");

        AuthResponse response = provider.authenticate(authContext("device-42"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getAuthDetails()).isEqualTo("exact");
    }

    private AuthContext authContext(String clientId) {
        SSLSession session = mock(SSLSession.class);
        try {
            when(session.getPeerCertificates()).thenReturn(new X509Certificate[]{certificate});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        SSLEngine engine = mock(SSLEngine.class);
        when(engine.getSession()).thenReturn(session);
        SslHandler sslHandler = mock(SslHandler.class);
        when(sslHandler.engine()).thenReturn(engine);

        AuthContext authContext = mock(AuthContext.class);
        when(authContext.isSecurePortUsed()).thenReturn(true);
        when(authContext.getClientId()).thenReturn(clientId);
        when(authContext.getSslHandler()).thenReturn(sslHandler);
        return authContext;
    }

    private void mockRegexCredentials(ClientTypeSslMqttCredentials... credentials) {
        when(cacheOps.lookup(CacheConstants.SSL_REGEX_BASED_CREDENTIALS_CACHE, ClientCredentialsType.X_509, SslCredentialsCacheValue.class))
                .thenReturn(TbCacheOps.Lookup.hit(new SslCredentialsCacheValue(List.of(credentials))));
    }

    private void mockExactCnCredentials(String name, String clientIdPattern) {
        MqttClientCredentials credentials = new MqttClientCredentials();
        credentials.setName(name);
        credentials.setClientType(ClientType.DEVICE);
        credentials.setCredentialsType(ClientCredentialsType.X_509);
        credentials.setCredentialsValue(JacksonUtil.toString(sslCredentials(CERT_CN, false, clientIdPattern)));
        when(clientCredentialsService.findMatchingCredentials(List.of(ProtocolUtil.sslCredentialsId(CERT_CN))))
                .thenReturn(List.of(credentials));
    }

    private static ClientTypeSslMqttCredentials regexCredentials(String name, String clientIdPattern) {
        return new ClientTypeSslMqttCredentials(ClientType.DEVICE, sslCredentials("shared-.*", true, clientIdPattern), name);
    }

    private static SslMqttCredentials sslCredentials(String certCnPattern, boolean certCnIsRegex, String clientIdPattern) {
        return new SslMqttCredentials(certCnPattern, certCnIsRegex, clientIdPattern, false,
                Map.of(".*", PubSubAuthorizationRules.defaultInstance()));
    }

}
