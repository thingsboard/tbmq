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
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.ClientSubscriptionsProto;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionChangesCallback;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionConsumer;
import org.thingsboard.mqtt.broker.service.subscription.data.SourcedSubscriptions;
import org.thingsboard.mqtt.broker.service.subscription.data.SubscriptionsSourceKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Loads the subscriptions of persistent clients from the embedded store at startup. There is only one node, so
 * nobody else changes the subscriptions and {@link #listen} has nothing to deliver.
 */
@Slf4j
@Component
@StandaloneComponent
public class StandaloneClientSubscriptionConsumer implements ClientSubscriptionConsumer {

    private final KeyValueStore store;

    public StandaloneClientSubscriptionConsumer(BrokerStateStores stores) {
        this.store = stores.clientSubscriptions();
    }

    @Override
    public Map<SubscriptionsSourceKey, Set<TopicSubscription>> initLoad() throws QueuePersistenceException {
        log.debug("Starting subscriptions initLoad");
        Map<String, SourcedSubscriptions> stored = StoreStateSupport.loadAll(store, "client subscriptions",
                bytes -> ProtoConverter.convertProtoToClientSubscriptions(ClientSubscriptionsProto.parseFrom(bytes)));
        Map<SubscriptionsSourceKey, Set<TopicSubscription>> subscriptions = new HashMap<>();
        stored.forEach((clientId, sourced) ->
                subscriptions.put(new SubscriptionsSourceKey(clientId, sourced.getSource()), sourced.getSubscriptions()));
        log.debug("Finished subscriptions initLoad, loaded the subscriptions of {} clients", subscriptions.size());
        return subscriptions;
    }

    @Override
    public void listen(ClientSubscriptionChangesCallback callback) {
        // single node: no other writer, nothing to listen to
    }

}
