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
package org.thingsboard.mqtt.broker.common.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Builder(toBuilder = true)
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class ClientSessionInfo implements EntitySessionInfo {

    private final boolean connected;
    private final String serviceId;
    private final UUID sessionId;
    private final boolean cleanStart;
    private final int sessionExpiryInterval;
    private final String clientId;
    private final ClientType type;
    private final byte[] clientIpAdr;
    private final long connectedAt;
    private final long disconnectedAt;
    private final int keepAlive;

    @Override
    public boolean isPersistent() {
        return safeGetSessionExpiryInterval() > 0 || isNotCleanSession();
    }

    public boolean isCleanSession() { // The equivalent for cleanSession=true in the CONNECT packet of MQTTv3
        return cleanStart && safeGetSessionExpiryInterval() == 0;
    }

    public boolean isNotCleanSession() { // The equivalent for cleanSession=false in the CONNECT packet of MQTTv3
        return !cleanStart && safeGetSessionExpiryInterval() == 0;
    }

    public int safeGetSessionExpiryInterval() {
        return sessionExpiryInterval == -1 ? 0 : sessionExpiryInterval;
    }

    public boolean isDisconnected() {
        return !connected;
    }

    /**
     * True when this session is disconnected and its MQTT Session Expiry Interval has elapsed.
     * Sessions with a zero expiry interval and cleanStart=false (MQTTv3 cleanSession=false semantics)
     * never expire by this rule; they are governed by the administrative TTL only.
     */
    public boolean isExpired(long now) {
        if (connected || isNotCleanSession()) {
            return false;
        }
        return disconnectedAt + TimeUnit.SECONDS.toMillis(safeGetSessionExpiryInterval()) < now;
    }

    public boolean isAppClient() {
        return ClientType.APPLICATION == type;
    }

    public boolean isPersistentAppClient() {
        return isAppClient() && isPersistent();
    }

    /**
     * For tests purposes
     */
    public static ClientSessionInfo withClientType(ClientType clientType) {
        return ClientSessionInfo.builder().type(clientType).build();
    }
}
