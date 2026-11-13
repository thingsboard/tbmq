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
import org.thingsboard.mqtt.broker.dao.store.BrokerStateStores;
import org.thingsboard.mqtt.broker.dao.store.KeyValueStore;

@Component
public class RocksDbBrokerStateStores implements BrokerStateStores {

    private final KeyValueStore clientSessions;
    private final KeyValueStore clientSubscriptions;
    private final KeyValueStore retainedMessages;
    private final KeyValueStore blockedClients;
    private final KeyValueStore clientSessionAttributes;

    public RocksDbBrokerStateStores(RocksDbStore store) {
        this.clientSessions = new RocksDbKeyValueStore(store, StoreColumnFamily.CLIENT_SESSION);
        this.clientSubscriptions = new RocksDbKeyValueStore(store, StoreColumnFamily.CLIENT_SUBSCRIPTIONS);
        this.retainedMessages = new RocksDbKeyValueStore(store, StoreColumnFamily.RETAINED_MSG);
        this.blockedClients = new RocksDbKeyValueStore(store, StoreColumnFamily.BLOCKED_CLIENT);
        this.clientSessionAttributes = new RocksDbKeyValueStore(store, StoreColumnFamily.CLIENT_SESSION_ATTR);
    }

    @Override
    public KeyValueStore clientSessions() {
        return clientSessions;
    }

    @Override
    public KeyValueStore clientSubscriptions() {
        return clientSubscriptions;
    }

    @Override
    public KeyValueStore retainedMessages() {
        return retainedMessages;
    }

    @Override
    public KeyValueStore blockedClients() {
        return blockedClients;
    }

    @Override
    public KeyValueStore clientSessionAttributes() {
        return clientSessionAttributes;
    }

}
