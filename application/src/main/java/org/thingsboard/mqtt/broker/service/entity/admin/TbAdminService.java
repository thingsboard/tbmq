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

import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.security.model.SecuritySettings;
import org.thingsboard.mqtt.broker.dto.AdminDto;

public interface TbAdminService {

    User save(AdminDto adminDto, User currentUser) throws ThingsboardException;

    void delete(User user, User currentUser);

    AdminSettings saveAdminSettings(AdminSettings adminSettings, User currentUser) throws ThingsboardException;

    SecuritySettings saveSecuritySettings(SecuritySettings securitySettings, User currentUser);

}
