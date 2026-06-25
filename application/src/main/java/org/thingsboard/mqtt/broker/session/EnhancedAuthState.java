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
package org.thingsboard.mqtt.broker.session;

import io.netty.handler.codec.mqtt.MqttConnectMessage;
import lombok.Getter;
import lombok.Setter;
import org.thingsboard.mqtt.broker.service.auth.enhanced.ScramServerWithCallbackHandler;

/**
 * Working set for the MQTT 5 enhanced-authentication (SCRAM) handshake of a single session.
 *
 * <p>Lifecycle: {@code authMethod} is set when the initial CONNECT requests enhanced auth and is retained
 * for the lifetime of the session (re-AUTH must use the same method). {@code scramServer} and
 * {@code connectMsg} are the transient handshake state and are dropped via {@link #clearScramServer()} /
 * {@link #clearConnectMsg()} once the handshake completes. {@link #isDefaultAuth()} reports whether the
 * session is using plain (non-enhanced) auth, i.e. no enhanced-auth CONNECT was buffered.
 *
 * <p>Fields are {@code volatile}: they are written on the Netty I/O thread (initial CONNECT) and the client
 * actor thread (AUTH continuation), and read from both.
 */
@Getter
@Setter
public class EnhancedAuthState {

    private volatile String authMethod;
    private volatile ScramServerWithCallbackHandler scramServer;
    private volatile MqttConnectMessage connectMsg;

    public boolean isDefaultAuth() {
        return connectMsg == null;
    }

    public void clearScramServer() {
        scramServer = null;
    }

    public void clearConnectMsg() {
        connectMsg = null;
    }
}
