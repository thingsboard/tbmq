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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Builder(toBuilder = true)
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class ClientSessionInfo {

    private final boolean connected;
    private final String serviceId;
    private final UUID sessionId;
    private final boolean cleanStart;
    private final Integer sessionExpiryInterval;
    private final String clientId;
    private final ClientType type;
    private final String clientIpAdr;
    private final long connectedAt;
    private final long disconnectedAt;
    private final int keepAlive;

}
