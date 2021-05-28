/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.AbstractDao;
import org.thingsboard.mqtt.broker.dao.DaoUtil;
import org.thingsboard.mqtt.broker.dao.model.MqttClientCredentialsEntity;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class DefaultMqttClientCredentialsDao extends AbstractDao<MqttClientCredentialsEntity, MqttClientCredentials> implements MqttClientCredentialsDao {

    @Autowired
    private MqttClientCredentialsRepository mqttClientCredentialsRepository;

    @Override
    protected Class<MqttClientCredentialsEntity> getEntityClass() {
        return MqttClientCredentialsEntity.class;
    }

    @Override
    protected CrudRepository<MqttClientCredentialsEntity, UUID> getCrudRepository() {
        return mqttClientCredentialsRepository;
    }

    @Override
    public MqttClientCredentials findByCredentialsId(String credentialsId) {
        return DaoUtil.getData(mqttClientCredentialsRepository.findByCredentialsId(credentialsId));
    }

    @Override
    public List<MqttClientCredentials> findAllByCredentialsIds(List<String> credentialIds) {
        return mqttClientCredentialsRepository.findByCredentialsIdIn(credentialIds).stream()
                .map(DaoUtil::getData)
                .collect(Collectors.toList());
    }

    @Override
    public PageData<MqttClientCredentials> findAll(PageLink pageLink) {
        return DaoUtil.toPageData(mqttClientCredentialsRepository.findAll(DaoUtil.toPageable(pageLink)));
    }
}
