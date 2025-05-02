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
import org.thingsboard.mqtt.broker.common.data.dto.ShortMqttClientAuthProvider;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientAuthProvider;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttClientAuthProviderService;
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
public class MqttClientAuthProviderServiceImpl implements MqttClientAuthProviderService {

    private final MqttClientAuthProviderDao mqttClientAuthProviderDao;

    @Override
    public MqttClientAuthProvider saveAuthProvider(MqttClientAuthProvider authProvider) {
        log.trace("Executing saveAuthProvider [{}]", authProvider);
        authProviderValidator.validate(authProvider);
        try {
            return mqttClientAuthProviderDao.save(authProvider);
        } catch (Exception e) {
            ConstraintViolationException ex = DbExceptionUtil.extractConstraintViolationException(e).orElse(null);
            if (ex != null && ex.getConstraintName() != null
                && ex.getConstraintName().equalsIgnoreCase("mqtt_client_auth_provider_type_key")) {
                throw new DataValidationException("MQTT client auth provider with such type already registered!");
            }
            throw e;
        }
    }

    @Override
    public Optional<MqttClientAuthProvider> getAuthProviderById(UUID id) {
        log.trace("Executing getAuthProviderById [{}]", id);
        return Optional.ofNullable(mqttClientAuthProviderDao.findById(id));
    }

    @Override
    public void deleteAuthProvider(UUID id) {
        log.trace("Executing deleteAuthProvider [{}]", id);
        var authProvider = mqttClientAuthProviderDao.findById(id);
        if (authProvider == null) {
            return;
        }
        mqttClientAuthProviderDao.removeById(id);
    }

    @Override
    public PageData<ShortMqttClientAuthProvider> getAuthProviders(PageLink pageLink) {
        log.trace("Executing getAuthProviders, pageLink [{}]", pageLink);
        validatePageLink(pageLink);
        PageData<MqttClientAuthProvider> pageData = mqttClientAuthProviderDao.findAll(pageLink);
        var shortMqttClientAuthProviders = pageData.getData().stream()
                .map(MqttClientAuthProviderServiceImpl::toShortMqttClientAuthProvider).collect(Collectors.toList());
        return new PageData<>(shortMqttClientAuthProviders, pageData.getTotalPages(), pageData.getTotalElements(), pageData.hasNext());
    }

    private static ShortMqttClientAuthProvider toShortMqttClientAuthProvider(MqttClientAuthProvider mqttClientAuthProvider) {
        return new ShortMqttClientAuthProvider(mqttClientAuthProvider.getId(), mqttClientAuthProvider.getType(), mqttClientAuthProvider.getCreatedTime());
    }

    private final DataValidator<MqttClientAuthProvider> authProviderValidator =
            new DataValidator<>() {

                @Override
                protected void validateUpdate(MqttClientAuthProvider updated) {
                    MqttClientAuthProvider existing = mqttClientAuthProviderDao.findById(updated.getId());
                    if (existing == null) {
                        throw new DataValidationException("Unable to update non-existent MQTT Client auth provider!");
                    }
                    if (existing.getType() != updated.getType()) {
                        throw new DataValidationException("MQTT client auth provider type can't be changed!");
                    }
                }

                @Override
                protected void validateDataImpl(MqttClientAuthProvider authProvider) {
                    if (authProvider.getType() == null) {
                        throw new DataValidationException("MQTT Client auth provider type should be specified!");
                    }
                    if (authProvider.getMqttClientAuthProviderConfiguration() == null) {
                        throw new DataValidationException("MQTT Client auth provider configuration should be specified!");
                    }
                    authProvider.getMqttClientAuthProviderConfiguration().validate();
                }
            };

}
