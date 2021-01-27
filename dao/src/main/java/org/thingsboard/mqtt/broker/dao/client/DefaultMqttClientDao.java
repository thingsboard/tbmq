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
import org.thingsboard.mqtt.broker.common.data.MqttClient;
import org.thingsboard.mqtt.broker.dao.AbstractDao;
import org.thingsboard.mqtt.broker.dao.DaoUtil;
import org.thingsboard.mqtt.broker.dao.model.MqttClientEntity;

import java.util.UUID;

@Component
public class DefaultMqttClientDao extends AbstractDao<MqttClientEntity, MqttClient> implements MqttClientDao {
    @Autowired
    private MqttClientRepository mqttClientRepository;

    @Override
    protected Class<MqttClientEntity> getEntityClass() {
        return MqttClientEntity.class;
    }

    @Override
    protected CrudRepository<MqttClientEntity, UUID> getCrudRepository() {
        return mqttClientRepository;
    }

    @Override
    public MqttClient findByClientId(String clientId) {
        return DaoUtil.getData(mqttClientRepository.findByClientId(clientId));
    }
}
