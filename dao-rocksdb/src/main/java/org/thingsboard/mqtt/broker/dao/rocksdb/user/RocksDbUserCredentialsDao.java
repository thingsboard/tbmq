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
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityIndex;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.RocksEntityStore;
import org.thingsboard.mqtt.broker.dao.user.UserCredentialsDao;

import java.util.List;
import java.util.UUID;

/**
 * The user credentials of the standalone edition, on the entity store. The user id, the activate token and the reset
 * token are each unique, as in the SQL table. {@link #replace} is one batch.
 */
@Component
@StandaloneComponent
public class RocksDbUserCredentialsDao implements UserCredentialsDao {

    private final RocksEntityStore store;

    public RocksDbUserCredentialsDao(RocksEntityStore store) {
        this.store = store;
    }

    @Override
    public List<UserCredentials> find() {
        return store.findAll(EntityDescriptors.USER_CREDENTIALS);
    }

    @Override
    public UserCredentials findById(UUID id) {
        return store.findById(EntityDescriptors.USER_CREDENTIALS, id);
    }

    @Override
    public ListenableFuture<UserCredentials> findByIdAsync(UUID id) {
        return Futures.immediateFuture(findById(id));
    }

    @Override
    public UserCredentials save(UserCredentials credentials) {
        return store.save(EntityDescriptors.USER_CREDENTIALS, credentials);
    }

    /**
     * @return {@code true} whether or not the credentials existed, as the SQL DAO answers
     */
    @Override
    public boolean removeById(UUID id) {
        store.removeById(EntityDescriptors.USER_CREDENTIALS, id);
        return true;
    }

    @Override
    public UserCredentials findByUserId(UUID userId) {
        return userId == null ? null
                : store.findByUnique(EntityDescriptors.USER_CREDENTIALS, EntityIndex.USER_CREDENTIALS_USER_ID, userId.toString());
    }

    @Override
    public UserCredentials findByActivateToken(String activateToken) {
        return store.findByUnique(EntityDescriptors.USER_CREDENTIALS, EntityIndex.USER_CREDENTIALS_ACTIVATE_TOKEN, activateToken);
    }

    @Override
    public UserCredentials findByResetToken(String resetToken) {
        return store.findByUnique(EntityDescriptors.USER_CREDENTIALS, EntityIndex.USER_CREDENTIALS_RESET_TOKEN, resetToken);
    }

    @Override
    public UserCredentials replace(UUID removedId, UserCredentials replacement) {
        return store.removeAndSave(EntityDescriptors.USER_CREDENTIALS, removedId, replacement);
    }

}
