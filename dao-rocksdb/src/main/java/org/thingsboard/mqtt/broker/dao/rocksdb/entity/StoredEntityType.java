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
package org.thingsboard.mqtt.broker.dao.rocksdb.entity;

/**
 * The entity types of the {@code entity} column family. The code is the first byte of every entity key, so it is part
 * of the on-disk format: a code is never changed or reused. The id kind decides how the id follows it in the key.
 */
public enum StoredEntityType {

    ADMIN_SETTINGS(1, IdKind.UUID),
    MQTT_AUTH_PROVIDER(2, IdKind.UUID),
    MQTT_CLIENT_CREDENTIALS(3, IdKind.UUID),
    USER(4, IdKind.UUID),
    USER_CREDENTIALS(5, IdKind.UUID),
    WEBSOCKET_CONNECTION(6, IdKind.UUID),
    WEBSOCKET_SUBSCRIPTION(7, IdKind.UUID),
    UNAUTHORIZED_CLIENT(8, IdKind.STRING);

    /**
     * How an id is written into a key.
     */
    public enum IdKind {
        /**
         * 16 bytes: the most significant long, then the least significant one, both big-endian.
         */
        UUID,
        /**
         * The UTF-8 bytes of the string.
         */
        STRING
    }

    private final byte code;
    private final IdKind idKind;

    StoredEntityType(int code, IdKind idKind) {
        this.code = (byte) code;
        this.idKind = idKind;
    }

    public byte code() {
        return code;
    }

    public IdKind idKind() {
        return idKind;
    }

}
