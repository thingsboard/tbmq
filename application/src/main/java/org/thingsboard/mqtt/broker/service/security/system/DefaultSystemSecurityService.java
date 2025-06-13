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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.passay.CharacterRule;
import org.passay.EnglishCharacterData;
import org.passay.LengthRule;
import org.passay.PasswordData;
import org.passay.PasswordValidator;
import org.passay.Rule;
import org.passay.RuleResult;
import org.passay.WhitespaceRule;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.SysAdminSettingType;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.common.data.security.model.SecuritySettings;
import org.thingsboard.mqtt.broker.common.data.security.model.UserPasswordPolicy;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.dao.user.UserServiceImpl;
import org.thingsboard.mqtt.broker.exception.DataValidationException;
import org.thingsboard.mqtt.broker.service.security.exception.UserPasswordExpiredException;
import org.thingsboard.mqtt.broker.util.MiscUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class DefaultSystemSecurityService implements SystemSecurityService {

    private final UserService userService;
    private final BCryptPasswordEncoder encoder;
    private final AdminSettingsService adminSettingsService;

    public DefaultSystemSecurityService(UserService userService, @Lazy BCryptPasswordEncoder encoder, AdminSettingsService adminSettingsService) {
        this.userService = userService;
        this.encoder = encoder;
        this.adminSettingsService = adminSettingsService;
    }

    @Override
    public SecuritySettings getSecuritySettings() {
        SecuritySettings securitySettings;
        AdminSettings adminSettings = adminSettingsService.findAdminSettingsByKey(SysAdminSettingType.SECURITY_SETTINGS.getKey());
        if (adminSettings != null) {
            try {
                securitySettings = JacksonUtil.convertValue(adminSettings.getJsonValue(), SecuritySettings.class);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load security settings!", e);
            }
        } else {
            securitySettings = new SecuritySettings();
            securitySettings.setPasswordPolicy(new UserPasswordPolicy());
            securitySettings.getPasswordPolicy().setMinimumLength(6);
            securitySettings.getPasswordPolicy().setMaximumLength(72);
        }
        return securitySettings;
    }

    @Override
    public SecuritySettings saveSecuritySettings(SecuritySettings securitySettings) {
        AdminSettings adminSettings = adminSettingsService.findAdminSettingsByKey(SysAdminSettingType.SECURITY_SETTINGS.getKey());
        if (adminSettings == null) {
            adminSettings = new AdminSettings();
            adminSettings.setKey(SysAdminSettingType.SECURITY_SETTINGS.getKey());
        }
        adminSettings.setJsonValue(JacksonUtil.valueToTree(securitySettings));
        AdminSettings savedAdminSettings = adminSettingsService.saveAdminSettings(adminSettings);
        try {
            return JacksonUtil.convertValue(savedAdminSettings.getJsonValue(), SecuritySettings.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load security settings!", e);
        }
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

        SecuritySettings securitySettings = getSecuritySettings();
        if (isPositiveInteger(securitySettings.getPasswordPolicy().getPasswordExpirationPeriodDays())) {
            if ((userCredentials.getCreatedTime()
                 + TimeUnit.DAYS.toMillis(securitySettings.getPasswordPolicy().getPasswordExpirationPeriodDays()))
                < System.currentTimeMillis()) {
                userCredentials = userService.requestExpiredPasswordReset(userCredentials.getId());
                throw new UserPasswordExpiredException("User password expired!", userCredentials.getResetToken());
            }
        }
    }

    @Override
    public void validatePassword(String password, UserCredentials userCredentials) throws DataValidationException {
        SecuritySettings securitySettings = getSecuritySettings();
        UserPasswordPolicy passwordPolicy = securitySettings.getPasswordPolicy();

        validatePasswordByPolicy(password, passwordPolicy);

        if (userCredentials != null && isPositiveInteger(passwordPolicy.getPasswordReuseFrequencyDays())) {
            long passwordReuseFrequencyTs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(passwordPolicy.getPasswordReuseFrequencyDays());
            User user = userService.findUserById(userCredentials.getUserId());
            JsonNode additionalInfo = user.getAdditionalInfo();
            if (additionalInfo instanceof ObjectNode && additionalInfo.has(UserServiceImpl.USER_PASSWORD_HISTORY)) {
                JsonNode userPasswordHistoryJson = additionalInfo.get(UserServiceImpl.USER_PASSWORD_HISTORY);
                Map<String, String> userPasswordHistoryMap = JacksonUtil.convertValue(userPasswordHistoryJson, new TypeReference<>() {
                });
                for (Map.Entry<String, String> entry : userPasswordHistoryMap.entrySet()) {
                    if (encoder.matches(password, entry.getValue()) && Long.parseLong(entry.getKey()) > passwordReuseFrequencyTs) {
                        throw new DataValidationException("Password was already used for the last " + passwordPolicy.getPasswordReuseFrequencyDays() + " days");
                    }
                }
            }
        }
    }

    @Override
    public void validatePasswordByPolicy(String password, UserPasswordPolicy passwordPolicy) {
        List<Rule> passwordRules = new ArrayList<>();

        Integer maximumLength = passwordPolicy.getMaximumLength();
        Integer minLengthBound = passwordPolicy.getMinimumLength();
        int maxLengthBound = (maximumLength != null && maximumLength > passwordPolicy.getMinimumLength()) ? maximumLength : Integer.MAX_VALUE;

        passwordRules.add(new LengthRule(minLengthBound, maxLengthBound));
        if (isPositiveInteger(passwordPolicy.getMinimumUppercaseLetters())) {
            passwordRules.add(new CharacterRule(EnglishCharacterData.UpperCase, passwordPolicy.getMinimumUppercaseLetters()));
        }
        if (isPositiveInteger(passwordPolicy.getMinimumLowercaseLetters())) {
            passwordRules.add(new CharacterRule(EnglishCharacterData.LowerCase, passwordPolicy.getMinimumLowercaseLetters()));
        }
        if (isPositiveInteger(passwordPolicy.getMinimumDigits())) {
            passwordRules.add(new CharacterRule(EnglishCharacterData.Digit, passwordPolicy.getMinimumDigits()));
        }
        if (isPositiveInteger(passwordPolicy.getMinimumSpecialCharacters())) {
            passwordRules.add(new CharacterRule(EnglishCharacterData.Special, passwordPolicy.getMinimumSpecialCharacters()));
        }
        if (passwordPolicy.getAllowWhitespaces() != null && !passwordPolicy.getAllowWhitespaces()) {
            passwordRules.add(new WhitespaceRule());
        }
        PasswordValidator validator = new PasswordValidator(passwordRules);
        PasswordData passwordData = new PasswordData(password);
        RuleResult result = validator.validate(passwordData);
        if (!result.isValid()) {
            String message = String.join("\n", validator.getMessages(result));
            throw new DataValidationException(message);
        }
    }

    @Override
    public String getBaseUrl(HttpServletRequest httpServletRequest) {
        String baseUrl = null;
        AdminSettings generalSettings = adminSettingsService.findAdminSettingsByKey("general");

        JsonNode prohibitDifferentUrl = generalSettings.getJsonValue().get("prohibitDifferentUrl");

        if ((prohibitDifferentUrl != null && prohibitDifferentUrl.asBoolean()) || httpServletRequest == null) {
            baseUrl = generalSettings.getJsonValue().get("baseUrl").asText();
        }

        if (StringUtils.isEmpty(baseUrl) && httpServletRequest != null) {
            baseUrl = MiscUtils.constructBaseUrl(httpServletRequest);
        }

        return baseUrl;
    }

    private static boolean isPositiveInteger(Integer val) {
        return val != null && val > 0;
    }
}
