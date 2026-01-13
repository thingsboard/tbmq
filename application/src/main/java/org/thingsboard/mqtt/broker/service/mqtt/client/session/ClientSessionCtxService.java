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
package org.thingsboard.mqtt.broker.service.mqtt.client.session;


import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.Collection;

public interface ClientSessionCtxService {

    void registerSession(ClientSessionCtx clientSessionCtx);

    void unregisterSession(String clientId);

    ClientSessionCtx getClientSessionCtx(String clientId);

    boolean hasSession(String clientId);

    Collection<ClientSessionCtx> getAllClientSessionCtx();

}
