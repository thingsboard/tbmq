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
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.store.BrokerStateStores;
import org.thingsboard.mqtt.broker.dao.store.KeyValueStore;
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionInfoProto;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionPersistenceService;

/**
 * Writes persistent client sessions through to the embedded store. The write is done before the method returns,
 * so the callback (from which CONNACK is sent) fires only after the session is stored.
 */
@Slf4j
@Service
@StandaloneComponent
public class StandaloneClientSessionPersistenceService implements ClientSessionPersistenceService {

    private final KeyValueStore store;

    public StandaloneClientSessionPersistenceService(BrokerStateStores stores) {
        this.store = stores.clientSessions();
    }

    @Override
    public void persistClientSessionInfoAsync(String clientId, ClientSessionInfoProto clientSessionInfoProto, BasicCallback callback) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Persisting client session asynchronously - {}", clientId, clientSessionInfoProto);
        }
        StoreStateSupport.writeThenCallback(
                () -> write(clientId, clientSessionInfoProto),
                callback,
                e -> log.error("[{}] Failed to store client session", clientId, e));
    }

    @Override
    public void persistClientSessionInfoSync(String clientId, ClientSessionInfoProto clientSessionInfoProto) throws QueuePersistenceException {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Persisting client session synchronously - {}", clientId, clientSessionInfoProto);
        }
        try {
            write(clientId, clientSessionInfoProto);
        } catch (StoreException e) {
            log.warn("[{}] Failed to update client session. Reason - {}.", clientId, e.getMessage());
            throw new QueuePersistenceException("Failed to update client session", e);
        }
    }

    private void write(String clientId, ClientSessionInfoProto clientSessionInfoProto) {
        if (shouldBeStored(clientSessionInfoProto)) {
            store.put(clientId, clientSessionInfoProto.toByteArray());
        } else {
            store.delete(clientId);
        }
    }

    private boolean shouldBeStored(ClientSessionInfoProto proto) {
        if (isEmpty(proto)) {
            return false;
        }
        return ProtoConverter.convertToClientSessionInfo(proto).isPersistent();
    }

    private boolean isEmpty(ClientSessionInfoProto proto) {
        return proto.getSessionInfo().getClientInfo().getClientId().isEmpty()
                && proto.getSessionInfo().getClientInfo().getClientType().isEmpty();
    }

}
