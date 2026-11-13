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
package org.thingsboard.mqtt.broker.standalone.system;

import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.cache.ServiceRegistryStore;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@StandaloneComponent
public class InMemoryServiceRegistryStore implements ServiceRegistryStore {

    private final Map<String, String> entries = new ConcurrentHashMap<>();

    @Override
    public void put(String field, String value) {
        entries.put(field, value);
    }

    @Override
    public Optional<String> get(String field) {
        return Optional.ofNullable(entries.get(field));
    }

    @Override
    public Map<String, String> entries() {
        return new HashMap<>(entries);
    }

    @Override
    public long delete(String... fields) {
        long removed = 0;
        for (String field : fields) {
            if (entries.remove(field) != null) {
                removed++;
            }
        }
        return removed;
    }

}
