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
package org.thingsboard.mqtt.broker.dao.rocksdb.settings;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityIndex;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.RocksEntityStore;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsDao;

import java.util.List;
import java.util.UUID;

/**
 * The admin settings of the standalone edition, on the entity store. The key is unique in the store, where the SQL
 * table leaves it to {@code AdminSettingsService}.
 */
@Component
@StandaloneComponent
public class RocksDbAdminSettingsDao implements AdminSettingsDao {

    private final RocksEntityStore store;

    public RocksDbAdminSettingsDao(RocksEntityStore store) {
        this.store = store;
    }

    @Override
    public List<AdminSettings> find() {
        return store.findAll(EntityDescriptors.ADMIN_SETTINGS);
    }

    @Override
    public AdminSettings findById(UUID id) {
        return store.findById(EntityDescriptors.ADMIN_SETTINGS, id);
    }

    @Override
    public ListenableFuture<AdminSettings> findByIdAsync(UUID id) {
        return Futures.immediateFuture(findById(id));
    }

    @Override
    public AdminSettings save(AdminSettings adminSettings) {
        return store.save(EntityDescriptors.ADMIN_SETTINGS, adminSettings);
    }

    /**
     * @return {@code true} whether or not the setting existed, as the SQL DAO answers
     */
    @Override
    public boolean removeById(UUID id) {
        store.removeById(EntityDescriptors.ADMIN_SETTINGS, id);
        return true;
    }

    @Override
    public AdminSettings findByKey(String key) {
        return store.findByUnique(EntityDescriptors.ADMIN_SETTINGS, EntityIndex.ADMIN_SETTINGS_KEY, key);
    }

    @Override
    public void removeByKey(String key) {
        AdminSettings adminSettings = findByKey(key);
        if (adminSettings != null) {
            store.removeById(EntityDescriptors.ADMIN_SETTINGS, adminSettings.getId());
        }
    }

}
