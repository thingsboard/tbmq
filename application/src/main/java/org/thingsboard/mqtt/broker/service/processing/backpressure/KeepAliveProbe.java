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

/**
 * The answer of {@link PublishBackpressure#probe} for a session that sent no packet within its keep-alive time.
 */
public enum KeepAliveProbe {

    /**
     * The connection is not paused, or it was probed already and no data arrived since: the keep-alive closes it as
     * it closes any other.
     */
    CLOSE,

    /**
     * The paused connection was just probed with one read: the keep-alive skips this check, and the next one shows
     * whether the client sent anything.
     */
    WAIT,

    /**
     * Data arrived on the paused connection since it was paused or probed, so the client is alive: the keep-alive
     * refreshes the session and skips it.
     */
    ALIVE

}
