/**
 * Copyright © 2016-2023 The Thingsboard Authors
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

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.StringUtils;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.util.MiscUtils;

import javax.servlet.http.HttpServletRequest;

@Service
public class DefaultSystemSecurityService implements SystemSecurityService {

    private final UserService userService;
    private final BCryptPasswordEncoder encoder;
    private final AdminSettingsService adminSettingsService;

    public DefaultSystemSecurityService(UserService userService, BCryptPasswordEncoder encoder, AdminSettingsService adminSettingsService) {
        this.userService = userService;
        this.encoder = encoder;
        this.adminSettingsService = adminSettingsService;
    }

    @Override
    public void validateUserCredentials(UserCredentials userCredentials, String username, String password) throws AuthenticationException {
        if (!encoder.matches(password, userCredentials.getPassword())) {
            throw new BadCredentialsException("Authentication Failed. Username or Password not valid.");
        }

        if (!userCredentials.isEnabled()) {
            throw new DisabledException("User is not active");
        }

        userService.onUserLoginSuccessful(userCredentials.getUserId());
    }

    @Override
    public void validatePassword(String password) throws DataValidationException {
        // TODO: 17.11.22 improve password validation
        if (StringUtils.isEmpty(password)) {
            throw new DataValidationException("Password can not be empty!");
        }
        if (password.length() <= 5) {
            throw new DataValidationException("Password should be longer than 5 characters!");
        }
    }

    @Override
    public String getBaseUrl(HttpServletRequest httpServletRequest) {
        String baseUrl = null;
        AdminSettings generalSettings = adminSettingsService.findAdminSettingsByKey("general");

        JsonNode prohibitDifferentUrl = generalSettings.getJsonValue().get("prohibitDifferentUrl");

        if (prohibitDifferentUrl != null && prohibitDifferentUrl.asBoolean()) {
            baseUrl = generalSettings.getJsonValue().get("baseUrl").asText();
        }

        if (StringUtils.isEmpty(baseUrl)) {
            baseUrl = MiscUtils.constructBaseUrl(httpServletRequest);
        }

        return baseUrl;
    }
}
