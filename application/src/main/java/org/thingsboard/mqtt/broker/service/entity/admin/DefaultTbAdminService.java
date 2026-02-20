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
package org.thingsboard.mqtt.broker.service.entity.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.SysAdminSettingType;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.security.model.SecuritySettings;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.dto.AdminDto;
import org.thingsboard.mqtt.broker.service.entity.AbstractTbEntityService;
import org.thingsboard.mqtt.broker.service.install.data.MqttAuthSettings;
import org.thingsboard.mqtt.broker.service.mail.MailService;
import org.thingsboard.mqtt.broker.service.security.system.SystemSecurityService;
import org.thingsboard.mqtt.broker.service.system.SystemSettingsNotificationService;
import org.thingsboard.mqtt.broker.service.user.AdminService;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTbAdminService extends AbstractTbEntityService implements TbAdminService {

    private final AdminService adminService;
    private final UserService userService;
    private final AdminSettingsService adminSettingsService;
    private final MailService mailService;
    private final SystemSettingsNotificationService systemSettingsNotificationService;
    private final SystemSecurityService systemSecurityService;

    @Override
    public User save(AdminDto adminDto, User currentUser) throws ThingsboardException {
        return adminService.createAdmin(adminDto, true);
    }

    @Override
    public void delete(User user, User currentUser) {
        userService.deleteUser(user.getId());
    }

    @Override
    public AdminSettings saveAdminSettings(AdminSettings adminSettings, User currentUser) {
        AdminSettings saved = adminSettingsService.saveAdminSettings(adminSettings);
        SysAdminSettingType.parse(saved.getKey()).ifPresent(type -> {
            switch (type) {
                case MAIL -> mailService.updateMailConfiguration();
                case MQTT_AUTHORIZATION -> {
                    var mqttAuthSettings = JacksonUtil.convertValue(saved.getJsonValue(), MqttAuthSettings.class);
                    systemSettingsNotificationService.onMqttAuthSettingUpdate(mqttAuthSettings);
                }
            }
        });
        return saved;
    }

    @Override
    public SecuritySettings saveSecuritySettings(SecuritySettings securitySettings, User currentUser) {
        return systemSecurityService.saveSecuritySettings(securitySettings);
    }

}
