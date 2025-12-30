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
package org.thingsboard.mqtt.broker.service.mqtt.persistence;

import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgWithId;
import org.thingsboard.mqtt.broker.service.processing.data.PersistentMsgSubscriptions;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.Set;

public interface MsgPersistenceManager {

    void processPublish(PublishMsgWithId publishMsgWithId, PersistentMsgSubscriptions persistentSubscriptions, PublishMsgCallback callback);

    void processPubAck(ClientSessionCtx clientSessionCtx, int packetId);

    void processPubRec(ClientSessionCtx clientSessionCtx, int packetId);

    void processPubRecNoPubRelDelivery(ClientSessionCtx clientSessionCtx, int packetId);

    void processPubComp(ClientSessionCtx clientSessionCtx, int packetId);

    void startProcessingPersistedMessages(ClientActorStateInfo actorState);

    void startProcessingSharedSubscriptions(ClientSessionCtx clientSessionCtx, Set<TopicSharedSubscription> subscriptions);

    void saveAwaitingQoS2Packets(ClientSessionCtx clientSessionCtx);

    void stopProcessingPersistedMessages(ClientInfo clientInfo);

    void clearPersistedMessages(String clientId, ClientType type);
}
