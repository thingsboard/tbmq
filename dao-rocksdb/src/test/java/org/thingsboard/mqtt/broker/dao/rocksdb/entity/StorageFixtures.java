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
package org.thingsboard.mqtt.broker.dao.rocksdb.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.GenericClientSessionCtx;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.credentials.AnonymousCredentials;
import org.thingsboard.mqtt.broker.common.data.credentials.BasicCredentials;
import org.thingsboard.mqtt.broker.common.data.credentials.CertPemCredentials;
import org.thingsboard.mqtt.broker.common.data.credentials.ClientCredentials;
import org.thingsboard.mqtt.broker.common.data.dto.SubscriptionOptionsDto;
import org.thingsboard.mqtt.broker.common.data.props.UserProperties;
import org.thingsboard.mqtt.broker.common.data.security.Authority;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.common.data.security.basic.BasicMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.http.HttpMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.AlgorithmBasedVerifierConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.HmacBasedAlgorithmConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwksVerifierConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtSignAlgorithmConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtVerifierConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.PemKeyAlgorithmConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.scram.ScramMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.ssl.MqttClientAuthType;
import org.thingsboard.mqtt.broker.common.data.security.ssl.SslMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.ws.DataType;
import org.thingsboard.mqtt.broker.common.data.ws.LastWillMsg;
import org.thingsboard.mqtt.broker.common.data.ws.SizeUnit;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnectionConfiguration;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscription;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscriptionConfiguration;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * One filled instance of every type the entity store keeps, and of every polymorphic subtype the stored JSON holds.
 * The keys are the names of the golden files under {@code storage-golden}. Every declared field is set to a value
 * that is neither null nor a default, secrets included; {@link StorageObjectMapperTest} fails when a field is added to
 * a type and not filled here.
 */
final class StorageFixtures {

    static final long CREATED_TIME = 1_700_000_000_123L;

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String BASIC_CREDENTIALS_VALUE = "{\"clientId\":\"client-1\",\"userName\":\"user-1\","
            + "\"password\":\"basic-secret\",\"authRules\":{\"pubAuthRulePatterns\":[\"pub/.*\"],\"subAuthRulePatterns\":[\"sub/.*\"]}}";
    private static final String X509_CREDENTIALS_VALUE = "{\"certCnPattern\":\"device-.*\",\"certCnIsRegex\":true,"
            + "\"clientIdPattern\":\"dev-.*\",\"clientIdIsRegex\":true,"
            + "\"authRulesMapping\":{\"device-.*\":{\"pubAuthRulePatterns\":[\"pub/.*\"],\"subAuthRulePatterns\":[\"sub/.*\"]}}}";
    private static final String SCRAM_CREDENTIALS_VALUE = "{\"userName\":\"scram-user\",\"salt\":\"c2FsdC1zYWx0LXNhbHQ=\","
            + "\"serverKey\":\"c2VydmVyLWtleQ==\",\"storedKey\":\"c3RvcmVkLWtleQ==\",\"algorithm\":\"SHA_256\","
            + "\"authRules\":{\"pubAuthRulePatterns\":[\"pub/.*\"],\"subAuthRulePatterns\":[\"sub/.*\"]}}";

    private StorageFixtures() {
    }

