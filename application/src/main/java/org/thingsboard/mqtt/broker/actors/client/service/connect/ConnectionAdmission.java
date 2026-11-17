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
package org.thingsboard.mqtt.broker.actors.client.service.connect;

import java.util.UUID;

/**
 * Decides whether a CONNECT may go on, and tracks the CONNECTs it let through until each ends.
 */
public interface ConnectionAdmission {

    /**
     * Called on the client actor thread when a CONNECT starts, before the session info is set, so a refused CONNECT
     * leaves no session to clean up.
     *
     * @return whether the CONNECT may go on; one that may is tracked until {@link #release}
     */
    boolean tryAdmit(UUID sessionId);

    /**
     * Called on the client actor thread when an admitted CONNECT ends: its session is registered, or it is being
     * disconnected. Does nothing for a session that was never admitted or was already released.
     */
    void release(UUID sessionId);

}
