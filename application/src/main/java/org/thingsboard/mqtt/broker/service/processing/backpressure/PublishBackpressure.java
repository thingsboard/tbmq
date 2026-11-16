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
package org.thingsboard.mqtt.broker.service.processing.backpressure;

import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.UUID;

/**
 * Slows publishers down to the rate the broker processes their publishes, by pausing reads on their connections
 * while the broker holds too many publishes and resuming them once it has caught up.
 */
public interface PublishBackpressure {

    /**
     * Called on the client actor thread before the pipeline decides on a PUBLISH of the session. Pauses reading from
     * the session's connection while the broker holds too many publishes; reading resumes once it has caught up.
     */
    void onPublish(ClientSessionCtx ctx);

    /**
     * Called on the client actor thread when the session is cleaned up after its disconnect. Forgets the session, so
     * that a closed session is not kept until the next resume.
     */
    void onSessionClosed(UUID sessionId);

    /**
     * Called on the event loop for every inbound packet of the session, once the session knows its channel. A packet
     * that arrives on a paused connection shows that the client is alive, which {@link #probe} reports.
     */
    void onChannelRead(ClientSessionCtx ctx);

    /**
     * Called on the keep-alive thread for a session that sent no packet within its keep-alive time, before the
     * keep-alive closes its connection. A paused connection does not read the packets the client sent, so the
     * keep-alive asks first whether the session is paused, and whether the client is alive.
     *
     * @param lastPacketTime the session's last packet time as the keep-alive knows it, in milliseconds of
     *                       {@link System#currentTimeMillis()}: data read or a probe made before it belongs to an
     *                       earlier deadline
     * @return what the keep-alive does with the session
     */
    KeepAliveProbe probe(UUID sessionId, long lastPacketTime);

    /**
     * Called on the keep-alive thread on every check of a session that has a keep-alive and has not reached its
     * deadline. A paused connection does not read what the client sent, a PINGREQ included, and a client that waits too
     * long for its PINGRESP closes the connection itself: so a paused connection is read once whenever
     * {@code readIntervalMs} has passed since it was paused or last read for the keep-alive, here or by {@link #probe}.
     * What such a read finds counts as any packet read on a paused connection; a read that finds nothing changes
     * nothing in what {@link #probe} answers, so a dead client is closed at its deadline as before.
     */
    void readIfDue(UUID sessionId, long readIntervalMs);

}
