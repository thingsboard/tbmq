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
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;
import redis.clients.jedis.JedisClientConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared support for the no-Docker Redis configuration tests. Generates a throwaway CA once,
 * exposes a {@link RedisSslCredentials} pointing at it, builds a ready-to-use {@link LettuceConfig},
 * and provides assertion helpers for the Jedis client config produced by each deployment.
 * <p>
 * Tests instantiate the concrete {@code TBRedis*Configuration} directly and set {@code @Value}
 * fields via reflection — no Spring context — because {@code JedisCluster}/{@code JedisSentineled}
 * connect eagerly at construction and cannot be built without a live server (see GH#331).
 */
abstract class AbstractTBRedisConfigurationTest {

    static String caCertPath;

    @BeforeAll
    static void generateCa() throws Exception {
        Path certsDir = Files.createTempDirectory("tbmq-redis-config-test-");
        KeyPair caKeyPair = RedisTlsCertGenerator.generateKeyPair();
        X509Certificate caCert = RedisTlsCertGenerator.generateCaCert(caKeyPair);
        Path ca = certsDir.resolve("ca.pem");
        RedisTlsCertGenerator.writePem(ca, caCert);
        caCertPath = ca.toString();
    }

    static RedisSslCredentials sslCredentials() {
        RedisSslCredentials creds = new RedisSslCredentials();
        creds.setCertFile(caCertPath);
        return creds;
    }

    static LettuceConfig lettuceConfig() {
        LettuceConfig config = new LettuceConfig();
        config.setCommandTimeout(5);
        config.setShutdownQuietPeriod(1);
        config.setShutdownTimeout(10);
        LettuceConfig.ClusterConfig cluster = new LettuceConfig.ClusterConfig();
        LettuceConfig.ClusterConfig.LettuceTopologyRefreshConfig refresh =
                new LettuceConfig.ClusterConfig.LettuceTopologyRefreshConfig();
        refresh.setEnabled(false);
        refresh.setPeriod(60);
        cluster.setTopologyRefresh(refresh);
        config.setCluster(cluster);
        return config;
    }

    /** Sets the SSL-related fields shared by every {@code TBRedis*Configuration}. */
    static void setSslFields(TBRedisCacheConfiguration<?> config, boolean sslEnabled) {
        ReflectionTestUtils.setField(config, "sslEnabled", sslEnabled);
        ReflectionTestUtils.setField(config, "redisSslCredentials", sslCredentials());
    }

    static void assertTls(JedisClientConfig clientConfig) {
        assertThat(clientConfig.isSsl()).isTrue();
        assertThat(clientConfig.getSslSocketFactory()).isNotNull();
    }

    static void assertNoTls(JedisClientConfig clientConfig) {
        assertThat(clientConfig.isSsl()).isFalse();
        assertThat(clientConfig.getSslSocketFactory()).isNull();
    }

    static void assertCredentials(JedisClientConfig clientConfig, String user, String password) {
        assertThat(clientConfig.getUser()).isEqualTo(user);
        assertThat(clientConfig.getPassword()).isEqualTo(password);
    }

    /**
     * The Lettuce SSL trust/key material (custom CA, optional mTLS key manager) is not introspectable via
     * Lettuce's public API: {@code ClientOptions.getSslOptions()} always returns a non-null default whose
     * provider is already {@code JDK}, so asserting on it is vacuous. {@code isUseSsl()} — set only when
     * {@code redis.ssl.enabled=true} — is the meaningful unit signal here; the actual custom-SSL handshake
     * is verified end-to-end by {@link TBRedisTlsConnectionTest}.
     */
    static void assertLettuceUsesSsl(LettuceConnectionFactory factory) {
        assertThat(factory.getClientConfiguration().isUseSsl()).isTrue();
    }
}
