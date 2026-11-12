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
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.store.BrokerStateStores;
import org.thingsboard.mqtt.broker.dao.store.KeyValueStore;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionInfoProto;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionChangesCallback;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionConsumer;

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
        long loadTs = System.currentTimeMillis();
        Map<String, ClientSessionInfo> sessions = StoreStateSupport.loadAll(store, "client sessions", bytes -> {
            ClientSessionInfo session = ProtoConverter.convertToClientSessionInfo(ClientSessionInfoProto.parseFrom(bytes));
            return session.isConnected() ? disconnectedByRestart(session, loadTs) : session;
        });
        log.debug("Finished client sessions initLoad, loaded {} sessions", sessions.size());
        return sessions;
    }

    /**
     * No connection survives a restart of the only node. The session was last seen connected, so its Session Expiry
     * Interval starts counting at the load; without a disconnect time a persistent MQTT 5 session would read as
     * expired right away.
     */
    private static ClientSessionInfo disconnectedByRestart(ClientSessionInfo session, long loadTs) {
        return session.toBuilder().connected(false).disconnectedAt(loadTs).build();
    }

    @Override
    public void listen(ClientSessionChangesCallback callback) {
        // single node: no other writer, nothing to listen to
    }

}
