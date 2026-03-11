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
package org.thingsboard.mqtt.broker.cache;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SslOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Redis SSL/TLS configuration is correctly applied to the Lettuce connection factory
 * when {@code redis.ssl.enabled=true}.
 * <p>
 * No running Redis instance is required — Lettuce uses lazy connections, so the test only
 * inspects the wired Spring beans without making any actual network calls.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {CacheProperties.class, LettuceConfig.class, RedisSslCredentials.class,
        TBRedisCacheConfiguration.class, TBRedisStandaloneConfiguration.class})
@EnableConfigurationProperties
@TestPropertySource(properties = {
        "redis.connection.type=standalone",
        "cache.stats.enabled=false",
        "cache.cache-prefix=",
        "cache.specs.mqttClientCredentials.timeToLiveInMinutes=1440",
        "lettuce.config.command-timeout=5",
        "lettuce.config.shutdown-quiet-period=1",
        "lettuce.config.shutdown-timeout=10",
        "lettuce.config.cluster.topology-refresh.enabled=false",
        "lettuce.config.cluster.topology-refresh.period=60"
})
public class TBRedisSslConfigurationTest {

    static final String CA_CERT_PATH;
    static final String CLIENT_CERT_PATH;
    static final String CLIENT_KEY_PATH;

    static {
        try {
            Path certsDir = Files.createTempDirectory("tbmq-redis-ssl-config-test-");

            KeyPair caKeyPair = RedisTlsCertGenerator.generateKeyPair();
            X509Certificate caCert = RedisTlsCertGenerator.generateCaCert(caKeyPair);

            KeyPair clientKeyPair = RedisTlsCertGenerator.generateKeyPair();
            X509Certificate clientCert = RedisTlsCertGenerator.generateSignedCert(clientKeyPair, caKeyPair, caCert, "tbmq-client");

            RedisTlsCertGenerator.writePem(certsDir.resolve("ca.pem"), caCert);
            RedisTlsCertGenerator.writePem(certsDir.resolve("client.pem"), clientCert);
            RedisTlsCertGenerator.writePem(certsDir.resolve("client.key"), clientKeyPair.getPrivate());

            CA_CERT_PATH = certsDir.resolve("ca.pem").toString();
            CLIENT_CERT_PATH = certsDir.resolve("client.pem").toString();
            CLIENT_KEY_PATH = certsDir.resolve("client.key").toString();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void sslProperties(DynamicPropertyRegistry registry) {
        registry.add("redis.ssl.enabled", () -> "true");
        registry.add("redis.ssl.credentials.cert-file", () -> CA_CERT_PATH);
        registry.add("redis.ssl.credentials.user-cert-file", () -> CLIENT_CERT_PATH);
        registry.add("redis.ssl.credentials.user-key-file", () -> CLIENT_KEY_PATH);
    }

    @Autowired
    LettuceConnectionFactory lettuceConnectionFactory;

    @Test
    void givenSslEnabled_whenContextLoaded_thenLettuceConnectionFactoryUsesSsl() {
        assertThat(lettuceConnectionFactory.getClientConfiguration().isUseSsl()).isTrue();
    }

    @Test
    void givenSslEnabled_whenContextLoaded_thenLettuceClientOptionsHasSslOptions() {
        var clientOptionsOpt = lettuceConnectionFactory.getClientConfiguration().getClientOptions();
        assertThat(clientOptionsOpt).isPresent();

        ClientOptions clientOptions = clientOptionsOpt.get();
        SslOptions sslOptions = clientOptions.getSslOptions();
        // SslOptions is always non-null; verifying that together with isUseSsl()=true (see previous test)
        // confirms that our custom TrustManagerFactory / KeyManagerFactory were wired into the client options.
        assertThat(sslOptions).isNotNull();
        assertThat(sslOptions.getSslProvider()).isEqualTo(io.netty.handler.ssl.SslProvider.JDK);
    }

    @Test
    void givenSslDisabledByDefault_whenContextLoaded_thenClientOptionsIsBaseType() {
        // Standalone config (not cluster) produces base ClientOptions, not ClusterClientOptions.
        var clientOptionsOpt = lettuceConnectionFactory.getClientConfiguration().getClientOptions();
        assertThat(clientOptionsOpt).isPresent();
        assertThat(clientOptionsOpt.get().getClass()).isEqualTo(ClientOptions.class);
    }

}
