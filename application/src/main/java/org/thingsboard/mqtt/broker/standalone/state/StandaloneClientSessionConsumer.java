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

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.store.BrokerStateStores;
import org.thingsboard.mqtt.broker.dao.store.KeyValueStore;
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionInfoProto;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionChangesCallback;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionConsumer;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads the persistent client sessions from the embedded store at startup. There is only one node, so nobody else
 * changes the sessions and {@link #listen} has nothing to deliver.
 */
@Slf4j
@Component
@StandaloneComponent
public class StandaloneClientSessionConsumer implements ClientSessionConsumer {

    private final KeyValueStore store;

    public StandaloneClientSessionConsumer(BrokerStateStores stores) {
        this.store = stores.clientSessions();
    }

    @Override
    public Map<String, ClientSessionInfo> initLoad() throws QueuePersistenceException {
        log.debug("Starting client sessions initLoad");
        Map<String, ClientSessionInfo> sessions = new HashMap<>();
        try {
            store.forEach((clientId, bytes) -> {
                ClientSessionInfo session = parse(clientId, bytes);
                if (session != null) {
                    // no connection survives a restart of the only node
                    sessions.put(clientId, session.toBuilder().connected(false).build());
                }
            });
        } catch (StoreException e) {
            log.error("Failed to load client sessions.", e);
            throw new QueuePersistenceException("Failed to load client sessions", e);
        }
        log.debug("Finished client sessions initLoad, loaded {} sessions", sessions.size());
        return sessions;
    }

    @Override
    public void listen(ClientSessionChangesCallback callback) {
        // single node: no other writer, nothing to listen to
    }

    private ClientSessionInfo parse(String clientId, byte[] bytes) {
        try {
            return ProtoConverter.convertToClientSessionInfo(ClientSessionInfoProto.parseFrom(bytes));
        } catch (InvalidProtocolBufferException | RuntimeException e) {
            log.warn("[{}] Skipping a stored client session that cannot be read.", clientId, e);
            return null;
        }
    }

}
