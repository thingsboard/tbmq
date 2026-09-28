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

import io.netty.channel.EventLoop;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.util.concurrent.Promise;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Consumer;

@Getter(AccessLevel.PACKAGE)
final class MqttPendingSubscription {

    private final Promise<MqttQoS> future;
    private final String topic;
    /**
     * The handler to register on SUBACK. Callers replace it while the SUBSCRIBE is in flight, so the last {@code on()}
     * for the filter wins; the event loop reads it once, on SUBACK.
     */
    private volatile MqttHandler handler;
    private final MqttSubscribeMessage subscribeMessage;

    @Getter(AccessLevel.NONE)
    private final RetransmissionHandler<MqttSubscribeMessage> retransmissionHandler;

    @Setter(AccessLevel.PACKAGE)
    private boolean sent = false;

    private MqttPendingSubscription(
            Promise<MqttQoS> future,
            String topic,
            MqttHandler handler,
            MqttSubscribeMessage subscribeMessage,
            String ownerId,
            MqttClientConfig.RetransmissionConfig retransmissionConfig,
            PendingOperation operation
    ) {
        this.future = future;
        this.topic = topic;
        this.handler = handler;
        this.subscribeMessage = subscribeMessage;

        retransmissionHandler = new RetransmissionHandler<>(retransmissionConfig, operation, ownerId);
        retransmissionHandler.setOriginalMessage(subscribeMessage);
    }

    /**
     * The handler registered when the SUBACK grants the filter; the last {@code on()} for a filter in flight wins,
     * except one racing the SUBACK itself, whose handler can be lost while its shared future still succeeds.
     */
    void setHandler(MqttHandler handler) {
        this.handler = handler;
    }

    void startRetransmitTimer(EventLoop eventLoop, Consumer<Object> sendPacket) {
        if (sent) { // If the packet is sent, we can start the retransmission timer
            retransmissionHandler.setHandler((fixedHeader, originalMessage) ->
                    sendPacket.accept(new MqttSubscribeMessage(fixedHeader, originalMessage.variableHeader(), originalMessage.payload())));
            retransmissionHandler.start(eventLoop);
        }
    }

    void onSubackReceived() {
        retransmissionHandler.stop();
    }

    void onChannelClosed() {
        retransmissionHandler.stop();
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private Promise<MqttQoS> future;
        private String topic;
        private MqttHandler handler;
        private MqttSubscribeMessage subscribeMessage;
        private String ownerId;
        private PendingOperation pendingOperation;
        private MqttClientConfig.RetransmissionConfig retransmissionConfig;

        Builder future(Promise<MqttQoS> future) {
            this.future = future;
            return this;
        }

        Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        Builder handler(MqttHandler handler) {
            this.handler = handler;
            return this;
        }

        Builder subscribeMessage(MqttSubscribeMessage subscribeMessage) {
            this.subscribeMessage = subscribeMessage;
            return this;
        }

        Builder ownerId(String ownerId) {
            this.ownerId = ownerId;
            return this;
        }

        Builder retransmissionConfig(MqttClientConfig.RetransmissionConfig retransmissionConfig) {
            this.retransmissionConfig = retransmissionConfig;
            return this;
        }

        Builder pendingOperation(PendingOperation pendingOperation) {
            this.pendingOperation = pendingOperation;
            return this;
        }

        MqttPendingSubscription build() {
            return new MqttPendingSubscription(future, topic, handler, subscribeMessage, ownerId, retransmissionConfig, pendingOperation);
        }

    }

}
