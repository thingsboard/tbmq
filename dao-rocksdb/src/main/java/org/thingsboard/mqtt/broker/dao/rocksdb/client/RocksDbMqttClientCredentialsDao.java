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
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ClientCredentialsQuery;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsDao;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityIndex;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.RocksEntityStore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * The MQTT client credentials of the standalone edition, on the entity store. Pages are searched by the name and sorted
 * by {@code id}, {@code createdTime}, {@code credentialsId}, {@code name}, {@code clientType} or {@code credentialsType};
 * {@link #findAllV2} filters as {@link CredentialsQueryFilter} describes.
 */
@Component
@StandaloneComponent
public class RocksDbMqttClientCredentialsDao implements MqttClientCredentialsDao {

    private final RocksEntityStore store;

    public RocksDbMqttClientCredentialsDao(RocksEntityStore store) {
        this.store = store;
    }

    @Override
    public List<MqttClientCredentials> find() {
        return store.findAll(EntityDescriptors.MQTT_CLIENT_CREDENTIALS);
    }

    @Override
    public MqttClientCredentials findById(UUID id) {
        return store.findById(EntityDescriptors.MQTT_CLIENT_CREDENTIALS, id);
    }

    @Override
    public ListenableFuture<MqttClientCredentials> findByIdAsync(UUID id) {
        return Futures.immediateFuture(findById(id));
    }

    @Override
    public MqttClientCredentials save(MqttClientCredentials credentials) {
        return store.save(EntityDescriptors.MQTT_CLIENT_CREDENTIALS, credentials);
    }

    /**
     * @return {@code true} whether or not the credentials existed, as the SQL DAO answers
     */
    @Override
    public boolean removeById(UUID id) {
        store.removeById(EntityDescriptors.MQTT_CLIENT_CREDENTIALS, id);
        return true;
    }

    @Override
    public MqttClientCredentials findByCredentialsId(String credentialsId) {
        return store.findByUnique(EntityDescriptors.MQTT_CLIENT_CREDENTIALS, EntityIndex.CREDENTIALS_ID, credentialsId);
    }

    /**
     * One lookup per distinct id; an id without credentials is left out, as by the SQL {@code IN}.
     */
    @Override
    public List<MqttClientCredentials> findAllByCredentialsIds(List<String> credentialIds) {
        if (credentialIds == null) {
            return List.of();
        }
        List<MqttClientCredentials> found = new ArrayList<>();
        for (String credentialsId : new LinkedHashSet<>(credentialIds)) {
            MqttClientCredentials credentials = findByCredentialsId(credentialsId);
            if (credentials != null) {
                found.add(credentials);
            }
        }
        return found;
    }

    @Override
    public MqttClientCredentials findSystemWebSocketCredentials() {
        return findCredentialsByName(BrokerConstants.WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_NAME);
    }

    /**
     * The name is not unique: the first credentials of the name in id order, where the SQL query expects one row.
     */
    @Override
    public MqttClientCredentials findCredentialsByName(String name) {
        return store.findByIndex(EntityDescriptors.MQTT_CLIENT_CREDENTIALS, EntityIndex.CREDENTIALS_NAME, name)
                .stream().findFirst().orElse(null);
    }

    @Override
    public PageData<MqttClientCredentials> findAll(PageLink pageLink) {
        return store.findPage(EntityDescriptors.MQTT_CLIENT_CREDENTIALS, pageLink, null);
    }

    @Override
    public PageData<MqttClientCredentials> findAllV2(ClientCredentialsQuery query) {
        return store.findPage(EntityDescriptors.MQTT_CLIENT_CREDENTIALS, query.getPageLink(), CredentialsQueryFilter.of(query));
    }

    @Override
    public boolean existsByCredentialsType(ClientCredentialsType credentialsType) {
        return credentialsType != null
                && store.existsByIndex(EntityDescriptors.MQTT_CLIENT_CREDENTIALS, EntityIndex.CREDENTIALS_TYPE, credentialsType.name());
    }

    @Override
    public List<MqttClientCredentials> findByCredentialsType(ClientCredentialsType type) {
        return type == null ? List.of()
                : store.findByIndex(EntityDescriptors.MQTT_CLIENT_CREDENTIALS, EntityIndex.CREDENTIALS_TYPE, type.name());
    }

}
