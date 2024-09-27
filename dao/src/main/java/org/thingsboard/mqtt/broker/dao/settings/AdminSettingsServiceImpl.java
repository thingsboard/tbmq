/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.util.HostNameValidator;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.connectivity.ConnectivityInfo;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.dao.service.DataValidator;
import org.thingsboard.mqtt.broker.dao.service.Validator;

import java.util.Map;
import java.util.UUID;


@Service
@Slf4j
public class AdminSettingsServiceImpl implements AdminSettingsService {

    private final AdminSettingsDao adminSettingsDao;


    public AdminSettingsServiceImpl(AdminSettingsDao adminSettingsDao) {
        this.adminSettingsDao = adminSettingsDao;
    }


    @Override
    public AdminSettings findAdminSettingsById(UUID adminSettingsId) {
        if (log.isTraceEnabled()) {
            log.trace("Executing findAdminSettingsById [{}]", adminSettingsId);
        }
        Validator.validateId(adminSettingsId, "Incorrect adminSettingsId " + adminSettingsId);
        return adminSettingsDao.findById(adminSettingsId);
    }

    @Override
    public AdminSettings findAdminSettingsByKey(String key) {
        if (log.isTraceEnabled()) {
            log.trace("Executing findAdminSettingsByKey [{}]", key);
        }
        Validator.validateString(key, "Incorrect key " + key);
        return adminSettingsDao.findByKey(key);
    }

    @Override
    public AdminSettings saveAdminSettings(AdminSettings adminSettings) {
        if (log.isTraceEnabled()) {
            log.trace("Executing saveAdminSettings [{}]", adminSettings);
        }
        adminSettingsDataValidator.validate(adminSettings);
        if (adminSettings.getKey().equals("mail") && !adminSettings.getJsonValue().has("password")) {
            AdminSettings mailSettings = findAdminSettingsByKey("mail");
            if (mailSettings != null) {
                ((ObjectNode) adminSettings.getJsonValue()).put("password", mailSettings.getJsonValue().get("password").asText());
            }
        }
        return adminSettingsDao.save(adminSettings);
    }

    @Override
    public void deleteAdminSettingsByKey(String key) {
        if (log.isTraceEnabled()) {
            log.trace("Executing deleteAdminSettings, key [{}]", key);
        }
        Validator.validateString(key, "Incorrect key " + key);
        adminSettingsDao.removeByKey(key);
    }

    private final DataValidator<AdminSettings> adminSettingsDataValidator =
            new DataValidator<>() {
                @Override
                protected void validateCreate(AdminSettings adminSettings) {
                    if (adminSettingsDao.findByKey(adminSettings.getKey()) != null) {
                        throw new DataValidationException("Admin settings with such key is already created!");
                    }
                }

                @Override
                protected void validateUpdate(AdminSettings adminSettings) {
                    AdminSettings existentAdminSettings = adminSettingsDao.findById(adminSettings.getId());
                    if (existentAdminSettings == null) {
                        throw new DataValidationException("Unable to update non-existent Admin Settings!");
                    }
                    if (!existentAdminSettings.getKey().equals(adminSettings.getKey())) {
                        throw new DataValidationException("Updating key of admin settings is prohibited!");
                    }
                }

                @Override
                protected void validateDataImpl(AdminSettings adminSettings) {
                    if (StringUtils.isEmpty(adminSettings.getKey())) {
                        throw new DataValidationException("Admin Settings key should be specified!");
                    }
                    if (adminSettings.getJsonValue() == null) {
                        throw new DataValidationException("Admin Settings json should be specified!");
                    }
                    if (adminSettings.getKey().equals(BrokerConstants.CONNECTIVITY_KEY)) {
                        Map<String, ConnectivityInfo> connectivityInfoMap = JacksonUtil.convertValue(adminSettings.getJsonValue(), new TypeReference<>() {
                        });
                        if (connectivityInfoMap != null) {
                            connectivityInfoMap.forEach((key, value) -> {
                                if (value.isEnabled() && !HostNameValidator.isValidHostName(value.getHost())) {
                                    throw new DataValidationException("Invalid host name found: " + value.getHost());
                                }
                            });
                        }
                    }
                }
            };
}
