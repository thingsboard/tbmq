/**
 * Copyright © 2016-2020 The Thingsboard Authors
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

import lombok.Getter;
import lombok.Setter;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.UUID;

public class DefaultClientActorState implements ClientActorState {
    // TODO: move subscription here
    //    private final Set<TopicSubscription> subscriptions = new HashSet<>();

    private final String clientId;
    private final boolean isClientIdGenerated;

    private final QueuedMqttMessages queuedMqttMessages;

    @Setter
    @Getter
    private UUID stopActorCommandId;

    private ClientSessionCtx clientSessionCtx;
    private SessionState currentSessionState = SessionState.DISCONNECTED;

    public DefaultClientActorState(String clientId, boolean isClientIdGenerated, int maxPreConnectQueueSize) {
        this.clientId = clientId;
        this.isClientIdGenerated = isClientIdGenerated;
        this.queuedMqttMessages = new QueuedMqttMessages(maxPreConnectQueueSize);
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    @Override
    public UUID getCurrentSessionId() {
        return clientSessionCtx != null ? clientSessionCtx.getSessionId() : null;
    }

    @Override
    public SessionState getCurrentSessionState() {
        return currentSessionState;
    }

    @Override
    public ClientSessionCtx getCurrentSessionCtx() {
        return clientSessionCtx;
    }

    @Override
    public boolean isClientIdGenerated() {
        return isClientIdGenerated;
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
    public void setClientSessionCtx(ClientSessionCtx clientSessionCtx) {
        this.clientSessionCtx = clientSessionCtx;
    }

    @Override
    public void clearStopActorCommandId() {
        this.stopActorCommandId = null;
    }
}
