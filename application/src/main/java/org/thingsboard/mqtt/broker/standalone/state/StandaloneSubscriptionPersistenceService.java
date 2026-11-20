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
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.store.BrokerStateStores;
import org.thingsboard.mqtt.broker.dao.store.KeyValueStore;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionPersistenceService;

import java.util.Set;

/**
 * Writes the subscriptions of persistent clients through to the embedded store. The write is done before the method
 * returns, so the callback fires only after the subscriptions are stored. The subscriptions of a client without a
 * persistent session are not stored: nothing could restore them after a restart.
 */
@Slf4j
@Service
@StandaloneComponent
public class StandaloneSubscriptionPersistenceService implements SubscriptionPersistenceService {

    private final KeyValueStore store;
    private final ClientSessionCache clientSessionCache;

    public StandaloneSubscriptionPersistenceService(BrokerStateStores stores, ClientSessionCache clientSessionCache) {
        this.store = stores.clientSubscriptions();
        this.clientSessionCache = clientSessionCache;
    }

    @Override
    public void persistClientSubscriptionsAsync(String clientId, Set<TopicSubscription> clientSubscriptions, BasicCallback callback) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Persisting client subscriptions asynchronously - {}", clientId, clientSubscriptions);
        }
        StoreStateSupport.writeThenCallback(
                () -> write(clientId, clientSubscriptions),
                callback,
                e -> log.error("[{}] Failed to store client subscriptions", clientId, e));
    }

    @Override
    public void persistClientSubscriptionsSync(String clientId, Set<TopicSubscription> clientSubscriptions) throws QueuePersistenceException {
        // only the cluster consumer calls this, to write its Kafka init marker; there is no marker to write here
    }

    private void write(String clientId, Set<TopicSubscription> clientSubscriptions) {
        if (clientSubscriptions.isEmpty() || !hasPersistentSession(clientId)) {
            store.delete(clientId);
        } else {
            store.put(clientId, ProtoConverter.convertToClientSubscriptionsProto(clientSubscriptions).toByteArray());
        }
    }

    private boolean hasPersistentSession(String clientId) {
        ClientSessionInfo session = clientSessionCache.getClientSessionInfo(clientId);
        return session != null && session.isPersistent();
    }

}
