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
package org.thingsboard.mqtt.broker.session;

import io.netty.handler.codec.mqtt.MqttMessage;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;

import java.util.UUID;

public interface ClientMqttActorManager {
    void initSession(String clientId, String username, byte[] passwordBytes, ClientSessionCtx clientSessionCtx, boolean isClientIdGenerated);

    void disconnect(String clientId, UUID sessionId, DisconnectReason reason);

    void processMqttMsg(String clientId, UUID sessionId, MqttMessage msg);

    void processConnectionAccepted(String clientId, UUID sessionId, boolean isPrevSessionPersistent, PublishMsg lastWillMsg);

    void processConnectionFinished(String clientId, UUID sessionId);
}
