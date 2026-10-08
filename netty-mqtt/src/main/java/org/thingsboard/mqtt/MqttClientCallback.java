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
package org.thingsboard.mqtt;

import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttUnsubAckMessage;

/**
 * The connection's lifecycle. A connection is up from an {@link #onConnAck} that accepted it until its
 * {@link #connectionLost}: each connection that was up gets exactly one of each. A connect attempt that never got an
 * accepted CONNACK gets no {@link #connectionLost} - its connect future carries the outcome - though a refused CONNACK
 * is still reported through {@link #onConnAck}. A CONNACK that arrives after {@link MqttClient#disconnect()} is not
 * reported at all, so no connection is up after a disconnect.
 */
public interface MqttClientCallback {

    /**
     * Called once when a connection whose CONNACK accepted it closes, whatever closed it.
     *
     * @param cause the reason behind the loss of connection.
     */
    void connectionLost(Throwable cause);

    /**
     * Called on the accepted CONNACK of an automatic reconnect, just before {@link #onConnAck}.
     */
    void onSuccessfulReconnect();

    /**
     * Called for every CONNACK, accepted or refused, that arrives before {@link MqttClient#disconnect()}; an accepted one
     * means the connection is up.
     */
    default void onConnAck(MqttConnAckMessage connAckMessage) {
    }

    default void onPubAck(MqttPubAckMessage pubAckMessage) {
    }

    default void onSubAck(MqttSubAckMessage pubAckMessage) {
    }

    default void onUnsubAck(MqttUnsubAckMessage unsubAckMessage) {
    }

    /**
     * Called for a DISCONNECT the server sends. Informational: {@link #connectionLost} follows when the channel closes.
     */
    default void onDisconnect(MqttMessage mqttDisconnectMessage) {
    }

    /**
     * Called when the library could not resubscribe a registered filter after a reconnect that lost the session: the
     * server refused it, or its retransmissions or the message ids ran out. The filter stays registered, and the next
     * reconnect tries again. A resubscribe cut short by a closed channel is not reported: the next
     * accepted CONNACK sends it again, whether or not the server kept the session.
     */
    default void onResubscribeFailed(String topicFilter, Throwable cause) {
    }

    /**
     * Called for a PUBLISH over {@link MqttClientConfig#getMaxBytesInMessage()}, which the client skipped without
     * buffering it and acked as failed: 0x80 under MQTT 5, a plain ack under 3.x, where that ack loses the message. The
     * connection stays up.
     */
    default void onPublishTooLarge(String topic, MqttQoS qos, int remainingLength) {
    }

}
