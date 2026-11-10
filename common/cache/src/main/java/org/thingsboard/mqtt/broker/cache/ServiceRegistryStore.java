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

import java.util.Map;
import java.util.Optional;

/**
 * Storage primitive for the node registry: a flat map of service id to service type.
 */
public interface ServiceRegistryStore {

    void put(String field, String value);

    Optional<String> get(String field);

    /**
     * @return all registered entries; never null, empty when nothing is registered
     */
    Map<String, String> entries();

    /**
     * @return the number of fields removed
     */
    long delete(String... fields);

}
