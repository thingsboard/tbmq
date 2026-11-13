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
package org.thingsboard.mqtt.broker.service.system;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.thingsboard.mqtt.broker.common.data.queue.ServiceType;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.UNKNOWN;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ServiceInfoIds {

    private static final String SEPARATOR = ":";

    /**
     * Brokers are registered under their bare service id, as they always were, so older nodes keep finding them.
     * Every other service type is registered as {@code <type>:<serviceId>}, so an executor and a broker that share
     * a service id no longer overwrite each other. Entries written before this scheme are still understood.
     */
    public static String registryField(String serviceId, String serviceType) {
        return ServiceType.TBMQ.name().equals(serviceType) ? serviceId : serviceType + SEPARATOR + serviceId;
    }

    /**
     * Entity id of the system info timeseries (CPU, memory, disk) of a service. It follows the registry field, so a
     * broker and an executor that share a service id keep separate metrics.
     */
    public static String timeseriesEntityId(String serviceId, String serviceType) {
        return registryField(serviceId, serviceType);
    }

    /**
     * Inverse of {@link #registryField}: a field prefixed by a known service type is typed, anything else is a bare
     * service id (a broker, or an entry written before typed fields) whose type is the stored value.
     */
    static RegistryEntry parseRegistryField(String field, String value) {
        int separator = field.indexOf(SEPARATOR);
        if (separator > 0) {
            String prefix = field.substring(0, separator);
            if (ServiceType.findByName(prefix).isPresent()) {
                return new RegistryEntry(field.substring(separator + 1), prefix);
            }
        }
        return new RegistryEntry(field, value == null ? UNKNOWN : value);
    }

    record RegistryEntry(String serviceId, String serviceType) {
    }

}
