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
package org.thingsboard.mqtt.broker.dao.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.dto.ShortMqttClientAuthenticator;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientAuthenticator;
import org.thingsboard.mqtt.broker.dao.client.authenticator.MqttClientAuthenticatorService;
import org.thingsboard.mqtt.broker.dao.service.DataValidator;
import org.thingsboard.mqtt.broker.dao.util.exception.DbExceptionUtil;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.thingsboard.mqtt.broker.dao.service.Validator.validatePageLink;

@Service
@Slf4j
@RequiredArgsConstructor
public class MqttClientAuthenticatorServiceImpl implements MqttClientAuthenticatorService {

    private final MqttClientAuthenticatorDao mqttClientAuthenticatorDao;

    @Override
    public MqttClientAuthenticator saveAuthenticator(MqttClientAuthenticator authenticator) {
        log.trace("Executing saveAuthenticator [{}]", authenticator);
        authenticatorValidator.validate(authenticator);
        try {
            return mqttClientAuthenticatorDao.save(authenticator);
        } catch (Exception e) {
            ConstraintViolationException ex = DbExceptionUtil.extractConstraintViolationException(e).orElse(null);
            if (ex != null && ex.getConstraintName() != null
                && ex.getConstraintName().equalsIgnoreCase("mqtt_client_authenticator_type_key")) {
                throw new DataValidationException("MQTT client authenticator with such type already registered!");
            }
            throw e;
        }
    }

    @Override
    public Optional<MqttClientAuthenticator> getAuthenticatorById(UUID id) {
        log.trace("Executing getAuthenticatorById [{}]", id);
        return Optional.ofNullable(mqttClientAuthenticatorDao.findById(id));
    }

    @Override
    public void deleteAuthenticator(UUID id) {
        log.trace("Executing deleteAuthenticator [{}]", id);
        var authenticator = mqttClientAuthenticatorDao.findById(id);
        if (authenticator == null) {
            return;
        }
        mqttClientAuthenticatorDao.removeById(id);
    }

    @Override
    public PageData<ShortMqttClientAuthenticator> getAuthenticators(PageLink pageLink) {
        log.trace("Executing getAuthenticators, pageLink [{}]", pageLink);
        validatePageLink(pageLink);
        PageData<MqttClientAuthenticator> pageData = mqttClientAuthenticatorDao.findAll(pageLink);
        var shortMqttClientAuthenticators = pageData.getData().stream()
                .map(MqttClientAuthenticatorServiceImpl::toShortMqttClientAuthenticator).collect(Collectors.toList());
        return new PageData<>(shortMqttClientAuthenticators, pageData.getTotalPages(), pageData.getTotalElements(), pageData.hasNext());
    }

    private static ShortMqttClientAuthenticator toShortMqttClientAuthenticator(MqttClientAuthenticator mqttClientAuthenticator) {
        return new ShortMqttClientAuthenticator(mqttClientAuthenticator.getId(), mqttClientAuthenticator.getType(), mqttClientAuthenticator.getCreatedTime());
    }

    private final DataValidator<MqttClientAuthenticator> authenticatorValidator =
            new DataValidator<>() {

                @Override
                protected void validateUpdate(MqttClientAuthenticator updated) {
                    MqttClientAuthenticator existing = mqttClientAuthenticatorDao.findById(updated.getId());
                    if (existing == null) {
                        throw new DataValidationException("Unable to update non-existent MQTT Client authenticator!");
                    }
                    if (existing.getType() != updated.getType()) {
                        throw new DataValidationException("MQTT client authenticator type can't be changed!");
                    }
                }

                @Override
                protected void validateDataImpl(MqttClientAuthenticator authenticator) {
                    if (authenticator.getType() == null) {
                        throw new DataValidationException("MQTT Client authenticator type should be specified!");
                    }
                    if (authenticator.getMqttClientAuthenticatorConfiguration() == null) {
                        throw new DataValidationException("MQTT Client authenticator configuration should be specified!");
                    }
                    authenticator.getMqttClientAuthenticatorConfiguration().validate();
                }
            };

}
