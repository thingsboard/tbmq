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
package org.thingsboard.mqtt.broker.dao.rocksdb;

import java.nio.charset.StandardCharsets;

/**
 * The column families of the store. Each constant carries its RocksDB column family name explicitly: the name is
 * part of the on-disk schema, so renaming a constant must not rename the family.
 */
public enum StoreColumnFamily {

    META("meta"),
    CLIENT_SESSION("client_session"),
    CLIENT_SUBSCRIPTIONS("client_subscriptions"),
    RETAINED_MSG("retained_msg"),
    BLOCKED_CLIENT("blocked_client"),
    CLIENT_SESSION_ATTR("client_session_attr"),
    DEVICE_MSG("device_msg"),
    DEVICE_MSG_META("device_msg_meta");

    private final String columnFamilyName;

    StoreColumnFamily(String columnFamilyName) {
        this.columnFamilyName = columnFamilyName;
    }

    public String columnFamilyName() {
        return columnFamilyName;
    }

    byte[] columnFamilyNameBytes() {
        return columnFamilyName.getBytes(StandardCharsets.UTF_8);
    }

}
