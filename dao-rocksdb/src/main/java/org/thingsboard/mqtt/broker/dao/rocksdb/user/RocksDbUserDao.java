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
package org.thingsboard.mqtt.broker.dao.rocksdb.user;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityIndex;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.RocksEntityStore;
import org.thingsboard.mqtt.broker.dao.user.UserDao;

import java.util.List;
import java.util.UUID;

/**
 * The users of the standalone edition, on the entity store. The email is unique in the store as in the SQL table;
 * {@code UserService} lower-cases it when logins are case-insensitive. Pages are searched by the email and sorted by
 * {@code id}, {@code createdTime}, {@code authority}, {@code email}, {@code firstName} or {@code lastName}. Removing a
 * user removes its WebSocket connections and their subscriptions in the same batch, as the SQL foreign keys cascade.
 */
@Component
@StandaloneComponent
public class RocksDbUserDao implements UserDao {

    private final RocksEntityStore store;

    public RocksDbUserDao(RocksEntityStore store) {
        this.store = store;
    }

    @Override
    public List<User> find() {
        return store.findAll(EntityDescriptors.USER);
    }

    @Override
    public User findById(UUID id) {
        return store.findById(EntityDescriptors.USER, id);
    }

    @Override
    public ListenableFuture<User> findByIdAsync(UUID id) {
        return Futures.immediateFuture(findById(id));
    }

    @Override
    public User save(User user) {
        return store.save(EntityDescriptors.USER, user);
    }

    /**
     * @return {@code true} whether or not the user existed, as the SQL DAO answers
     */
    @Override
    public boolean removeById(UUID id) {
        store.removeById(EntityDescriptors.USER, id);
        return true;
    }

    @Override
    public User findByEmail(String email) {
        return store.findByUnique(EntityDescriptors.USER, EntityIndex.USER_EMAIL, email);
    }

    @Override
    public PageData<User> findAll(PageLink pageLink) {
        return store.findPage(EntityDescriptors.USER, pageLink, null);
    }

}
