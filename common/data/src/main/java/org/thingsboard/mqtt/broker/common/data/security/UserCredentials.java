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
package org.thingsboard.mqtt.broker.common.data.security;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.thingsboard.mqtt.broker.common.data.BaseData;

import java.io.Serial;
import java.util.UUID;

@Setter
@Getter
@EqualsAndHashCode(callSuper = true)
public class UserCredentials extends BaseData {

    @Serial
    private static final long serialVersionUID = -3440717077888573927L;

    private UUID userId;
    private boolean enabled;
    private String password;
    private String activateToken;
    private String resetToken;

    public UserCredentials() {
        super();
    }

    public UserCredentials(UUID id) {
        super(id);
    }

    public UserCredentials(UserCredentials userCredentials) {
        super(userCredentials);
        this.userId = userCredentials.getUserId();
        this.password = userCredentials.getPassword();
        this.enabled = userCredentials.isEnabled();
        this.activateToken = userCredentials.getActivateToken();
        this.resetToken = userCredentials.getResetToken();
    }

    @Override
    public String toString() {
        return "UserCredentials [userId=" +
                userId +
                ", enabled=" +
                enabled +
                ", password=" +
                password +
                ", activateToken=" +
                activateToken +
                ", resetToken=" +
                resetToken +
                ", createdTime=" +
                createdTime +
                ", id=" +
                id +
                "]";
    }

    public UserCredentials activateUserCredentials() {
        if (!enabled) {
            enabled = true;
            activateToken = null;
        }
        return this;
    }

}
