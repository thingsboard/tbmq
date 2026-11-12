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
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.store.BrokerStateStores;
import org.thingsboard.mqtt.broker.dao.store.KeyValueStore;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.BlockedClientProto;
import org.thingsboard.mqtt.broker.queue.constants.QueueConstants;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.producer.BlockedClientProducerService;

/**
 * Writes blocked clients through to the embedded store. The write is done before the method returns, so the
 * callback fires only after the entry is stored.
 */
@Slf4j
@Service
@StandaloneComponent
public class StandaloneBlockedClientProducerService implements BlockedClientProducerService {

    private final KeyValueStore store;

    public StandaloneBlockedClientProducerService(BrokerStateStores stores) {
        this.store = stores.blockedClients();
    }

    @Override
    public void persistBlockedClient(String key, BlockedClientProto blockedClientProto, BasicCallback callback) {
        log.trace("[{}] Persisting blocked client - {}", key, blockedClientProto);
        StoreStateSupport.writeThenCallback(
                () -> write(key, blockedClientProto),
                callback,
                e -> log.error("[{}] Failed to store blocked client {}", key, blockedClientProto, e));
    }

    @Override
    public void persistDummyBlockedClient(String key, BlockedClientProto blockedClientProto) throws QueuePersistenceException {
        // only the cluster consumer calls this, to write its Kafka init marker; there is no marker to write here
    }

    private void write(String key, BlockedClientProto blockedClientProto) {
        if (QueueConstants.EMPTY_BLOCKED_CLIENT_PROTO.equals(blockedClientProto)) {
            store.delete(key);
        } else {
            store.put(key, blockedClientProto.toByteArray());
        }
    }

}
