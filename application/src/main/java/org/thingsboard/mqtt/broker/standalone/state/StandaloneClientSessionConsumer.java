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
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionInfoProto;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionChangesCallback;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionConsumer;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads the persistent client sessions from the embedded store at startup. There is only one node, so nobody else
 * changes the sessions and {@link #listen} has nothing to deliver.
 * <p>
 * Once the sessions are read, each one is adjusted to this run, and an adjusted session is written back to the store
 * before the load returns, so that the adjustment survives the next restart:
 * <ul>
 *   <li>a session stored as connected is disconnected at the load time: no connection survives a restart, and its
 *   Session Expiry Interval (and the administrative TTL) must count from the first restart, not from every one;</li>
 *   <li>a session stored with another service id gets the current one: the session cleanup only expires the sessions
 *   of its own service id, so a session of a previous id (a recreated container, a changed
 *   {@code standalone.service-id}) would never expire.</li>
 * </ul>
 * A session that needs neither is not rewritten. A store that cannot be written at startup is not usable, so a failed
 * write-back fails the load.
 */
@Slf4j
@Component
@StandaloneComponent
public class StandaloneClientSessionConsumer implements ClientSessionConsumer {

    private final KeyValueStore store;
    private final ServiceInfoProvider serviceInfoProvider;

    public StandaloneClientSessionConsumer(BrokerStateStores stores, ServiceInfoProvider serviceInfoProvider) {
        this.store = stores.clientSessions();
        this.serviceInfoProvider = serviceInfoProvider;
    }

    @Override
    public Map<String, ClientSessionInfo> initLoad() throws QueuePersistenceException {
        log.debug("Starting client sessions initLoad");
        long loadTs = System.currentTimeMillis();
        String serviceId = serviceInfoProvider.getServiceId();
        Map<String, ClientSessionInfo> stored = StoreStateSupport.loadAll(store, "client sessions",
                bytes -> ProtoConverter.convertToClientSessionInfo(ClientSessionInfoProto.parseFrom(bytes)));
        // after the scan: the store must not be written from inside its own scan
        Map<String, ClientSessionInfo> sessions = new HashMap<>();
        int storedBack = 0;
        for (Map.Entry<String, ClientSessionInfo> entry : stored.entrySet()) {
            ClientSessionInfo session = adjust(entry.getValue(), loadTs, serviceId);
            if (session != entry.getValue()) {
                writeBack(entry.getKey(), session);
                storedBack++;
            }
            sessions.put(entry.getKey(), session);
        }
        log.debug("Finished client sessions initLoad, loaded {} sessions, stored back {} adjusted ones", sessions.size(), storedBack);
        return sessions;
    }

    private static ClientSessionInfo adjust(ClientSessionInfo session, long loadTs, String serviceId) {
        ClientSessionInfo result = session.isConnected() ? disconnectedByRestart(session, loadTs) : session;
        return serviceId.equals(result.getServiceId()) ? result : result.toBuilder().serviceId(serviceId).build();
    }

    /**
     * No connection survives a restart of the only node. The session was last seen connected, so its Session Expiry
     * Interval starts counting at the load; without a disconnect time a persistent MQTT 5 session would read as
     * expired right away. The result is stored back, so that the next restart keeps this disconnect time instead of
     * setting a later one.
     */
    private static ClientSessionInfo disconnectedByRestart(ClientSessionInfo session, long loadTs) {
        return session.toBuilder().connected(false).disconnectedAt(loadTs).build();
    }

    private void writeBack(String clientId, ClientSessionInfo session) throws QueuePersistenceException {
        try {
            store.put(clientId, ProtoConverter.convertToClientSessionInfoProto(session).toByteArray());
        } catch (StoreException e) {
            log.error("[{}] Failed to store the loaded client session back", clientId, e);
            throw new QueuePersistenceException("Failed to store the loaded client session of client '" + clientId + "' back", e);
        }
    }

    @Override
    public void listen(ClientSessionChangesCallback callback) {
        // single node: no other writer, nothing to listen to
    }

}
