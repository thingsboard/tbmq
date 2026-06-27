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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import redis.clients.jedis.JedisClientConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Sentinel client configuration built by {@link TBRedisSentinelConfiguration}.
 * <p>
 * Regression guard for GH#331: when {@code redis.ssl.enabled=true}, the connection to the Redis/Valkey
 * Sentinel itself must use TLS. Previously SSL was applied only to the master/data-node client config,
 * so {@code JedisSentineled} attempted the Sentinel handshake in plaintext against a TLS Sentinel and
 * failed at startup with "Could not get master address ... java.net.SocketException: Connection reset".
 */
public class TBRedisSentinelSslConfigurationTest {

    static String caCertPath;

    @BeforeAll
    static void generateCa() throws Exception {
        Path certsDir = Files.createTempDirectory("tbmq-redis-sentinel-ssl-test-");
        KeyPair caKeyPair = RedisTlsCertGenerator.generateKeyPair();
        X509Certificate caCert = RedisTlsCertGenerator.generateCaCert(caKeyPair);
        Path ca = certsDir.resolve("ca.pem");
        RedisTlsCertGenerator.writePem(ca, caCert);
        caCertPath = ca.toString();
    }

    private TBRedisSentinelConfiguration newConfig(boolean sslEnabled) {
        TBRedisSentinelConfiguration cfg = new TBRedisSentinelConfiguration(null, null);
        RedisSslCredentials creds = new RedisSslCredentials();
        creds.setCertFile(caCertPath);
        ReflectionTestUtils.setField(cfg, "redisSslCredentials", creds);
        ReflectionTestUtils.setField(cfg, "sslEnabled", sslEnabled);
        ReflectionTestUtils.setField(cfg, "sentinelUsername", "sentinel-user");
        ReflectionTestUtils.setField(cfg, "sentinelPassword", "sentinel-pass");
        return cfg;
    }

    @Test
    void givenSslEnabled_whenBuildingSentinelClientConfig_thenSslIsApplied() {
        JedisClientConfig sentinelConfig = newConfig(true).buildSentinelClientConfig();
        assertThat(sentinelConfig.isSsl()).isTrue();
        assertThat(sentinelConfig.getSslSocketFactory()).isNotNull();
    }

    @Test
    void givenSslEnabled_whenBuildingSentinelClientConfig_thenSentinelCredentialsApplied() {
        JedisClientConfig sentinelConfig = newConfig(true).buildSentinelClientConfig();
        assertThat(sentinelConfig.getUser()).isEqualTo("sentinel-user");
        assertThat(sentinelConfig.getPassword()).isEqualTo("sentinel-pass");
    }

    @Test
    void givenSslDisabled_whenBuildingSentinelClientConfig_thenNoSsl() {
        JedisClientConfig sentinelConfig = newConfig(false).buildSentinelClientConfig();
        assertThat(sentinelConfig.isSsl()).isFalse();
        assertThat(sentinelConfig.getSslSocketFactory()).isNull();
    }
}
