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
package org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data;

import lombok.Data;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.util.BlockedClientKeyUtil;

@Data
public abstract class AbstractBlockedClient implements BlockedClient {

    private long expirationTime;
    private String description;

    public AbstractBlockedClient() {
    }

    public AbstractBlockedClient(long expirationTime, String description) {
        this.expirationTime = expirationTime;
        this.description = description;
    }

    @Override
    public String getKey() {
        // Maybe improve to have this pre-computed
        return BlockedClientKeyUtil.generateKey(getType(), getValue(), getRegexMatchTarget());
    }

    @Override
    public boolean isExpired() {
        if (expirationTime <= 0) {
            return false;
        }
        return System.currentTimeMillis() > expirationTime;
    }
}
