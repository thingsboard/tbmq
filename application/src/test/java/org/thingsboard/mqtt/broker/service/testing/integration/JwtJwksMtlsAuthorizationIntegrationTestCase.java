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
package org.thingsboard.mqtt.broker.service.testing.integration;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.shaded.org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.testcontainers.utility.MountableFile;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.credentials.CertPemCredentials;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwksVerifierConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.io.FileOutputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = JwtJwksMtlsAuthorizationIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class JwtJwksMtlsAuthorizationIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final String CLIENT_USERNAME = "test-jwks-with-mtls-creds-username";
    private static final String CLIENT_ID = "jwksMtlsAuthClientId";
    private static final String MY_TOPIC = "my/topic";
    private static final String TEST_TOPIC = "test/topic";

    @ClassRule
    public static final WireMockContainer wireMockServer;

    private static final RSAKey rsaJwk;
    private static final String jwksBodyEscapedStr;
    private static final String jwksPath = "/secure-with-mtls-jwks";

    private static final String clientCertPem;
    private static final String clientPrivateKeyPem;
    private static final String serverCertPem;

    static {
        try {
            rsaJwk = new RSAKeyGenerator(2048)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyID("test-key-id")
                    .generate();

            String jwksJsonStr = JacksonUtil.toString(new JWKSet(rsaJwk.toPublicJWK()).toJSONObject());
            jwksBodyEscapedStr = JacksonUtil.toString(jwksJsonStr);

            KeyPair clientKeyPair = generateKeyPair();
            X509Certificate clientCert = generateSelfSignedCert(clientKeyPair, "CN=TBMQ-Test-Client");
            clientCertPem = toPem(clientCert);
            clientPrivateKeyPem = toPem(clientKeyPair.getPrivate());

            KeyPair serverKeyPair = generateKeyPair();
            X509Certificate serverCert = generateSelfSignedCert(serverKeyPair, "CN=TBMQ-WireMock-Server");
            serverCertPem = toPem(serverCert);

            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null, null);
            keyStore.setKeyEntry("wiremock", serverKeyPair.getPrivate(), "password".toCharArray(), new Certificate[]{serverCert});

            File jksFile = File.createTempFile("wiremock", ".jks");
            try (FileOutputStream fos = new FileOutputStream(jksFile)) {
                keyStore.store(fos, "password".toCharArray());
            }

            MountableFile mountableFile = MountableFile.forHostPath(jksFile.getAbsolutePath());
            log.warn("WireMock server keystore: {}", jksFile.getAbsolutePath());

            wireMockServer = new WireMockContainer("wiremock/wiremock:3.13.1")
                    .withCopyFileToContainer(mountableFile, "/home/wiremock.jks")
                    .withCreateContainerCmdModifier(cmd -> {
                        cmd.withEntrypoint("/docker-entrypoint.sh");
                        cmd.withCmd("--https-port", "8443",
                                "--https-keystore", "/home/wiremock.jks",
                                "--keystore-password", "password");
                               // "--verbose"); useful for debugging, but not necessary for the test.
                        cmd.withExposedPorts(ExposedPort.tcp(8080), ExposedPort.tcp(8443));
                        Objects.requireNonNull(cmd.getHostConfig()).withPortBindings(
                                new PortBinding(Ports.Binding.empty(), new ExposedPort(8080)),
                                new PortBinding(Ports.Binding.bindPort(443), new ExposedPort(8443))
                        );
                    })
                    .withMappingFromJSON("""
                    {
                      "request": {
                        "method": "GET",
                        "url": "%s"
                      },
                      "response": {
                        "status": 200,
                        "headers": {
                          "Content-Type": "application/json"
                        },
                        "body": %s
                      }
                    }
                    """.formatted(jwksPath, jwksBodyEscapedStr))
                    .withLogConsumer(x -> log.warn("{}", x.getUtf8StringWithoutLineEnding()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize JWKS server with mTLS", e);
        }
    }

    private MqttAuthProvider jwtProvider;

    @Before
    public void beforeTest() throws Exception {
        super.beforeTest();

        JwksVerifierConfiguration config = new JwksVerifierConfiguration();
        config.setEndpoint("https://localhost" + jwksPath);

        CertPemCredentials creds = new CertPemCredentials();
        creds.setCert(clientCertPem);
        creds.setPrivateKey(clientPrivateKeyPem);
        creds.setCaCert(serverCertPem);
        config.setCredentials(creds);

        MqttAuthProvider provider = getMqttAuthProvider(MqttAuthProviderType.JWT);
        var configuration = (JwtMqttAuthProviderConfiguration) provider.getConfiguration();
        configuration.setJwtVerifierConfiguration(config);
        PubSubAuthorizationRules pubSubAuthorizationRules = new PubSubAuthorizationRules(
                List.of("test/.*"),
                List.of("my/.*")
        );
        provider.setEnabled(true);
        configuration.setAuthRules(pubSubAuthorizationRules);
        provider.setConfiguration(configuration);
        jwtProvider = mqttAuthProviderManagerService.saveAuthProvider(provider);
    }

    @After
    public void afterTest() {
        mqttAuthProviderManagerService.disableAuthProvider(jwtProvider.getId());
    }

    @Test
    public void givenClient_whenConnectWithoutPassword_thenConnectionRefusedNotAuthorized() throws Throwable {
        MqttClient client = getMqttClient();
        Throwable thrown = catchThrowable(client::connect);
        assertThat(thrown).isInstanceOf(MqttException.class);
        MqttException me = (MqttException) thrown;
        assertThat(me.getReasonCode()).isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED.byteValue());
        client.close();
    }

    @Test
    public void givenClient_whenConnectWithValidJwtPassword_thenConnectionAccepted() throws Throwable {
        String jwt = generateSignedJwt();
        MqttClient client = getMqttClient();
        client.connect(getMqttConnectOptions(jwt));
        assertThat(client.isConnected()).isTrue();
        client.disconnect();
        client.close();
    }

    @Test
    public void givenClient_whenPublishToCorrectTopic_thenSuccess() throws Throwable {
        String jwt = generateSignedJwt();
        MqttClient client = getMqttClient();
        client.connect(getMqttConnectOptions(jwt));
        assertThat(client.isConnected()).isTrue();
        client.publish(TEST_TOPIC, "msg".getBytes(StandardCharsets.UTF_8), 1, false);
        client.disconnect();
        client.close();
    }

    @Test
    public void givenClient_whenSubscribeToCorrectTopic_thenSuccess() throws Throwable {
        String jwt = generateSignedJwt();
        MqttClient client = getMqttClient();
        client.connect(getMqttConnectOptions(jwt));
        assertThat(client.isConnected()).isTrue();
        client.subscribe(MY_TOPIC);
        client.disconnect();
        client.close();
    }

    @Test(expected = MqttException.class)
    public void givenClient_whenPublishToWrongTopic_thenCloseChannelWithoutDisconnect() throws Throwable {
        String jwt = generateSignedJwt();
        MqttClient client = getMqttClient();
        client.connect(getMqttConnectOptions(jwt));
        assertThat(client.isConnected()).isTrue();
        client.publish(MY_TOPIC, "msg".getBytes(StandardCharsets.UTF_8), 1, false);
        client.close();
    }

    @Test(expected = MqttException.class)
    public void givenClient_whenSubscribeToWrongTopic_thenCloseChannelWithoutDisconnect() throws Throwable {
        String jwt = generateSignedJwt();
        MqttClient client = getMqttClient();
        client.connect(getMqttConnectOptions(jwt));
        assertThat(client.isConnected()).isTrue();
        client.subscribe(TEST_TOPIC);
        client.close();
    }

    private String generateSignedJwt() throws JOSEException {
        JWSSigner signer = new RSASSASigner(rsaJwk.toPrivateKey());
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(CLIENT_USERNAME)
                .expirationTime(new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1)))
                .build();

        JWSHeader jwsHeader = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaJwk.getKeyID())
                .build();
        SignedJWT signedJWT = new SignedJWT(jwsHeader, claimsSet);
        signedJWT.sign(signer);
        return signedJWT.serialize();
    }

    private MqttClient getMqttClient() throws MqttException {
        return new MqttClient(SERVER_URI + mqttPort, CLIENT_ID);
    }

    private MqttConnectOptions getMqttConnectOptions(String jwt) {
        var options = new MqttConnectOptions();
        options.setUserName(CLIENT_USERNAME);
        options.setPassword(jwt.toCharArray());
        return options;
    }

    private static KeyPair generateKeyPair() throws Exception {
        var keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair();
    }

    private static X509Certificate generateSelfSignedCert(KeyPair keyPair, String dn) throws Exception {
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now);
        Date notAfter = new Date(now + TimeUnit.DAYS.toMillis(365));

        X500Principal subject = new X500Principal(dn);
        BigInteger serial = BigInteger.valueOf(now);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .build(keyPair.getPrivate());

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject,
                serial,
                notBefore,
                notAfter,
                subject,
                keyPair.getPublic()
        );

        // Add SAN: DNS:localhost
        GeneralName san = new GeneralName(GeneralName.dNSName, "localhost");
        certBuilder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(san));

        X509CertificateHolder certHolder = certBuilder.build(signer);
        return new JcaX509CertificateConverter()
                .setProvider(new BouncyCastleProvider())
                .getCertificate(certHolder);
    }

    private static String toPem(Object obj) throws Exception {
        try (StringWriter sw = new StringWriter(); JcaPEMWriter writer = new JcaPEMWriter(sw)) {
            writer.writeObject(obj);
            writer.flush();
            return sw.toString();
        }
    }
}
