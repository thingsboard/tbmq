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

import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.store.StoreSecrets;

@Component
@StandaloneComponent
public class RocksDbStoreSecrets implements StoreSecrets {

    private final RocksDbStore store;

    public RocksDbStoreSecrets(RocksDbStore store) {
        this.store = store;
    }

    @Override
    public byte[] jwtSigningKey() {
        return store.get(StoreColumnFamily.META, RocksDbStore.META_JWT_SIGNING_KEY);
    }

}
