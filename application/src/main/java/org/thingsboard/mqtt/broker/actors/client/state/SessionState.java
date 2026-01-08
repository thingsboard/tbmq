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
package org.thingsboard.mqtt.broker.actors.client.state;

import java.util.EnumSet;
import java.util.Set;

public enum SessionState {

    INITIALIZED,
    INITIALIZED_ON_CONFLICT,
    ENHANCED_AUTH_STARTED,
    ENHANCED_AUTH_STARTED_ON_CONFLICT, //todo: use correctly with @dshvaika
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    DISCONNECTED,
    CHANNEL_NON_WRITABLE;

    public static final Set<SessionState> MQTT_PROCESSABLE_STATES = EnumSet.of(
            SessionState.CONNECTING,
            SessionState.CONNECTED,
            SessionState.CHANNEL_NON_WRITABLE
    );

    public static final EnumSet<SessionState> CONNECT_PROCESSABLE_STATES = EnumSet.of(INITIALIZED, INITIALIZED_ON_CONFLICT);

    public boolean isConnectProcessable() {
        return CONNECT_PROCESSABLE_STATES.contains(this);
    }

    public boolean isConnectNotProcessable() {
        return !isConnectProcessable();
    }

    public boolean isConnectOnConflict() {
        return this == INITIALIZED_ON_CONFLICT;
    }
}
