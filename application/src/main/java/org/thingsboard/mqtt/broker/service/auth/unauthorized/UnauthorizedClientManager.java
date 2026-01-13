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
package org.thingsboard.mqtt.broker.service.auth.unauthorized;

import org.thingsboard.mqtt.broker.actors.client.messages.SessionInitMsg;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorState;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthContinueResponse;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthFinalResponse;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

public interface UnauthorizedClientManager {

    void persistClientUnauthorized(ClientActorState state, SessionInitMsg sessionInitMsg, String reason);

    void persistClientUnauthorized(ClientActorState state, ClientSessionCtx clientSessionCtx, EnhancedAuthContinueResponse authResponse);

    void persistClientUnauthorized(ClientActorState state, ClientSessionCtx clientSessionCtx, EnhancedAuthFinalResponse authResponse);

    void persistClientUnauthorized(ClientActorState state, ClientSessionCtx clientSessionCtx,
                                   String username, boolean passwordProvided, String reason);

    void persistClientUnauthorized(String clientId, String reason);

    void removeClientUnauthorized(ClientActorState state);

}
