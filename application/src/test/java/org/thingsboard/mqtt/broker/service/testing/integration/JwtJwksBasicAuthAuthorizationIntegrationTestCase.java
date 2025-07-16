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
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.credentials.BasicCredentials;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwksVerifierConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = JwtJwksBasicAuthAuthorizationIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class JwtJwksBasicAuthAuthorizationIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final String CLIENT_USERNAME = "test-jwks-with-basic-creds-username";
    private static final String CLIENT_ID = "jwksBasicAuthClientId";
    private static final String MY_TOPIC = "my/topic";
    private static final String TEST_TOPIC = "test/topic";

    private static final String JWKS_USERNAME = "test-user";
    private static final String JWKS_PASSWORD = "test-pass";

    public static final String JWKS_WRONG_USERNAME = "wrong-user";
    public static final String JWKS_WRONG_PASSWORD = "wrong-pass";

    @ClassRule
    public static final WireMockContainer wireMockServer;

    private static final RSAKey rsaJwk;
    private static final String jwksBodyEscapedStr;
    private static final String jwksPath = "/secure-with-basic-jwks";

    static {
        try {
            rsaJwk = new RSAKeyGenerator(2048)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyID("test-key-id")
                    .generate();

            String jwksJsonStr = JacksonUtil.toString(new JWKSet(rsaJwk.toPublicJWK()).toJSONObject());
            jwksBodyEscapedStr = JacksonUtil.toString(jwksJsonStr);

            String correctAuthHeader = Base64.getEncoder().encodeToString((JWKS_USERNAME + ":" + JWKS_PASSWORD).getBytes(StandardCharsets.UTF_8));
            String invalidAuthHeader = Base64.getEncoder().encodeToString((JWKS_WRONG_USERNAME + ":" + JWKS_WRONG_PASSWORD).getBytes(StandardCharsets.UTF_8));

            // Mapping for correct credentials → 200 OK
            String successMappingJson = """
                {
                  "request": {
                    "method": "GET",
                    "url": "%s",
                    "headers": {
                      "Authorization": {
                        "equalTo": "Basic %s"
                      }
                    }
                  },
                  "response": {
                    "status": 200,
                    "headers": {
                      "Content-Type": "application/json"
                    },
                    "body": %s
                  }
                }
                """.formatted(jwksPath, correctAuthHeader, jwksBodyEscapedStr);

            // Fallback mapping → 401 Unauthorized for all other requests to the same path
            String unauthorizedMappingJson = """
                {
                  "request": {
                    "method": "GET",
                    "url": "%s",
                    "headers": {
                      "Authorization": {
                        "equalTo": "Basic %s"
                      }
                    }
                  },
                  "response": {
                    "status": 401,
                    "headers": {
                      "Content-Type": "text/plain"
                    },
                    "body": "Unauthorized"
                  }
                }
                """.formatted(jwksPath, invalidAuthHeader);

            wireMockServer = new WireMockContainer("wiremock/wiremock:3.13.1")
                    .withCreateContainerCmdModifier(cmd -> {
                        cmd.withEntrypoint("/docker-entrypoint.sh");
                        // cmd.withCmd("--verbose"); useful for debugging, but not necessary for the test.
                        cmd.withExposedPorts(ExposedPort.tcp(8080));
                        Objects.requireNonNull(cmd.getHostConfig()).withPortBindings(new PortBinding(Ports.Binding.empty(), new ExposedPort(8080)));
                    })
                    .withLogConsumer(x -> log.warn("{}", x.getUtf8StringWithoutLineEnding()))
                    .withMappingFromJSON(successMappingJson)
                    .withMappingFromJSON(unauthorizedMappingJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize the JWKS server with Basic Auth: ", e);
        }
    }

    private MqttAuthProvider jwtProvider;

    @Before
    public void beforeTest() throws Exception {
        super.beforeTest();

        JwksVerifierConfiguration config = new JwksVerifierConfiguration();
        config.setEndpoint(wireMockServer.getBaseUrl() + jwksPath);

        var creds = new BasicCredentials();
        creds.setUsername(JWKS_USERNAME);
        creds.setPassword(JWKS_PASSWORD);
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

    @Test
    public void givenInvalidJwksBasicCredentials_whenConnect_thenConnectionRefusedNotAuthorized() throws Throwable {
        // Reconfigure the auth provider with invalid JWKS credentials
        JwksVerifierConfiguration config = new JwksVerifierConfiguration();
        config.setEndpoint(wireMockServer.getBaseUrl() + jwksPath);

        var invalidCreds = new BasicCredentials();
        invalidCreds.setUsername(JWKS_WRONG_USERNAME);
        invalidCreds.setPassword(JWKS_WRONG_PASSWORD);
        config.setCredentials(invalidCreds);

        MqttAuthProvider provider = getMqttAuthProvider(MqttAuthProviderType.JWT);
        var configuration = (JwtMqttAuthProviderConfiguration) provider.getConfiguration();
        configuration.setJwtVerifierConfiguration(config);
        provider.setConfiguration(configuration);
        mqttAuthProviderManagerService.saveAuthProvider(provider);

        // Attempt to connect with valid JWT (but invalid JWKS config behind the scenes)
        String jwt = generateSignedJwt();
        MqttClient client = getMqttClient();
        Throwable thrown = catchThrowable(() -> client.connect(getMqttConnectOptions(jwt)));
        assertThat(thrown).isInstanceOf(MqttException.class);
        MqttException me = (MqttException) thrown;
        assertThat(me.getReasonCode()).isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED.byteValue());
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

}
