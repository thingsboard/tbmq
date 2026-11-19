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
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityIndex;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.RocksEntityStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.RocksEntityStore.ProjectedEntity;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketConnectionDao;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The WebSocket connections of the standalone edition, on the entity store. A user cannot have two connections of one
 * name, as the SQL {@code name_unq_key}; the store refuses a connection of a missing user with the {@code fk_user_id}
 * violation. Pages are of one user, searched by the name and sorted by {@code id}, {@code createdTime}, {@code name} or
 * {@code userId}. Removing a connection removes its subscriptions in the same batch, as the SQL foreign key cascades.
 */
@Component
@StandaloneComponent
public class RocksDbWebSocketConnectionDao implements WebSocketConnectionDao {

    private final RocksEntityStore store;

    public RocksDbWebSocketConnectionDao(RocksEntityStore store) {
        this.store = store;
    }

    @Override
    public List<WebSocketConnection> find() {
        return store.findAll(EntityDescriptors.WEBSOCKET_CONNECTION);
    }

    @Override
    public WebSocketConnection findById(UUID id) {
        return store.findById(EntityDescriptors.WEBSOCKET_CONNECTION, id);
    }

    @Override
    public ListenableFuture<WebSocketConnection> findByIdAsync(UUID id) {
        return Futures.immediateFuture(findById(id));
    }

    @Override
    public WebSocketConnection save(WebSocketConnection connection) {
        return store.save(EntityDescriptors.WEBSOCKET_CONNECTION, connection);
    }

    /**
     * @return {@code true} whether or not the connection existed, as the SQL DAO answers
     */
    @Override
    public boolean removeById(UUID id) {
        store.removeById(EntityDescriptors.WEBSOCKET_CONNECTION, id);
        return true;
    }

    @Override
    public PageData<WebSocketConnection> findAll(UUID userId, PageLink pageLink) {
        return store.findPage(EntityDescriptors.WEBSOCKET_CONNECTION, pageLink, new OfUser(userId));
    }

    /**
     * The key of the unique index is the user id and the name, as {@code EntityDescriptors} builds it.
     */
    @Override
    public WebSocketConnection findByUserIdAndName(UUID userId, String name) {
        if (userId == null || name == null) {
            return null;
        }
        return store.findByUnique(EntityDescriptors.WEBSOCKET_CONNECTION, EntityIndex.WS_CONNECTION_USER_NAME, EntityDescriptors.userAndName(userId, name));
    }

    private record OfUser(UUID userId) implements Predicate<ProjectedEntity> {

        @Override
        public boolean test(ProjectedEntity entity) {
            return userId != null && userId.equals(entity.filterValues().get("userId"));
        }

    }

}
