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
package org.thingsboard.mqtt.broker.service.mqtt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;

@AllArgsConstructor
@Getter
@Builder(toBuilder = true)
@EqualsAndHashCode
@ToString
public class ClientSession {
    private final boolean connected;
    private final SessionInfo sessionInfo;

    public String getClientId() {
        return (sessionInfo != null && sessionInfo.getClientInfo() != null) ?
                sessionInfo.getClientInfo().getClientId() : null;
    }

    public ClientType getClientType() {
        return sessionInfo.getClientInfo().getType();
    }
}
