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
package org.thingsboard.mqtt.broker.session;

import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionAcceptedMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.EnhancedAuthInitMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.NonWritableChannelMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.SessionInitMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.SubscribeCommandMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.UnsubscribeCommandMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.WritableChannelMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttAuthMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttConnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.QueueableMqttMsg;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;

import java.util.Set;

public interface ClientMqttActorManager {

    void initSession(String clientId, boolean isClientIdGenerated, SessionInitMsg sessionInitMsg);

    void initEnhancedAuth(String clientId, boolean isClientIdGenerated, EnhancedAuthInitMsg enhancedAuthInitMsg);

    void processMqttAuthMsg(String clientId, MqttAuthMsg mqttAuthMsg);

    void disconnect(String clientId, MqttDisconnectMsg disconnectMsg);

    void connect(String clientId, MqttConnectMsg connectMsg);

    void processMqttMsg(String clientId, QueueableMqttMsg mqttMsg);

    void notifyConnectionAccepted(String clientId, ConnectionAcceptedMsg connectionAcceptedMsg);

    void subscribe(String clientId, SubscribeCommandMsg subscribeCommandMsg);

    void unsubscribe(String clientId, UnsubscribeCommandMsg unsubscribeCommandMsg);

    void notifyChannelNonWritable(String clientId, NonWritableChannelMsg nonWritableChannelMsg);

    void notifyChannelWritable(String clientId, WritableChannelMsg writableChannelMsg);

    void processSubscriptionsChanged(String clientId, Set<TopicSubscription> topicSubscriptions);

}
