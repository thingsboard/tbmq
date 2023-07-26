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
package org.thingsboard.mqtt.broker.common.data;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.thingsboard.mqtt.broker.common.data.security.Authority;
import org.thingsboard.mqtt.broker.common.data.validation.NoXss;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
public class User extends SearchTextBasedWithAdditionalInfo {

    private static final long serialVersionUID = 8250339805336035966L;

    @Getter
    @Setter
    private String email;
    @Getter
    @Setter
    private Authority authority;
    @Getter
    @Setter
    @NoXss
    private String firstName;
    @Getter
    @Setter
    @NoXss
    private String lastName;

    public User() {
    }

    public User(UUID id) {
        super(id);
    }

    @Override
    public String getSearchText() {
        return getEmail();
    }

    public User(User user) {
        super(user);
        this.email = user.email;
        this.authority = user.authority;
        this.firstName = user.firstName;
        this.lastName = user.lastName;
    }


    @Override
    public String toString() {
        return "User [email=" +
                email +
                ", authority=" +
                authority +
                ", firstName=" +
                firstName +
                ", lastName=" +
                lastName +
                ", additionalInfo=" +
                getAdditionalInfo() +
                ", createdTime=" +
                createdTime +
                ", id=" +
                id +
                "]";
    }
}
