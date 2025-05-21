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
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(of = {"clientId"}, callSuper = false)
@Data
@NoArgsConstructor
public class ClientIdBlockedClient extends AbstractBlockedClient {

    private String clientId;

    public ClientIdBlockedClient(String clientId) {
        super();
        this.clientId = clientId;
    }

    public ClientIdBlockedClient(long expirationTime, String description, String clientId) {
        super(expirationTime, description);
        this.clientId = clientId;
    }

    @Override
    public BlockedClientType getType() {
        return BlockedClientType.CLIENT_ID;
    }

    @Override
    public String getValue() {
        return clientId;
    }

}
