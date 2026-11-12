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

import java.util.Arrays;

/**
 * The accepted values of {@code store.wal-sync}, parsed in one place for the store and the WAL syncer.
 */
enum WalSyncMode {

    PERIODIC("periodic"),
    ALWAYS("always");

    private final String value;

    WalSyncMode(String value) {
        this.value = value;
    }

    String value() {
        return value;
    }

    /**
     * @throws IllegalStateException when the value is neither {@code periodic} nor {@code always}
     */
    static WalSyncMode parse(String value) {
        return Arrays.stream(values())
                .filter(mode -> mode.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown store.wal-sync value '" + value + "', expected '"
                        + PERIODIC.value + "' or '" + ALWAYS.value + "'"));
    }

}
