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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;

import java.util.Map;
import java.util.stream.Collectors;

@Component
@ConfigurationProperties(prefix = "cache")
@Data
public class CacheProperties {

    private CacheStats stats;
    private String cachePrefix;
    private Map<String, CacheSpecs> specs;

    public Map<String, CacheSpecs> getCacheSpecs() {
        return specs.entrySet().stream()
                .collect(Collectors.toMap(entry -> cachePrefix + entry.getKey(), Map.Entry::getValue));
    }

    public String prefixKey(String raw) {
        return StringUtils.isEmpty(cachePrefix) ? raw : cachePrefix + raw;
    }
}
