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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that verifies TBMQ can connect to a TLS-enabled Redis/Valkey instance.
 * <p>
 * A Valkey container is started via Testcontainers with mTLS enforced. On-the-fly PKI artifacts
 * (CA, server cert, client cert) are generated using BouncyCastle. Both the Lettuce and Jedis
 * connection paths are exercised.
 * <p>
 * Requires Docker to be available at runtime.
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
public class TBRedisTlsConnectionTest {

    static final Path CERTS_DIR;
    static final GenericContainer<?> VALKEY;

    static {
        try {
            // Create the temp directory with world-readable/executable permissions so the Docker
            // container process (running as a different uid) can access the mounted cert files.
            CERTS_DIR = Files.createTempDirectory("tbmq-redis-tls-conn-test-",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x")));

            KeyPair caKeyPair = RedisTlsCertGenerator.generateKeyPair();
            X509Certificate caCert = RedisTlsCertGenerator.generateCaCert(caKeyPair);

            // Server cert must have SAN for localhost so the TLS handshake hostname check passes.
            KeyPair serverKeyPair = RedisTlsCertGenerator.generateKeyPair();
            X509Certificate serverCert = RedisTlsCertGenerator.generateSignedCert(serverKeyPair, caKeyPair, caCert, "localhost", true);

            KeyPair clientKeyPair = RedisTlsCertGenerator.generateKeyPair();
            X509Certificate clientCert = RedisTlsCertGenerator.generateSignedCert(clientKeyPair, caKeyPair, caCert, "tbmq-client");

            RedisTlsCertGenerator.writePem(CERTS_DIR.resolve("ca.pem"), caCert);
            RedisTlsCertGenerator.writePem(CERTS_DIR.resolve("server.pem"), serverCert);
            RedisTlsCertGenerator.writePem(CERTS_DIR.resolve("server.key"), serverKeyPair.getPrivate());
            RedisTlsCertGenerator.writePem(CERTS_DIR.resolve("client.pem"), clientCert);
            RedisTlsCertGenerator.writePem(CERTS_DIR.resolve("client.key"), clientKeyPair.getPrivate());

            // All cert/key files must be world-readable so the Valkey container process can load them.
            for (String name : new String[]{"ca.pem", "server.pem", "server.key", "client.pem", "client.key"}) {
                Files.setPosixFilePermissions(CERTS_DIR.resolve(name),
                        PosixFilePermissions.fromString("rw-r--r--"));
            }

            VALKEY = new GenericContainer<>("valkey/valkey:8.0")
                    .withFileSystemBind(CERTS_DIR.toString(), "/tls", BindMode.READ_ONLY)
                    .withCommand("valkey-server",
                            "--tls-port", "6379",
                            "--port", "0",
                            "--tls-cert-file", "/tls/server.pem",
                            "--tls-key-file", "/tls/server.key",
                            "--tls-ca-cert-file", "/tls/ca.pem",
                            "--tls-auth-clients", "yes")
                    .withExposedPorts(6379)
                    .waitingFor(Wait.forLogMessage(".*Ready to accept connections tls.*", 1));
            VALKEY.start();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("redis.standalone.host", VALKEY::getHost);
        registry.add("redis.standalone.port", () -> VALKEY.getMappedPort(6379));
        registry.add("redis.ssl.enabled", () -> "true");
        registry.add("redis.ssl.credentials.cert-file", () -> CERTS_DIR.resolve("ca.pem").toString());
        registry.add("redis.ssl.credentials.user-cert-file", () -> CERTS_DIR.resolve("client.pem").toString());
        registry.add("redis.ssl.credentials.user-key-file", () -> CERTS_DIR.resolve("client.key").toString());
    }

    @Autowired
    LettuceConnectionFactory lettuceConnectionFactory;

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    @Test
    void givenTlsEnabledValkey_whenPingViaLettuce_thenReturnsPong() {
        try (RedisConnection connection = lettuceConnectionFactory.getConnection()) {
            assertThat(connection.ping()).isEqualToIgnoringCase("PONG");
        }
    }

    @Test
    void givenTlsEnabledValkey_whenReadWriteViaJedis_thenSucceeds() {
        String testKey = "tbmq:tls:probe";
        redisTemplate.opsForValue().set(testKey, "ok");
        assertThat(redisTemplate.opsForValue().get(testKey)).isEqualTo("ok");
        redisTemplate.delete(testKey);
    }

}
