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
package org.thingsboard.mqtt.broker.standalone.local;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandService;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.UUID;

/**
 * The only session owner is this node, so the command goes straight to the client actor
 * instead of through a queue; the target service id is not needed.
 */
@Component
@StandaloneComponent
@RequiredArgsConstructor
public class LocalDisconnectClientCommandService implements DisconnectClientCommandService {

    private final ClientMqttActorManager clientMqttActorManager;

    @Override
    public void disconnectOnSessionConflict(String serviceId, String clientId, UUID sessionId, boolean newSessionCleanStart) {
        disconnect(clientId, sessionId, newSessionCleanStart, DisconnectReasonType.ON_CLUSTER_CONFLICTING_SESSIONS);
    }

    @Override
    public void disconnectSessionOnAdminAction(String serviceId, String clientId, UUID sessionId, boolean newSessionCleanStart) {
        disconnect(clientId, sessionId, newSessionCleanStart, DisconnectReasonType.ON_ADMINISTRATIVE_ACTION);
    }

    private void disconnect(String clientId, UUID sessionId, boolean newSessionCleanStart, DisconnectReasonType type) {
        clientMqttActorManager.disconnect(clientId, new MqttDisconnectMsg(sessionId, new DisconnectReason(type), newSessionCleanStart));
    }

}
