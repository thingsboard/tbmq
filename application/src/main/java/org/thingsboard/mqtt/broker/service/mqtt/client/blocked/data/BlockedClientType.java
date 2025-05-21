/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data;

import lombok.Getter;

@Getter
public enum BlockedClientType {

    CLIENT_ID("clientId"),
    USERNAME("username"),
    IP_ADDRESS("ipAddress"),
    REGEX("regex");

    private final String label;

    BlockedClientType(String label) {
        this.label = label;
    }

    public static BlockedClientType fromLabel(String label) {
        for (BlockedClientType type : BlockedClientType.values()) {
            if (type.label.equals(label)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown BlockedClientType label: " + label);
    }
}
