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
package org.thingsboard.mqtt.broker.service.stats;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;

/**
 * Tracks the terminal outcomes of inbound MQTT connection attempts as three cumulative Prometheus
 * counters: {@code connectionAccepted}, {@code connectionRefused}, {@code connectionError}. In CE all
 * three are untagged, so {@code accepted + refused + error} (derived in PromQL) approximates total
 * attempts and each {@code rate(...)} gives the accept/refuse/error throughput. The per-cause breakdowns
 * ({@code returnCode} / {@code type}) are PE-only extensions that override this impl.
 * <p>
 * {@code connectionError} counts establishment failures that surface before a session exists, so
 * {@code accepted + refused + error} is a <b>lower bound</b> on total attempts, not an exact identity:
 * a channel error occurring after the CONNECT is read but before the session is established (during async
 * authentication/validation) is counted by neither {@code connectionError} (see
 * {@code MqttSessionHandler.exceptionCaught}, gated on {@code clientId == null}) nor {@code clientDisconnects}
 * (which skips sessions that were never established). That gap is deliberate — it keeps the two metric
 * families disjoint at the cost of not counting the narrow mid-handshake window.
 * <p>
 * Obtained from {@link StatsManager}, so it shares the {@code stats.enabled} master switch; when stats
 * are disabled the stub implementation is a no-op and nothing is exposed.
 */
public interface ConnectionStats {

    /** Records one accepted connection (successful CONNACK). */
    void onConnectionAccepted();

    /**
     * Records one refused connection (a {@code CONNECTION_REFUSED_*} CONNACK). {@code returnCode} is
     * carried so the PE extension can tag by reason; the CE impl ignores it (single untagged counter).
     */
    void onConnectionRefused(MqttConnectReturnCode returnCode);

    /**
     * Records one connection-establishment error (no CONNACK — TLS/framing/IO). {@code type} is carried
     * so the PE extension can tag by type; the CE impl ignores it (single untagged counter).
     */
    void onConnectionError(ConnectionErrorType type);

    /** Accepted count since the last {@link #reset()} — read for the periodic stats log line. */
    int getAcceptedCount();

    /** Refused count since the last {@link #reset()} — read for the periodic stats log line. */
    int getRefusedCount();

    /** Error count since the last {@link #reset()} — read for the periodic stats log line. */
    int getErrorCount();

    /** Clears the per-interval counts; the cumulative Prometheus counters stay monotonic. */
    void reset();
}
