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
package org.thingsboard.mqtt.broker.actors.client.service.session;


import org.thingsboard.mqtt.broker.actors.client.messages.ClientCallback;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.ConnectionRequestMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.SessionDisconnectedMsg;

import java.util.UUID;

public interface SessionClusterManager {

    void processConnectionRequest(ConnectionRequestMsg msg);

    void processSessionDisconnected(String clientId, SessionDisconnectedMsg msg);

    void processClearSession(String clientId, UUID sessionId);

    void processRemoveApplicationTopicRequest(String clientId, ClientCallback callback);
}
