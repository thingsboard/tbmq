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
package org.thingsboard.mqtt.broker.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.service.mail.MailService;
import org.thingsboard.mqtt.broker.service.security.model.ChangePasswordRequest;
import org.thingsboard.mqtt.broker.service.security.model.JwtTokenPair;
import org.thingsboard.mqtt.broker.service.security.model.ResetPasswordEmailRequest;
import org.thingsboard.mqtt.broker.service.security.model.ResetPasswordRequest;
import org.thingsboard.mqtt.broker.service.security.model.SecurityUser;
import org.thingsboard.mqtt.broker.service.security.model.UserPrincipal;
import org.thingsboard.mqtt.broker.service.security.model.token.JwtToken;
import org.thingsboard.mqtt.broker.service.security.model.token.JwtTokenFactory;
import org.thingsboard.mqtt.broker.service.security.system.SystemSecurityService;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Slf4j
public class AuthController extends BaseController {
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenFactory tokenFactory;
    private final SystemSecurityService systemSecurityService;
    private final MailService mailService;


    @PreAuthorize("isAuthenticated()")
    @RequestMapping(value = "/auth/user", method = RequestMethod.GET)
    @ResponseBody
    public User getUser() throws ThingsboardException {
        try {
            SecurityUser securityUser = getCurrentUser();
            return userService.findUserById(securityUser.getId());
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("isAuthenticated()")
    @RequestMapping(value = "/auth/changePassword", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public ObjectNode changePassword(@RequestBody ChangePasswordRequest changePasswordRequest) throws ThingsboardException {
        try {
            SecurityUser securityUser = getCurrentUser();
            UserCredentials userCredentials = userService.findUserCredentialsByUserId(securityUser.getId());

            validatePassword(passwordEncoder, changePasswordRequest, userCredentials.getPassword());
            systemSecurityService.validatePassword(changePasswordRequest.getNewPassword());

            userCredentials.setPassword(passwordEncoder.encode(changePasswordRequest.getNewPassword()));
            userService.replaceUserCredentials(userCredentials);

            ObjectNode response = JacksonUtil.newObjectNode();
            response.put("token", tokenFactory.createAccessJwtToken(securityUser).getToken());
            response.put("refreshToken", tokenFactory.createRefreshToken(securityUser).getToken());
            return response;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @RequestMapping(value = "/noauth/resetPasswordByEmail", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public void requestResetPasswordByEmail(
            @RequestBody ResetPasswordEmailRequest resetPasswordByEmailRequest,
            HttpServletRequest request) {
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

    @RequestMapping(value = "/noauth/resetPassword", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public JwtTokenPair resetPassword(
            @RequestBody ResetPasswordRequest resetPasswordRequest,
            HttpServletRequest request) throws ThingsboardException {
        try {
            String resetToken = resetPasswordRequest.getResetToken();
            String password = resetPasswordRequest.getPassword();
            UserCredentials userCredentials = userService.findUserCredentialsByResetToken(resetToken);
            if (userCredentials != null) {
                systemSecurityService.validatePassword(password);
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
                mailService.sendPasswordWasResetEmail(loginUrl, email);
                JwtToken accessToken = tokenFactory.createAccessJwtToken(securityUser);
                JwtToken refreshToken = tokenFactory.createRefreshToken(securityUser);

                return new JwtTokenPair(accessToken.getToken(), refreshToken.getToken());
            } else {
                throw new ThingsboardException("Invalid reset token!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @RequestMapping(value = "/noauth/resetPassword", params = {"resetToken"}, method = RequestMethod.GET)
    public ResponseEntity<String> checkResetToken(
            @RequestParam(value = "resetToken") String resetToken) {
        HttpHeaders headers = new HttpHeaders();
        HttpStatus responseStatus;
        String resetURI = "/login/resetPassword";
        UserCredentials userCredentials = userService.findUserCredentialsByResetToken(resetToken);
        if (userCredentials != null) {
            try {
                URI location = new URI(resetURI + "?resetToken=" + resetToken);
                headers.setLocation(location);
                responseStatus = HttpStatus.SEE_OTHER;
            } catch (URISyntaxException e) {
                log.error("Unable to create URI with address [{}]", resetURI);
                responseStatus = HttpStatus.BAD_REQUEST;
            }
        } else {
            responseStatus = HttpStatus.CONFLICT;
        }
        return new ResponseEntity<>(headers, responseStatus);
    }
}
