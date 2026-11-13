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
package org.thingsboard.mqtt.broker.standalone.state;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.store.BrokerStateStores;
import org.thingsboard.mqtt.broker.dao.store.KeyValueStore;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.BlockedClientProto;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.consumer.BlockedClientChangesCallback;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.consumer.BlockedClientConsumerService;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClient;

import java.util.Map;

/**
 * Loads the blocked clients from the embedded store at startup. There is only one node, so nobody else changes
 * them and {@link #listen} has nothing to deliver.
 */
@Slf4j
@Component
@StandaloneComponent
public class StandaloneBlockedClientConsumerService implements BlockedClientConsumerService {

    private final KeyValueStore store;

    public StandaloneBlockedClientConsumerService(BrokerStateStores stores) {
        this.store = stores.blockedClients();
    }

    @Override
    public Map<String, BlockedClient> initLoad() throws QueuePersistenceException {
        log.debug("Starting blocked client initLoad");
        Map<String, BlockedClient> blockedClients = StoreStateSupport.loadAll(store, "blocked clients",
                bytes -> ProtoConverter.convertProtoToBlockedClient(BlockedClientProto.parseFrom(bytes)));
        log.debug("Finished blocked client initLoad, loaded {} blocked clients", blockedClients.size());
        return blockedClients;
    }

    @Override
    public void listen(BlockedClientChangesCallback callback) {
        // single node: no other writer, nothing to listen to
    }

}
