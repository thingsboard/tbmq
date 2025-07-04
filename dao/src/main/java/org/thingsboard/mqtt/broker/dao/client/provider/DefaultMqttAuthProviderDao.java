/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao.client.provider;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.dao.AbstractDao;
import org.thingsboard.mqtt.broker.dao.DaoUtil;
import org.thingsboard.mqtt.broker.dao.model.MqttAuthProviderEntity;

import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultMqttAuthProviderDao extends AbstractDao<MqttAuthProviderEntity, MqttAuthProvider>
        implements MqttAuthProviderDao {

    private final MqttAuthProviderRepository mqttAuthProviderRepository;

    @Override
    protected Class<MqttAuthProviderEntity> getEntityClass() {
        return MqttAuthProviderEntity.class;
    }

    @Override
    protected CrudRepository<MqttAuthProviderEntity, UUID> getCrudRepository() {
        return mqttAuthProviderRepository;
    }

    @Override
    public PageData<MqttAuthProvider> findAll(PageLink pageLink) {
        log.trace("Trying to find all MQTT client auth providers, pageLink {}", pageLink);
        return DaoUtil.toPageData(mqttAuthProviderRepository.findAll(
                Objects.toString(pageLink.getTextSearch(), ""),
                DaoUtil.toPageable(pageLink)));
    }

    @Override
    public MqttAuthProvider findByType(MqttAuthProviderType type) {
        return DaoUtil.getData(mqttAuthProviderRepository.findByType(type));
    }

    @Override
    public PageData<MqttAuthProvider> findAllEnabled(PageLink pageLink) {
        log.trace("Trying to find all enabled MQTT client auth providers, pageLink {}", pageLink);
        return DaoUtil.toPageData(mqttAuthProviderRepository.findAllEnabled(
                Objects.toString(pageLink.getTextSearch(), ""),
                DaoUtil.toPageable(pageLink)));
    }

    @Override
    @Transactional
    public boolean enableById(UUID id) {
        log.trace("[{}] Trying to enable MQTT client auth provider!", id);
        return changeEnabledState(id, true);
    }

    @Override
    @Transactional
    public boolean disableById(UUID id) {
        log.trace("[{}] Trying to disable MQTT client auth provider!", id);
        return changeEnabledState(id, false);
    }

    private boolean changeEnabledState(UUID id, boolean newValue) {
        return mqttAuthProviderRepository.updateEnabled(id, newValue) == 1;
    }
}
