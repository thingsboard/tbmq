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
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.dao.service.DataValidator;
import org.thingsboard.mqtt.broker.dao.util.exception.DbExceptionUtil;
import org.thingsboard.mqtt.broker.dao.util.mapping.JacksonUtil;

@Service
@Slf4j
public class MqttClientCredentialsServiceImpl implements MqttClientCredentialsService {
    private static final String USERNAME_CREDENTIALS_ID_PREFIX = "username";
    private static final String CLIENT_ID_CREDENTIALS_ID_PREFIX = "client_id";
    private static final String MIXED_CREDENTIALS_ID_PREFIX = "mixed";

    @Autowired
    private MqttClientCredentialsDao mqttClientCredentialsDao;

    @Override
    public MqttClientCredentials saveCredentials(MqttClientCredentials mqttClientCredentials) {
        if(mqttClientCredentials.getCredentialsType() == null){
            throw new DataValidationException("MQTT Client credentials type should be specified");
        }
        if (mqttClientCredentials.getCredentialsType() == ClientCredentialsType.MQTT_BASIC) {
            processSimpleMqttCredentials(mqttClientCredentials);
        }
        log.trace("Executing saveCredentials [{}]", mqttClientCredentials);
        credentialsValidator.validate(mqttClientCredentials);
        try {
            return mqttClientCredentialsDao.save(mqttClientCredentials);
        } catch (Exception t) {
            ConstraintViolationException e = DbExceptionUtil.extractConstraintViolationException(t).orElse(null);
            if (e != null && e.getConstraintName() != null
                    && e.getConstraintName().equalsIgnoreCase("mqtt_client_credentials_id_unq_key")) {
                throw new DataValidationException("Specified credentials are already registered!");
            } else {
                throw t;
            }
        }
    }

    private void processSimpleMqttCredentials(MqttClientCredentials mqttClientCredentials) {
        BasicMqttCredentials mqttCredentials = getBasicMqttCredentials(mqttClientCredentials);
        if (StringUtils.isEmpty(mqttClientCredentials.getClientId()) && StringUtils.isEmpty(mqttCredentials.getUserName())) {
            throw new DataValidationException("Both mqtt client id and user name are empty!");
        }
        if (StringUtils.isEmpty(mqttClientCredentials.getClientId())) {
            mqttClientCredentials.setCredentialsId(USERNAME_CREDENTIALS_ID_PREFIX + mqttCredentials.getUserName());
        } else if (StringUtils.isEmpty(mqttCredentials.getUserName())) {
            mqttClientCredentials.setCredentialsId(CLIENT_ID_CREDENTIALS_ID_PREFIX + mqttClientCredentials.getClientId());
        } else {
            mqttClientCredentials.setCredentialsId(MIXED_CREDENTIALS_ID_PREFIX + mqttClientCredentials.getClientId() + "|" + mqttCredentials.getUserName());
        }
    }

    private BasicMqttCredentials getBasicMqttCredentials(MqttClientCredentials mqttClientCredentials) {
        BasicMqttCredentials mqttCredentials;
        try {
            mqttCredentials = JacksonUtil.fromString(mqttClientCredentials.getCredentialsValue(), BasicMqttCredentials.class);
            if (mqttCredentials == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new DataValidationException("Invalid credentials body for simple mqtt credentials!");
        }
        return mqttCredentials;
    }

    private final DataValidator<MqttClientCredentials> credentialsValidator =
            new DataValidator<>() {
                @Override
                protected void validateCreate(MqttClientCredentials mqttClientCredentials) {
                    if (mqttClientCredentialsDao.findByCredentialsId(mqttClientCredentials.getCredentialsId()) != null) {
                        throw new DataValidationException("Such MQTT Client credentials are already created!");
                    }
                }

                @Override
                protected void validateUpdate(MqttClientCredentials mqttClientCredentials) {
                    if (mqttClientCredentialsDao.findById(mqttClientCredentials.getId()) == null) {
                        throw new DataValidationException("Unable to update non-existent MQTT Client credentials!");
                    }
                    MqttClientCredentials existingCredentials = mqttClientCredentialsDao.findByCredentialsId(mqttClientCredentials.getCredentialsId());
                    if (existingCredentials != null && !existingCredentials.getId().equals(mqttClientCredentials.getId())) {
                        throw new DataValidationException("New MQTT Client credentials are already created!");
                    }
                }

                @Override
                protected void validateDataImpl(MqttClientCredentials mqttClientCredentials) {
                    if (mqttClientCredentials.getCredentialsType() == null) {
                        throw new DataValidationException("MQTT Client credentials type should be specified!");
                    }
                    if (StringUtils.isEmpty(mqttClientCredentials.getCredentialsId())) {
                        throw new DataValidationException("MQTT Client credentials id should be specified!");
                    }
                }
            };

}
