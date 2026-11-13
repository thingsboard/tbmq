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
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.store.BrokerStateStores;
import org.thingsboard.mqtt.broker.dao.store.KeyValueStore;
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.ClientSubscriptionsProto;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionChangesCallback;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionConsumer;
import org.thingsboard.mqtt.broker.service.subscription.data.SourcedSubscriptions;
import org.thingsboard.mqtt.broker.service.subscription.data.SubscriptionsSourceKey;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads the subscriptions of persistent clients from the embedded store at startup. There is only one node, so
 * nobody else changes the subscriptions and {@link #listen} has nothing to deliver.
 * <p>
 * The load first deletes the stored subscriptions of every client that has no stored session (see
 * {@link #purgeSubscriptionsWithoutSession()}), so that they cannot come back attached to a later session of the
 * same client id.
 */
@Slf4j
@Component
@StandaloneComponent
public class StandaloneClientSubscriptionConsumer implements ClientSubscriptionConsumer {

    private final KeyValueStore store;
    private final KeyValueStore sessionsStore;

    public StandaloneClientSubscriptionConsumer(BrokerStateStores stores) {
        this.store = stores.clientSubscriptions();
        this.sessionsStore = stores.clientSessions();
    }

    @Override
    public Map<SubscriptionsSourceKey, Set<TopicSubscription>> initLoad() throws QueuePersistenceException {
        log.debug("Starting subscriptions initLoad");
        purgeSubscriptionsWithoutSession();
        Map<String, SourcedSubscriptions> stored = StoreStateSupport.loadAll(store, "client subscriptions",
                bytes -> ProtoConverter.convertProtoToClientSubscriptions(ClientSubscriptionsProto.parseFrom(bytes)));
        Map<SubscriptionsSourceKey, Set<TopicSubscription>> subscriptions = new HashMap<>();
        stored.forEach((clientId, sourced) ->
                subscriptions.put(new SubscriptionsSourceKey(clientId, sourced.getSource()), sourced.getSubscriptions()));
        log.debug("Finished subscriptions initLoad, loaded the subscriptions of {} clients", subscriptions.size());
        return subscriptions;
    }

    /**
     * Deletes the stored subscriptions of every client id that has no entry in the sessions store. Such an entry is
     * left behind when a session stops being persistent while stored (an MQTT 5 client with cleanStart=true that
     * disconnects with Session Expiry Interval 0 loses its session entry but not its subscriptions) or when a
     * crash falls between the two separate writes of a session removal. Left in the store it would be loaded
     * and attached to a later persistent session of the same client id that has not subscribed yet.
     * <p>
     * It is safe: only persistent sessions are stored, a session is stored before the CONNACK and the subscriptions
     * only after it, so a stored subscriptions entry without a stored session is never live; and the subscriptions
     * of integrations are not stored here (they are cluster-only).
     */
    private void purgeSubscriptionsWithoutSession() throws QueuePersistenceException {
        try {
            Set<String> sessionIds = new HashSet<>();
            sessionsStore.forEach((clientId, bytes) -> sessionIds.add(clientId));
            int purged = store.retainKeys(sessionIds::contains);
            if (purged > 0) {
                log.info("Deleted the stored subscriptions of {} clients that have no stored session", purged);
            }
        } catch (StoreException e) {
            log.error("Failed to delete the stored subscriptions of clients without a stored session", e);
            throw new QueuePersistenceException("Failed to load client subscriptions: could not delete the subscriptions of clients without a stored session", e);
        }
    }

    @Override
    public void listen(ClientSubscriptionChangesCallback callback) {
        // single node: no other writer, nothing to listen to
    }

}