    static Map<String, Object> everyStoredDomainType() {
        Map<String, Object> all = new LinkedHashMap<>();
        all.put("user", user());
        all.put("user-credentials", userCredentials());
        all.put("admin-settings", adminSettings());
        all.put("auth-provider-basic", authProvider(MqttAuthProviderType.MQTT_BASIC, basicConfiguration()));
        all.put("auth-provider-x509", authProvider(MqttAuthProviderType.X_509, sslConfiguration()));
        all.put("auth-provider-jwt-hmac", authProvider(MqttAuthProviderType.JWT, jwtConfiguration(algorithmVerifier(hmac()))));
        all.put("auth-provider-jwt-pem", authProvider(MqttAuthProviderType.JWT, jwtConfiguration(algorithmVerifier(pemKey()))));
        all.put("auth-provider-jwt-jwks", authProvider(MqttAuthProviderType.JWT, jwtConfiguration(jwksVerifier())));
        all.put("auth-provider-scram", authProvider(MqttAuthProviderType.SCRAM, new ScramMqttAuthProviderConfiguration()));
        all.put("auth-provider-http-anonymous", authProvider(MqttAuthProviderType.HTTP, httpConfiguration(new AnonymousCredentials())));
        all.put("auth-provider-http-basic", authProvider(MqttAuthProviderType.HTTP, httpConfiguration(basicCredentials())));
        all.put("auth-provider-http-cert-pem", authProvider(MqttAuthProviderType.HTTP, httpConfiguration(certPemCredentials())));
        all.put("client-credentials-basic", clientCredentials(ClientCredentialsType.MQTT_BASIC, "client-1|user-1", BASIC_CREDENTIALS_VALUE));
        all.put("client-credentials-x509", clientCredentials(ClientCredentialsType.X_509, "device-.*", X509_CREDENTIALS_VALUE));
        all.put("client-credentials-scram", clientCredentials(ClientCredentialsType.SCRAM, "scram-user", SCRAM_CREDENTIALS_VALUE));
        all.put("unauthorized-client", unauthorizedClient());
        all.put("websocket-connection", webSocketConnection());
        all.put("websocket-subscription", webSocketSubscription());
        all.put("generic-client-session-ctx", genericClientSessionCtx());
        return all;
    }

    static UUID uuid(long n) {
        return new UUID(0x1234_5678_9ABC_DEF0L, n);
    }

    static AdminSettings adminSettings() {
        AdminSettings settings = new AdminSettings(uuid(3));
        settings.setCreatedTime(CREATED_TIME);
        settings.setKey("mail");
        settings.setJsonValue(json("{\"smtpHost\":\"localhost\",\"smtpPort\":25,\"password\":\"mail-secret\",\"tls\":true}"));
        return settings;
    }

    private static User user() {
        User user = new User(uuid(1));
        user.setCreatedTime(CREATED_TIME);
        user.setEmail("admin@example.org");
        user.setAuthority(Authority.SYS_ADMIN);
        user.setFirstName("Ada");
        user.setLastName("Admin");
        user.setAdditionalInfo(json("{\"lang\":\"en\",\"nested\":{\"a\":1}}"));
        return user;
    }

    private static UserCredentials userCredentials() {
        UserCredentials credentials = new UserCredentials(uuid(2));
        credentials.setCreatedTime(CREATED_TIME);
        credentials.setUserId(uuid(1));
        credentials.setEnabled(true);
        credentials.setPassword("$2a$10$hashedpasswordhashedpasswordhashedpasswordhashedpass");
        credentials.setActivateToken("activate-token-secret");
        credentials.setResetToken("reset-token-secret");
        return credentials;
    }

    private static MqttAuthProvider authProvider(MqttAuthProviderType type, MqttAuthProviderConfiguration configuration) {
        MqttAuthProvider provider = new MqttAuthProvider();
        provider.setId(uuid(10 + type.ordinal()));
        provider.setCreatedTime(CREATED_TIME);
        provider.setEnabled(true);
        provider.setType(type);
        provider.setConfiguration(configuration);
        provider.setAdditionalInfo(json("{\"description\":\"" + type + " provider\"}"));
        return provider;
    }

    private static BasicMqttAuthProviderConfiguration basicConfiguration() {
        BasicMqttAuthProviderConfiguration configuration = new BasicMqttAuthProviderConfiguration();
        configuration.setAuthStrategy(BasicMqttAuthProviderConfiguration.AuthStrategy.USERNAME);
        return configuration;
    }

    private static SslMqttAuthProviderConfiguration sslConfiguration() {
        return new SslMqttAuthProviderConfiguration(true, MqttClientAuthType.CLIENT_AUTH_REQUIRED);
    }

