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
package org.thingsboard.mqtt.broker.service.security.system;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.AuthenticationException;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.common.data.security.model.SecuritySettings;
import org.thingsboard.mqtt.broker.common.data.security.model.UserPasswordPolicy;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;

public interface SystemSecurityService {

    SecuritySettings getSecuritySettings();

    SecuritySettings saveSecuritySettings(SecuritySettings securitySettings);

    void validatePasswordByPolicy(String password, UserPasswordPolicy passwordPolicy);

    void validateUserCredentials(UserCredentials userCredentials, String username, String password) throws AuthenticationException;

    void validatePassword(String password, UserCredentials userCredentials) throws DataValidationException;

    String getBaseUrl(HttpServletRequest httpServletRequest);
}
