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
package org.thingsboard.mqtt.broker.service.security.auth.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.service.security.auth.RefreshAuthenticationToken;
import org.thingsboard.mqtt.broker.service.security.model.SecurityUser;
import org.thingsboard.mqtt.broker.service.security.model.UserPrincipal;
import org.thingsboard.mqtt.broker.service.security.model.token.JwtTokenFactory;
import org.thingsboard.mqtt.broker.service.security.model.token.RawAccessJwtToken;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RefreshTokenAuthenticationProvider implements AuthenticationProvider {

    private final JwtTokenFactory tokenFactory;
    private final UserService userService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Assert.notNull(authentication, "No authentication data provided");
        RawAccessJwtToken rawAccessToken = (RawAccessJwtToken) authentication.getCredentials();
        SecurityUser unsafeUser = tokenFactory.parseRefreshToken(rawAccessToken);
        SecurityUser securityUser = authenticateByUserId(unsafeUser.getId());
        return new RefreshAuthenticationToken(securityUser);
    }

    private SecurityUser authenticateByUserId(UUID userId) {
        User user = userService.findUserById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("User not found by refresh token");
        }

        UserCredentials userCredentials = userService.findUserCredentialsByUserId(user.getId());
        if (userCredentials == null) {
            throw new UsernameNotFoundException("User credentials not found");
        }

        if (!userCredentials.isEnabled()) {
            throw new DisabledException("User is not active");
        }

        if (user.getAuthority() == null) throw new InsufficientAuthenticationException("User has no authority assigned");

        return new SecurityUser(user, userCredentials.isEnabled(), new UserPrincipal(user.getEmail()));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return (RefreshAuthenticationToken.class.isAssignableFrom(authentication));
    }
}
