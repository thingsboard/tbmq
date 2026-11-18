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
 * The indexes of the {@code entity_index} column family. The code is the first byte of every index key, so it is part
 * of the on-disk format: a code is never changed or reused. The constraint name is the one the SQL schema gives the
 * same unique index, so the services keep their messages; {@code null} for an index that is not unique.
 */
public enum EntityIndex {

    // RocksDB-only name: the SQL schema has no such constraint
    ADMIN_SETTINGS_KEY(1, "admin_settings_key_unq_key"),
    AUTH_PROVIDER_TYPE(2, "mqtt_auth_provider_type_key"),
    CREDENTIALS_ID(3, "mqtt_client_credentials_id_unq_key"),
    CREDENTIALS_NAME(4, null),
    CREDENTIALS_TYPE(5, null),
    USER_EMAIL(6, "broker_user_email_key"),
    USER_CREDENTIALS_USER_ID(7, "user_credentials_user_id_key"),
    USER_CREDENTIALS_ACTIVATE_TOKEN(8, "user_credentials_activate_token_key"),
    USER_CREDENTIALS_RESET_TOKEN(9, "user_credentials_reset_token_key"),
    // the indexed value is the user id, a zero byte and the connection name
    WS_CONNECTION_USER_NAME(10, "name_unq_key"),
    WS_CONNECTION_USER(11, null),
    WS_SUBSCRIPTION_CONNECTION(12, null),
    // the key string to its 4-byte key id, for the time series
    TS_KEY_DICTIONARY(64, null);

    private final byte code;
    private final String constraintName;

    EntityIndex(int code, String constraintName) {
        this.code = (byte) code;
        this.constraintName = constraintName;
    }

    public byte code() {
        return code;
    }

    public String constraintName() {
        return constraintName;
    }

}