    private static JwtMqttAuthProviderConfiguration jwtConfiguration(JwtVerifierConfiguration verifier) {
        JwtMqttAuthProviderConfiguration configuration = new JwtMqttAuthProviderConfiguration();
        configuration.setDefaultClientType(ClientType.APPLICATION);
        configuration.setJwtVerifierConfiguration(verifier);
        configuration.setAuthClaims(Map.of("sub", "${username}"));
        configuration.setClientTypeClaims(Map.of("kind", "APPLICATION"));
        configuration.setAuthRules(rules());
        configuration.setPubAuthRuleClaim("pubRules");
        configuration.setSubAuthRuleClaim("subRules");
        return configuration;
    }

    private static AlgorithmBasedVerifierConfiguration algorithmVerifier(JwtSignAlgorithmConfiguration algorithm) {
        AlgorithmBasedVerifierConfiguration verifier = new AlgorithmBasedVerifierConfiguration();
        verifier.setJwtSignAlgorithmConfiguration(algorithm);
        return verifier;
    }

    private static HmacBasedAlgorithmConfiguration hmac() {
        HmacBasedAlgorithmConfiguration hmac = new HmacBasedAlgorithmConfiguration();
        hmac.setSecret("hmac-secret-hmac-secret-hmac-secret-32");
        return hmac;
    }

    private static PemKeyAlgorithmConfiguration pemKey() {
        PemKeyAlgorithmConfiguration pem = new PemKeyAlgorithmConfiguration();
        pem.setPublicPemKeyFileName("public.pem");
        pem.setPublicPemKey("-----BEGIN PUBLIC KEY-----\nMFkwEwYH\n-----END PUBLIC KEY-----\n");
        return pem;
    }

    private static JwksVerifierConfiguration jwksVerifier() {
        JwksVerifierConfiguration jwks = new JwksVerifierConfiguration();
        jwks.setEndpoint("https://idp.example.org/jwks");
        jwks.setHeaders(Map.of("X-Api", "jwks-header"));
        jwks.setRefreshInterval(600);
        jwks.setCredentials(basicCredentials());
        return jwks;
    }

    private static HttpMqttAuthProviderConfiguration httpConfiguration(ClientCredentials credentials) {
        return HttpMqttAuthProviderConfiguration.builder()
                .restEndpointUrl("https://auth.example.org/check")
                .requestMethod("PUT")
                .credentials(credentials)
                .headers(Map.of("X-Token", "http-header"))
                .requestBody("{\"clientId\":\"${clientId}\"}")
                .readTimeoutMs(1234)
                .maxParallelRequestsCount(7)
                .maxInMemoryBufferSizeInKb(512)
                .defaultClientType(ClientType.APPLICATION)
                .authRules(rules())
                .build();
    }

    private static BasicCredentials basicCredentials() {
        BasicCredentials credentials = new BasicCredentials();
        credentials.setUsername("http-user");
        credentials.setPassword("http-password-secret");
        return credentials;
    }

    private static CertPemCredentials certPemCredentials() {
        CertPemCredentials credentials = new CertPemCredentials();
        credentials.setCaCert("-----BEGIN CERTIFICATE-----\nCA\n-----END CERTIFICATE-----\n");
        credentials.setCert("-----BEGIN CERTIFICATE-----\nCERT\n-----END CERTIFICATE-----\n");
        credentials.setPrivateKey("-----BEGIN PRIVATE KEY-----\nKEY\n-----END PRIVATE KEY-----\n");
        credentials.setPrivateKeyPassword("key-password-secret");
        return credentials;
    }

    private static PubSubAuthorizationRules rules() {
        return new PubSubAuthorizationRules(List.of("pub/.*"), List.of("sub/.*", "shared/.*"));
    }

    private static MqttClientCredentials clientCredentials(ClientCredentialsType type, String credentialsId, String value) {
        MqttClientCredentials credentials = new MqttClientCredentials(uuid(20 + type.ordinal()));
        credentials.setCreatedTime(CREATED_TIME);
        credentials.setCredentialsId(credentialsId);
        credentials.setName("credentials " + type);
        credentials.setClientType(ClientType.APPLICATION);
        credentials.setCredentialsType(type);
        credentials.setCredentialsValue(value);
        credentials.setAdditionalInfo(json("{\"description\":\"" + type + " credentials\"}"));
        return credentials;
    }

