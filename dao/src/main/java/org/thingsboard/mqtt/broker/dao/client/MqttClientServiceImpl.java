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

import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.common.data.MqttClient;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.dao.service.DataValidator;
import org.thingsboard.mqtt.broker.dao.util.exception.DbExceptionUtil;

import java.util.Optional;

@Service
@Slf4j
public class MqttClientServiceImpl implements MqttClientService {

    @Autowired
    private MqttClientDao mqttClientDao;

    @Override
    public MqttClient saveMqttClient(MqttClient mqttClient) {
        log.trace("Executing saveMqttClient [{}]", mqttClient);
        mqttClientValidator.validate(mqttClient);
        try {
            return mqttClientDao.save(mqttClient);
        } catch (Exception t) {
            ConstraintViolationException e = DbExceptionUtil.extractConstraintViolationException(t).orElse(null);
            if (e != null && e.getConstraintName() != null
                    && e.getConstraintName().equalsIgnoreCase("mqtt_client_id_unq_key")) {
                throw new DataValidationException("Specified client id is already registered!");
            } else {
                throw t;
            }
        }
    }

    @Override
    public Optional<MqttClient> getMqttClient(String clientId) {
        log.trace("Executing getMqttClient [{}]", clientId);
        return Optional.ofNullable(mqttClientDao.findByClientId(clientId));
    }

    private final DataValidator<MqttClient> mqttClientValidator =
            new DataValidator<>() {
                @Override
                protected void validateUpdate(MqttClient mqttClient) {
                    MqttClient old = mqttClientDao.findById(mqttClient.getId());
                    if (old == null) {
                        throw new DataValidationException("Can't update non existing MQTT client!");
                    }
                }

                @Override
                protected void validateDataImpl(MqttClient mqttClient) {
                    if (StringUtils.isEmpty(mqttClient.getName()) || mqttClient.getName().trim().length() == 0) {
                        throw new DataValidationException("MQTT client name should be specified!");
                    }
                    if (mqttClient.getType() == null) {
                        throw new DataValidationException("The client type should be specified!");
                    }
                    if (mqttClient.getCreatedBy() == null) {
                        throw new DataValidationException("The ID of the admin that created this client should be specified!");
                    }
                }
            };
}
