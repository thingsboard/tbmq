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
package org.thingsboard.mqtt.broker.service.mqtt.will;

import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.processing.PublisherIdentity;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface LastWillService {

    void saveLastWillMsg(SessionInfo sessionInfo, PublishMsg publishMsg, PublisherIdentity publisher);

    void removeAndExecuteLastWillIfNeeded(MqttDisconnectMsg disconnectMsg, int sessionExpiryInterval);

    void cancelLastWillDelayIfScheduled(String clientId);

    /**
     * @return whether a Last Will saved at the session's CONNECT is still waiting for the session's disconnect. The
     * disconnect takes the will from the store only after it has handed a due will to the scheduler, so once this is
     * {@code false} after a disconnect, {@link #dueLastWillsHandled()} covers that will
     */
    boolean hasLastWill(UUID sessionId);

    /**
     * @return a future that completes once every Last Will that is due by now has been processed: handed to the
     * dispatcher, or failed. A will whose Will Delay Interval has not passed yet is not waited for. The future is
     * complete at once when the scheduler of the wills is shut down.
     */
    CompletableFuture<Void> dueLastWillsHandled();
}