    private static UnauthorizedClient unauthorizedClient() {
        return UnauthorizedClient.builder()
                .clientId("unauthorized-1")
                .ipAddress("10.0.0.7")
                .ts(CREATED_TIME)
                .username("intruder")
                .passwordProvided(true)
                .tlsUsed(true)
                .reason("bad password")
                .build();
    }

    private static WebSocketConnection webSocketConnection() {
        WebSocketConnection connection = new WebSocketConnection();
        connection.setId(uuid(30));
        connection.setCreatedTime(CREATED_TIME);
        connection.setName("connection 1");
        connection.setUserId(uuid(1));
        connection.setConfiguration(webSocketConnectionConfiguration());
        return connection;
    }

    private static WebSocketConnectionConfiguration webSocketConnectionConfiguration() {
        WebSocketConnectionConfiguration configuration = new WebSocketConnectionConfiguration("ws-client-1");
        configuration.setUrl("wss://broker.example.org:8084/mqtt");
        configuration.setRejectUnauthorized(true);
        configuration.setClientCredentialsId(uuid(20));
        configuration.setUsername("ws-user");
        configuration.setPasswordRequired(true);
        configuration.setCleanStart(true);
        configuration.setKeepAlive(60);
        configuration.setKeepAliveUnit(TimeUnit.SECONDS);
        configuration.setConnectTimeout(30);
        configuration.setConnectTimeoutUnit(TimeUnit.SECONDS);
        configuration.setReconnectPeriod(2);
        configuration.setReconnectPeriodUnit(TimeUnit.MINUTES);
        configuration.setMqttVersion(5);
        configuration.setSessionExpiryInterval(3);
        configuration.setSessionExpiryIntervalUnit(TimeUnit.HOURS);
        configuration.setMaxPacketSize(4);
        configuration.setMaxPacketSizeUnit(SizeUnit.KILOBYTE);
        configuration.setTopicAliasMax(5);
        configuration.setReceiveMax(6);
        configuration.setRequestResponseInfo(true);
        configuration.setRequestProblemInfo(true);
        LastWillMsg will = new LastWillMsg();
        will.setTopic("will/topic");
        will.setQos(1);
        will.setPayloadType(DataType.JSON);
        will.setPayload("{\"gone\":true}");
        will.setRetain(true);
        will.setPayloadFormatIndicator(true);
        will.setContentType("application/json");
        will.setWillDelayInterval(7);
        will.setWillDelayIntervalUnit(TimeUnit.SECONDS);
        will.setMsgExpiryInterval(8);
        will.setMsgExpiryIntervalUnit(TimeUnit.MINUTES);
        will.setResponseTopic("will/response");
        will.setCorrelationData("correlation");
        configuration.setLastWillMsg(will);
        configuration.setUserProperties(new UserProperties(List.of(new UserProperties.StringPair("k1", "v1"),
                new UserProperties.StringPair("k2", "v2"))));
        return configuration;
    }

    private static WebSocketSubscription webSocketSubscription() {
        WebSocketSubscription subscription = new WebSocketSubscription();
        subscription.setId(uuid(40));
        subscription.setCreatedTime(CREATED_TIME);
        subscription.setWebSocketConnectionId(uuid(30));
        subscription.setConfiguration(new WebSocketSubscriptionConfiguration("sensors/#", 2, "#ff0000",
                new SubscriptionOptionsDto(true, true, 2), 77));
        return subscription;
    }

    private static GenericClientSessionCtx genericClientSessionCtx() {
        return GenericClientSessionCtx.builder()
                .clientId("session-client-1")
                .lastUpdatedTime(CREATED_TIME)
                .qos2PublishPacketIds(new HashSet<>(List.of(5, 9)))
                .build();
    }

    private static JsonNode json(String text) {
        try {
            return JSON.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

}
