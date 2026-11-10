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

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RedisServiceRegistryStore implements ServiceRegistryStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheProperties cacheProperties;

    private String registryKey;

    @PostConstruct
    void init() {
        registryKey = cacheProperties.prefixKey(CacheConstants.SERVICE_REGISTRY_KEY);
    }

    @Override
    public void put(String field, String value) {
        redisTemplate.opsForHash().put(registryKey, field, value);
    }

    @Override
    public Optional<String> get(String field) {
        return Optional.ofNullable(redisTemplate.opsForHash().get(registryKey, field)).map(String::valueOf);
    }

    @Override
    public Map<String, String> entries() {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(registryKey);
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>(raw.size());
        raw.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
        return result;
    }

    @Override
    public long delete(String... fields) {
        Long removed = redisTemplate.opsForHash().delete(registryKey, (Object[]) fields);
        return removed == null ? 0L : removed;
    }

}
