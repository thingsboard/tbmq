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
package org.thingsboard.mqtt.broker.dao.rocksdb.client;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderDao;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityIndex;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.RocksEntityStore;

import java.util.List;
import java.util.UUID;

/**
 * The MQTT auth providers of the standalone edition, on the entity store. Pages are searched by the type name and
 * sorted by {@code type}, {@code enabled}, {@code id} or {@code createdTime}.
 */
@Component
@StandaloneComponent
public class RocksDbMqttAuthProviderDao implements MqttAuthProviderDao {

    private static final String ENABLED = "enabled";

    private final RocksEntityStore store;
    /**
     * Serializes the read-modify-write of {@link #enableById} and {@link #disableById} with the other writes of this DAO.
     */
    private final Object writeLock = new Object();

    public RocksDbMqttAuthProviderDao(RocksEntityStore store) {
        this.store = store;
    }

    @Override
    public List<MqttAuthProvider> find() {
        return store.findAll(EntityDescriptors.MQTT_AUTH_PROVIDER);
    }

    @Override
    public MqttAuthProvider findById(UUID id) {
        return store.findById(EntityDescriptors.MQTT_AUTH_PROVIDER, id);
    }

    @Override
    public ListenableFuture<MqttAuthProvider> findByIdAsync(UUID id) {
        return Futures.immediateFuture(findById(id));
    }

    @Override
    public MqttAuthProvider save(MqttAuthProvider authProvider) {
        synchronized (writeLock) {
            return store.save(EntityDescriptors.MQTT_AUTH_PROVIDER, authProvider);
        }
    }

    /**
     * @return {@code true} whether or not the provider existed, as the SQL DAO answers
     */
    @Override
    public boolean removeById(UUID id) {
        synchronized (writeLock) {
            store.removeById(EntityDescriptors.MQTT_AUTH_PROVIDER, id);
        }
        return true;
    }

    @Override
    public PageData<MqttAuthProvider> findAll(PageLink pageLink) {
        return store.findPage(EntityDescriptors.MQTT_AUTH_PROVIDER, pageLink, null);
    }

    @Override
    public MqttAuthProvider findByType(MqttAuthProviderType type) {
        return type == null ? null : store.findByUnique(EntityDescriptors.MQTT_AUTH_PROVIDER, EntityIndex.AUTH_PROVIDER_TYPE, type.name());
    }

    @Override
    public PageData<MqttAuthProvider> findAllEnabled(PageLink pageLink) {
        return store.findPage(EntityDescriptors.MQTT_AUTH_PROVIDER, pageLink,
                projected -> Boolean.TRUE.equals(projected.filterValues().get(ENABLED)));
    }

    @Override
    public boolean enableById(UUID id) {
        return changeEnabledState(id, true);
    }

    @Override
    public boolean disableById(UUID id) {
        return changeEnabledState(id, false);
    }

    /**
     * @return whether the provider exists, as the matched-row count of the SQL update
     */
    private boolean changeEnabledState(UUID id, boolean enabled) {
        synchronized (writeLock) {
            MqttAuthProvider authProvider = findById(id);
            if (authProvider == null) {
                return false;
            }
            authProvider.setEnabled(enabled);
            store.save(EntityDescriptors.MQTT_AUTH_PROVIDER, authProvider);
            return true;
        }
    }

}
