/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2026 ThingsBoard, Inc. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 *
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes
 * information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
 * OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
 * THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
 * DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
 * OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
 */
package org.thingsboard.mqtt.broker.service.system;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.thingsboard.mqtt.broker.common.data.queue.ServiceType;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

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
     * Stable audit log entity id of a service, derived from its registry field, so every audit entry of one service
     * can be found by one entity id. An operation without a type targets the bare field, which is the broker's.
     */
    public static UUID auditEntityId(String serviceId, String serviceType) {
        String name = serviceType == null ? serviceId : registryField(serviceId, serviceType);
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
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
