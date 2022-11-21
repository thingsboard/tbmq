/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.service.security.model.ChangePasswordRequest;
import org.thingsboard.mqtt.broker.service.security.model.SecurityUser;
import org.thingsboard.mqtt.broker.service.security.model.token.JwtTokenFactory;
import org.thingsboard.mqtt.broker.service.security.system.SystemSecurityService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class AuthController extends BaseController {
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenFactory tokenFactory;
    private final SystemSecurityService systemSecurityService;

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
}
