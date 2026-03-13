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

import io.github.bucket4j.distributed.serialization.Mapper;
import io.github.bucket4j.redis.jedis.Bucket4jJedis;
import io.github.bucket4j.redis.jedis.cas.JedisBasedProxyManager;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SslOptions;
import io.lettuce.core.TimeoutOptions;
import lombok.Data;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.util.SslUtil;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.UnifiedJedis;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableCaching
@Data
public abstract class TBRedisCacheConfiguration<C extends RedisConfiguration> {

    private final CacheProperties cacheProperties;
    protected final LettuceConfig lettuceConfig;

    @Value("${redis.pool_config.maxTotal:128}")
    private int maxTotal;

    @Value("${redis.pool_config.maxIdle:128}")
    private int maxIdle;

    @Value("${redis.pool_config.minIdle:16}")
    private int minIdle;

    @Value("${redis.pool_config.testOnBorrow:true}")
    private boolean testOnBorrow;

    @Value("${redis.pool_config.testOnReturn:true}")
    private boolean testOnReturn;

    @Value("${redis.pool_config.testWhileIdle:true}")
    private boolean testWhileIdle;

    @Value("${redis.pool_config.minEvictableMs:60000}")
    private long minEvictableMs;

    @Value("${redis.pool_config.evictionRunsMs:30000}")
    private long evictionRunsMs;

    @Value("${redis.pool_config.maxWaitMills:60000}")
    private long maxWaitMills;

    @Value("${redis.pool_config.numberTestsPerEvictionRun:3}")
    private int numberTestsPerEvictionRun;

    @Value("${redis.pool_config.blockWhenExhausted:true}")
    private boolean blockWhenExhausted;

    @Value("${redis.ssl.enabled:false}")
    protected boolean sslEnabled;

    @Autowired
    private RedisSslCredentials redisSslCredentials;

    @Bean
    public JedisConnectionFactory jedisConnectionFactory() {
        return loadFactory();
    }

    @Bean
    public LettuceConnectionFactory lettuceConnectionFactory() {
        var lettucePoolingClientConfigBuilder = LettucePoolingClientConfiguration.builder();
        if (!useDefaultPoolConfig()) {
            lettucePoolingClientConfigBuilder.poolConfig(buildConnectionPoolConfig());
        }

        lettucePoolingClientConfigBuilder.shutdownQuietPeriod(Duration.ofSeconds(lettuceConfig.getShutdownQuietPeriod()));
        lettucePoolingClientConfigBuilder.shutdownTimeout(Duration.ofSeconds(lettuceConfig.getShutdownTimeout()));

        lettucePoolingClientConfigBuilder
                .commandTimeout(Duration.ofSeconds(lettuceConfig.getCommandTimeout()))
                .clientOptions(getLettuceClientOptions());

        if (sslEnabled) {
            lettucePoolingClientConfigBuilder.useSsl();
        }

        return new LettuceConnectionFactory(getRedisConfiguration(), lettucePoolingClientConfigBuilder.build());
    }

    protected ClientOptions getLettuceClientOptions() {
        ClientOptions.Builder builder = ClientOptions.builder().timeoutOptions(TimeoutOptions.enabled());
        if (sslEnabled) {
            builder.sslOptions(createLettuceSslOptions());
        }
        return builder.build();
    }

    protected abstract JedisConnectionFactory loadFactory();

    protected abstract boolean useDefaultPoolConfig();

    protected abstract C getRedisConfiguration();

    protected abstract UnifiedJedis loadUnifiedJedis();

    @Bean
    public JedisBasedProxyManager<String> jedisBasedProxyManager() {
        return Bucket4jJedis.casBasedBuilder(loadUnifiedJedis())
                .keyMapper(Mapper.STRING)
                .build();
    }

    @Bean
    public CacheManager cacheManager(JedisConnectionFactory cf) {
        DefaultFormattingConversionService redisConversionService = new DefaultFormattingConversionService();
        RedisCacheConfiguration.registerDefaultConverters(redisConversionService);
        RedisCacheConfiguration configuration = createRedisCacheConfig(redisConversionService);

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        for (Map.Entry<String, CacheSpecs> entry : cacheProperties.getCacheSpecs().entrySet()) {
            cacheConfigurations.put(entry.getKey(), createRedisCacheConfigWithTtl(redisConversionService, entry.getValue().getTimeToLiveInMinutes()));
        }

        RedisCacheWriter cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(cf, BatchStrategies.scan(100));
        var redisCacheManagerBuilder = RedisCacheManager.builder(cacheWriter).cacheDefaults(configuration)
                .withInitialCacheConfigurations(cacheConfigurations).transactionAware().disableCreateOnMissingCache();
        if (cacheProperties.getStats().isEnabled()) {
            redisCacheManagerBuilder.enableStatistics();
        }
        return redisCacheManagerBuilder.build();
    }

    private RedisCacheConfiguration createRedisCacheConfigWithTtl(DefaultFormattingConversionService redisConversionService, int ttlInMinutes) {
        return createRedisCacheConfig(redisConversionService).entryTtl(getDuration(ttlInMinutes));
    }

