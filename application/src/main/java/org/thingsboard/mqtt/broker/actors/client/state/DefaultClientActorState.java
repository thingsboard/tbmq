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

import lombok.Data;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.UUID;

@Data
public class DefaultClientActorState implements ClientActorState {

    private final String clientId;
    private final boolean isClientIdGenerated;
    private final QueuedMqttMessages queuedMqttMessages;

    private volatile UUID stopActorCommandId;
    private volatile ClientSessionCtx clientSessionCtx;
    private volatile SessionState currentSessionState = SessionState.DISCONNECTED;

    public DefaultClientActorState(String clientId, boolean isClientIdGenerated, int maxPreConnectQueueSize) {
        this.clientId = clientId;
        this.isClientIdGenerated = isClientIdGenerated;
        this.queuedMqttMessages = new QueuedMqttMessages(maxPreConnectQueueSize);
    }

    @Override
    public UUID getCurrentSessionId() {
        return clientSessionCtx != null ? clientSessionCtx.getSessionId() : null;
    }

    @Override
    public ClientSessionCtx getCurrentSessionCtx() {
        return clientSessionCtx;
    }

    @Override
    public QueuedMqttMessages getQueuedMessages() {
        return queuedMqttMessages;
    }

    @Override
    public void updateSessionState(SessionState newState) {
        this.currentSessionState = newState;
    }

    @Override
    public void clearStopActorCommandId() {
        this.stopActorCommandId = null;
    }
}
