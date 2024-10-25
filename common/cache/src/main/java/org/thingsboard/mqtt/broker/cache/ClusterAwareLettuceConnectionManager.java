/**
 * Copyright © 2016-2024 The Thingsboard Authors
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

import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "redis.connection", value = "type", havingValue = "cluster")
public class ClusterAwareLettuceConnectionManager extends AbstractLettuceConnectionManager implements LettuceConnectionManager {

    private final LettuceConnectionFactory lettuceConnectionFactory;

    private StatefulRedisClusterConnection<String, String> connection;

    @PostConstruct
    public void init() {
        RedisClusterClient client = (RedisClusterClient) lettuceConnectionFactory.getNativeClient();
        if (client == null) {
            throw new IllegalStateException("Failed to initiate Redis lettuce cluster client!");
        }
        connection = client.connect();
        connection.setAutoFlushCommands(autoFlush);
    }

    @Override
    @SneakyThrows
    public String scriptLoad(String script) {
        RedisFuture<String> scriptLoadFuture = connection.async().scriptLoad(script);
        forceFlush();
        return scriptLoadFuture.get(10, TimeUnit.SECONDS);
    }

    @Override
    public RedisFuture<String> scriptLoadAsync(String script) {
        RedisFuture<String> scriptLoadFuture = connection.async().scriptLoad(script);
        flushIfNeeded();
        return scriptLoadFuture;
    }

    @Override
    public <T> RedisFuture<T> evalShaAsync(String sha, ScriptOutputType outputType, String[] keys, String... values) {
        RedisFuture<T> evalshaFuture = connection.async().evalsha(sha, outputType, keys, values);
        flushIfNeeded();
        return evalshaFuture;
    }

    @Override
    public <T> RedisFuture<T> evalShaAsync(String sha, ScriptOutputType outputType, String... keys) {
        RedisFuture<T> evalshaFuture = connection.async().evalsha(sha, outputType, keys);
        flushIfNeeded();
        return evalshaFuture;
    }

    @Override
    public <T> RedisFuture<T> evalAsync(String script, ScriptOutputType outputType, String[] keys, String... values) {
        RedisFuture<T> evalFuture = connection.async().eval(script, outputType, keys, values);
        flushIfNeeded();
        return evalFuture;
    }

    @Override
    public <T> RedisFuture<T> evalAsync(String script, ScriptOutputType outputType, String... keys) {
        RedisFuture<T> evalFuture = connection.async().eval(script, outputType, keys);
        flushIfNeeded();
        return evalFuture;
    }

    @Override
    public RedisFuture<String> getAsync(String key) {
        RedisFuture<String> getFuture = connection.async().get(key);
        flushIfNeeded();
        return getFuture;
    }

    @Override
    public RedisFuture<Long> delAsync(String key) {
        RedisFuture<Long> delFuture = connection.async().del(key);
        flushIfNeeded();
        return delFuture;
    }

    @Override
    public RedisFuture<String> setAsync(String key, String value) {
        RedisFuture<String> setFuture = connection.async().set(key, value);
        flushIfNeeded();
        return setFuture;
    }

    @Override
    protected void flushCommands() {
        connection.flushCommands();
        bufferedCmdCount.set(0);
    }

    @Scheduled(fixedRateString = "${lettuce.flush-interval-ms}")
    public void flushOnTimeThreshold() {
        doFlushOnTimeThreshold();
    }

}