    private RedisCacheConfiguration createRedisCacheConfig(DefaultFormattingConversionService redisConversionService) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .withConversionService(redisConversionService);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }

    protected JedisPoolConfig buildPoolConfig() {
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        configurePool(poolConfig);
        return poolConfig;
    }

    protected ConnectionPoolConfig buildConnectionPoolConfig() {
        final ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
        configurePool(poolConfig);
        return poolConfig;
    }

    private void configurePool(GenericObjectPoolConfig<?> poolConfig) {
        poolConfig.setMaxTotal(maxTotal);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);
        poolConfig.setTestOnBorrow(testOnBorrow);
        poolConfig.setTestOnReturn(testOnReturn);
        poolConfig.setTestWhileIdle(testWhileIdle);
        poolConfig.setSoftMinEvictableIdleDuration(Duration.ofMillis(minEvictableMs));
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofMillis(evictionRunsMs));
        poolConfig.setMaxWait(Duration.ofMillis(maxWaitMills));
        poolConfig.setNumTestsPerEvictionRun(numberTestsPerEvictionRun);
        poolConfig.setBlockWhenExhausted(blockWhenExhausted);
    }

    protected SSLSocketFactory createSslSocketFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            KeyManagerFactory kmf = createAndInitKeyManagerFactory();
            TrustManagerFactory tmf = createAndInitTrustManagerFactory();
            sslContext.init(kmf == null ? null : kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException("Creating TLS socket factory for Redis failed!", e);
        }
    }

    protected SslOptions createLettuceSslOptions() {
        try {
            TrustManagerFactory tmf = createAndInitTrustManagerFactory();
            KeyManagerFactory kmf = createAndInitKeyManagerFactory();
            SslOptions.Builder builder = SslOptions.builder().jdkSslProvider().trustManager(tmf);
            if (kmf != null) {
                builder.keyManager(kmf);
            }
            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException("Creating TLS options for Redis (Lettuce) failed!", e);
        }
    }

    private TrustManagerFactory createAndInitTrustManagerFactory() throws Exception {
        List<X509Certificate> caCerts = SslUtil.readCertFileByPath(redisSslCredentials.getCertFile());
        KeyStore caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        caKeyStore.load(null, null);
        for (X509Certificate caCert : caCerts) {
            caKeyStore.setCertificateEntry("redis-ca-cert-" + caCert.getSubjectX500Principal().getName(), caCert);
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(caKeyStore);
        return tmf;
    }

    private KeyManagerFactory createAndInitKeyManagerFactory() throws Exception {
        KeyStore keyStore = loadKeyStore();
        if (keyStore == null) {
            return null;
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, null);
        return kmf;
    }

    private KeyStore loadKeyStore() throws Exception {
        if (StringUtils.isBlank(redisSslCredentials.getUserCertFile()) || StringUtils.isBlank(redisSslCredentials.getUserKeyFile())) {
            return null;
        }
        List<X509Certificate> certificates = SslUtil.readCertFileByPath(redisSslCredentials.getUserCertFile());
        PrivateKey privateKey = SslUtil.readPrivateKeyByFilePath(redisSslCredentials.getUserKeyFile(), null);

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null);
        List<X509Certificate> unique = certificates.stream().distinct().toList();
        for (X509Certificate cert : unique) {
            keyStore.setCertificateEntry("redis-cert-" + cert.getSubjectX500Principal().getName(), cert);
        }
        if (privateKey != null) {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            CertPath certPath = factory.generateCertPath(certificates);
            List<? extends Certificate> path = certPath.getCertificates();
            Certificate[] x509Certificates = path.toArray(new Certificate[0]);
            keyStore.setKeyEntry("redis-private-key", privateKey, null, x509Certificates);
        }
        return keyStore;
    }

    protected List<RedisNode> getNodes(String nodes) {
        List<RedisNode> result;
        if (StringUtils.isBlank(nodes)) {
            result = Collections.emptyList();
        } else {
            result = new ArrayList<>();
            for (String hostPort : nodes.split(BrokerConstants.COMMA)) {
                String host = hostPort.split(BrokerConstants.COLON)[0];
                int port = Integer.parseInt(hostPort.split(BrokerConstants.COLON)[1]);
                result.add(new RedisNode(host, port));
            }
        }
        return result;
    }

    protected Set<HostAndPort> toHostAndPort(String nodes) {
        return getNodes(nodes)
                .stream()
                .map(this::getHostAndPort)
                .collect(Collectors.toSet());
    }

    private HostAndPort getHostAndPort(RedisNode redisNode) {
        return new HostAndPort(redisNode.getHost(), redisNode.getPort());
    }

    private Duration getDuration(int ttlInMinutes) {
        // Duration.ofMinutes(0) will return Duration.ZERO, but this code is written to make this obvious
        return ttlInMinutes <= 0 ? Duration.ZERO : Duration.ofMinutes(ttlInMinutes);
    }
}
