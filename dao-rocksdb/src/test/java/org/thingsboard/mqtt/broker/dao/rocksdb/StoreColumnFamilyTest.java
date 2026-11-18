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

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoreColumnFamilyTest {

    @Test
    void theFiveNewFamiliesHaveTheirPinnedNames() {
        assertThat(StoreColumnFamily.ENTITY.columnFamilyName()).isEqualTo("entity");
        assertThat(StoreColumnFamily.ENTITY_INDEX.columnFamilyName()).isEqualTo("entity_index");
        assertThat(StoreColumnFamily.SESSION_CTX.columnFamilyName()).isEqualTo("session_ctx");
        assertThat(StoreColumnFamily.TS.columnFamilyName()).isEqualTo("ts");
        assertThat(StoreColumnFamily.TS_LATEST.columnFamilyName()).isEqualTo("ts_latest");
    }

    @Test
    void theEightEarlierFamiliesKeepTheirNamesAndTheNewOnesFollowThem() {
        assertThat(Arrays.stream(StoreColumnFamily.values()).map(StoreColumnFamily::columnFamilyName).toList())
                .isEqualTo(List.of("meta", "client_session", "client_subscriptions", "retained_msg", "blocked_client",
                        "client_session_attr", "device_msg", "device_msg_meta",
                        "entity", "entity_index", "session_ctx", "ts", "ts_latest"));
    }

}
