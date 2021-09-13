/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.security.Authority;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.dto.AdminDto;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {
    private final UserService userService;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public User createAdmin(AdminDto adminDto) {
        User user = toUser(adminDto);
        user = userService.saveUser(user);
        UserCredentials userCredentials = userService.findUserCredentialsByUserId(user.getId());
        if (userCredentials == null) {
            throw new IllegalArgumentException("User credentials were not created for user.");
        }
        userCredentials.setPassword(passwordEncoder.encode(adminDto.getPassword()));
        userCredentials.setEnabled(true);
        userCredentials.setActivateToken(null);
        userService.saveUserCredentials(userCredentials);
        return user;
    }

    private User toUser(AdminDto adminDto) {
        User user = new User();
        user.setId(adminDto.getId());
        user.setAuthority(Authority.SYS_ADMIN);
        user.setEmail(adminDto.getEmail());
        user.setFirstName(adminDto.getFirstName());
        user.setLastName(adminDto.getLastName());
        return user;
    }
}
