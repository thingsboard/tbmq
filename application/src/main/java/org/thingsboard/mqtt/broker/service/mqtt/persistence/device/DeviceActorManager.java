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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.device;

import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.Set;

public interface DeviceActorManager {

    void notifyClientConnected(ClientSessionCtx clientSessionCtx);

    void notifySubscribeToSharedSubscriptions(ClientSessionCtx clientSessionCtx, Set<TopicSharedSubscription> subscriptions);

    void notifyClientDisconnected(String clientId);

    void sendMsgToActor(String clientId, DevicePublishMsg devicePublishMsg);

    void notifyPacketAcknowledged(String clientId, int packetId);

    void notifyPacketReceived(String clientId, int packetId);

    void notifyPacketReceivedNoDelivery(String clientId, int packetId);

    void notifyPacketCompleted(String clientId, int packetId);

}
