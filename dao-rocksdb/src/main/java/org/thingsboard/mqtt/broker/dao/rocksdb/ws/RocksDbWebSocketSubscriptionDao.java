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
package org.thingsboard.mqtt.broker.dao.rocksdb.ws;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscription;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.RocksEntityStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.RocksEntityStore.ProjectedEntity;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketSubscriptionDao;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The WebSocket subscriptions of the standalone edition, on the entity store. The store refuses a subscription of a
 * missing connection with the {@code fk_websocket_connection_id} violation. Pages are of one connection, sorted by
 * {@code id}, {@code createdTime} or {@code webSocketConnectionId}; as in the SQL query, a text search is ignored.
 */
@Component
@StandaloneComponent
public class RocksDbWebSocketSubscriptionDao implements WebSocketSubscriptionDao {

    private final RocksEntityStore store;

    public RocksDbWebSocketSubscriptionDao(RocksEntityStore store) {
        this.store = store;
    }

    @Override
    public List<WebSocketSubscription> find() {
        return store.findAll(EntityDescriptors.WEBSOCKET_SUBSCRIPTION);
    }

    @Override
    public WebSocketSubscription findById(UUID id) {
        return store.findById(EntityDescriptors.WEBSOCKET_SUBSCRIPTION, id);
    }

    @Override
    public ListenableFuture<WebSocketSubscription> findByIdAsync(UUID id) {
        return Futures.immediateFuture(findById(id));
    }

    @Override
    public WebSocketSubscription save(WebSocketSubscription subscription) {
        return store.save(EntityDescriptors.WEBSOCKET_SUBSCRIPTION, subscription);
    }

    /**
     * @return {@code true} whether or not the subscription existed, as the SQL DAO answers
     */
    @Override
    public boolean removeById(UUID id) {
        store.removeById(EntityDescriptors.WEBSOCKET_SUBSCRIPTION, id);
        return true;
    }

    @Override
    public PageData<WebSocketSubscription> findAllByWebSocketConnectionId(UUID webSocketConnectionId, PageLink pageLink) {
        return store.findPage(EntityDescriptors.WEBSOCKET_SUBSCRIPTION, pageLink, new OfConnection(webSocketConnectionId));
    }

    private record OfConnection(UUID connectionId) implements Predicate<ProjectedEntity> {

        @Override
        public boolean test(ProjectedEntity entity) {
            return connectionId != null && connectionId.equals(entity.filterValues().get("webSocketConnectionId"));
        }

    }

}
