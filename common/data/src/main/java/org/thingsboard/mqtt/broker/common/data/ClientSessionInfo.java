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

    public static final long NO_SESSION_END = -1;
    public static final int NO_TTL = 0;

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
     * Timestamp (epoch millis) at which this session's state ends, or {@link #NO_SESSION_END} if it does not
     * end on its own: the session is connected, or it has MQTTv3 cleanSession=false semantics
     * (cleanStart=false, sessionExpiryInterval=0) and the administrative TTL is disabled ({@code ttlSeconds <= 0}).
     * Single source of the "disconnectedAt + interval" formula used by the cleanup job, the connect path and
     * the session details DTO.
     */
    public long getSessionEndTs(int ttlSeconds) {
        if (connected) {
            return NO_SESSION_END;
        }
        if (isNotCleanSession()) {
            return ttlSeconds > 0 ? disconnectedAt + TimeUnit.SECONDS.toMillis(ttlSeconds) : NO_SESSION_END;
        }
        return disconnectedAt + TimeUnit.SECONDS.toMillis(safeGetSessionExpiryInterval());
    }

    /**
     * True when this session is disconnected and its state has ended by {@code now} (see {@link #getSessionEndTs(int)}).
     * Pass {@link #NO_TTL} to consider only the MQTT Session Expiry Interval, which is what the connect path must do:
     * the administrative TTL is a housekeeping rule, not part of the protocol contract.
     * <p>
     * Notes:
     * <ul>
     *   <li>{@code disconnectedAt} is stamped by the node that processed the disconnect and compared with the caller's
     *   clock. Cross-node clock skew comparable to a short Session Expiry Interval (a few seconds) can make a session
     *   look expired earlier or later than the client expects; keep node clocks synchronized.</li>
     *   <li>A disconnected session with {@code disconnectedAt == 0} is unconditionally expired. Every real disconnect
     *   path sets the timestamp; test fixtures built from bare builder defaults must set it (or {@code connected(true)})
     *   to represent a live session.</li>
     * </ul>
     */
    public boolean isExpired(long now, int ttlSeconds) {
        long endTs = getSessionEndTs(ttlSeconds);
        return endTs != NO_SESSION_END && endTs < now;
    }

    public boolean isAppClient() {
        return ClientType.APPLICATION == type;
    }

    public boolean isPersistentAppClient() {
        return isAppClient() && isPersistent();
    }

    /**
     * For tests purposes. The result is disconnected with {@code disconnectedAt == 0}, so it reads as an
     * expired session (see {@link #isExpired(long, int)}); set {@code connected(true)} or {@code disconnectedAt(...)}
     * when the test needs a live or recently disconnected session.
     */
    public static ClientSessionInfo withClientType(ClientType clientType) {
        return ClientSessionInfo.builder().type(clientType).build();
    }
}
