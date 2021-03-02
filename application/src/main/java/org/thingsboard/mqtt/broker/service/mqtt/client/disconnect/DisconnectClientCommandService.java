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
package org.thingsboard.mqtt.broker.service.mqtt.client.disconnect;

import com.google.common.util.concurrent.SettableFuture;

import java.util.UUID;

public interface DisconnectClientCommandService {
    SettableFuture<Void> startWaitingForDisconnect(UUID disconnectRequesterSessionId, UUID sessionId, String clientId);

    void clearWaitingFuture(UUID disconnectRequesterSessionId, UUID sessionId, String clientId);

    void notifyWaitingSession(String clientId, UUID sessionId);

    void disconnectSession(String clientId, UUID sessionId);
}
