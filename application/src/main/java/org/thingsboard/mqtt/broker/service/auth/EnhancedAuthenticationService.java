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
package org.thingsboard.mqtt.broker.service.auth;

import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthContext;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthContinueResponse;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthFinalResponse;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

public interface EnhancedAuthenticationService {

    EnhancedAuthContinueResponse onClientConnectMsg(ClientSessionCtx sessionCtx, EnhancedAuthContext authContext);

    EnhancedAuthContinueResponse onReAuth(ClientSessionCtx sessionCtx, EnhancedAuthContext authContext);

    EnhancedAuthFinalResponse onAuthContinue(ClientSessionCtx sessionCtx, EnhancedAuthContext authContext);

    EnhancedAuthFinalResponse onReAuthContinue(ClientSessionCtx sessionCtx, EnhancedAuthContext authContext);

}
