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
package org.thingsboard.mqtt.broker.controller;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.common.data.security.model.SecuritySettings;
import org.thingsboard.mqtt.broker.common.data.security.model.UserPasswordPolicy;
import org.thingsboard.mqtt.broker.service.mail.MailService;
import org.thingsboard.mqtt.broker.service.security.model.ChangePasswordRequest;
import org.thingsboard.mqtt.broker.service.security.model.JwtTokenPair;
import org.thingsboard.mqtt.broker.service.security.model.ResetPasswordEmailRequest;
import org.thingsboard.mqtt.broker.service.security.model.ResetPasswordRequest;
import org.thingsboard.mqtt.broker.service.security.model.SecurityUser;
import org.thingsboard.mqtt.broker.service.security.model.UserPrincipal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Slf4j
public class AuthController extends BaseController {

    private final BCryptPasswordEncoder passwordEncoder;
    private final MailService mailService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping(value = "/auth/user")
    public User getUser() throws ThingsboardException {
        SecurityUser securityUser = getCurrentUser();
        return filterSensitiveUserData(checkNotNull(userService.findUserById(securityUser.getId())));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/auth/changePassword")
    public JwtTokenPair changePassword(@RequestBody ChangePasswordRequest changePasswordRequest) throws ThingsboardException {
        SecurityUser securityUser = getCurrentUser();
        UserCredentials userCredentials = userService.findUserCredentialsByUserId(securityUser.getId());

        validatePassword(passwordEncoder, changePasswordRequest, userCredentials.getPassword());
        systemSecurityService.validatePassword(changePasswordRequest.getNewPassword(), userCredentials);

        userCredentials.setPassword(passwordEncoder.encode(changePasswordRequest.getNewPassword()));
        userCredentials.activateUserCredentials();
        userService.replaceUserCredentials(userCredentials);

        return tokenFactory.createTokenPair(securityUser);
    }

    @PostMapping(value = "/noauth/resetPasswordByEmail")
    public void requestResetPasswordByEmail(@RequestBody ResetPasswordEmailRequest resetPasswordByEmailRequest, HttpServletRequest request) {
        try {
            String email = resetPasswordByEmailRequest.getEmail();
            UserCredentials userCredentials = userService.requestPasswordReset(email);
            String baseUrl = systemSecurityService.getBaseUrl(request);
            String resetUrl = String.format("%s/api/noauth/resetPassword?resetToken=%s", baseUrl,
                    userCredentials.getResetToken());
            mailService.sendResetPasswordEmailAsync(resetUrl, email);
        } catch (Exception e) {
            log.warn("Error occurred", e);
        }
    }

    @PostMapping(value = "/noauth/resetPassword")
    public JwtTokenPair resetPassword(@RequestBody ResetPasswordRequest resetPasswordRequest, HttpServletRequest request) throws ThingsboardException {
        String resetToken = resetPasswordRequest.getResetToken();
        String password = resetPasswordRequest.getPassword();
        UserCredentials userCredentials = userService.findUserCredentialsByResetToken(resetToken);
        if (userCredentials != null) {
            systemSecurityService.validatePassword(password, userCredentials);
            if (passwordEncoder.matches(password, userCredentials.getPassword())) {
                throw new ThingsboardException("New password should be different from existing!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
            }
            String encodedPassword = passwordEncoder.encode(password);
            userCredentials.setPassword(encodedPassword);
            userCredentials.setResetToken(null);
            userCredentials = userService.replaceUserCredentials(userCredentials);
            User user = userService.findUserById(userCredentials.getUserId());
            UserPrincipal principal = new UserPrincipal(user.getEmail());
            SecurityUser securityUser = new SecurityUser(user, userCredentials.isEnabled(), principal);
            String baseUrl = systemSecurityService.getBaseUrl(request);
            String loginUrl = String.format("%s/login", baseUrl);
            String email = user.getEmail();
            try {
                mailService.sendPasswordWasResetEmail(loginUrl, email);
            } catch (Exception e) {
                log.error("[{}][{}] Failed to send password reset email", loginUrl, email, e);
            }
            return tokenFactory.createTokenPair(securityUser);
        } else {
            throw new ThingsboardException("Invalid reset token!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
    }

    @GetMapping(value = "/noauth/resetPassword", params = {"resetToken"})
    public ResponseEntity<?> checkResetToken(
            @Parameter(description = "The reset token string.")
            @RequestParam(value = "resetToken") String resetToken) {
        UserCredentials userCredentials = userService.findUserCredentialsByResetToken(resetToken);
        if (userCredentials == null) {
            return response(HttpStatus.CONFLICT);
        }
        return redirectTo("/login/resetPassword?resetToken=" + resetToken);
    }

    @GetMapping(value = "/noauth/userPasswordPolicy")
    public UserPasswordPolicy getUserPasswordPolicy() throws ThingsboardException {
        SecuritySettings securitySettings =
                checkNotNull(systemSecurityService.getSecuritySettings());
        return securitySettings.getPasswordPolicy();
    }

}
