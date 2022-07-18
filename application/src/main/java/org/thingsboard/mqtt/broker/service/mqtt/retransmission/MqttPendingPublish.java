/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.retransmission;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;

@Getter
@Slf4j
public class MqttPendingPublish {

    private final ClientSessionCtx sessionCtx;
    private final int packetId;
    private final ByteBuf payload;
    private final MqttQoS qos;
    private final int retransmissionInitDelay;
    private final int retransmissionPeriod;

    private final RetransmissionHandler<MqttPublishMessage> publishRetransmissionHandler;
    private final RetransmissionHandler<MqttMessage> pubRelRetransmissionHandler;

    @Setter
    private boolean sent = false;

    public MqttPendingPublish(ClientSessionCtx sessionCtx, int packetId, ByteBuf payload, MqttQoS qos,
                              MqttPublishMessage message, int retransmissionInitDelay, int retransmissionPeriod) {
        this.sessionCtx = sessionCtx;
        this.packetId = packetId;
        this.payload = payload;
        this.qos = qos;
        this.retransmissionInitDelay = retransmissionInitDelay;
        this.retransmissionPeriod = retransmissionPeriod;

        PendingOperation operation = () -> !sessionCtx.getPendingPublishes().containsKey(packetId);

        this.publishRetransmissionHandler = new RetransmissionHandler<>(operation, retransmissionInitDelay, retransmissionPeriod);
        this.publishRetransmissionHandler.setOriginalMessage(message);
        this.pubRelRetransmissionHandler = new RetransmissionHandler<>(operation, retransmissionInitDelay, retransmissionPeriod);
    }

    public void startPublishRetransmissionTimer(ScheduledExecutorService scheduler, BiConsumer<ClientSessionCtx, MqttMessage> sendPacket) {
        this.publishRetransmissionHandler.setHandler(((fixedHeader, originalMessage) ->
                sendPacket.accept(sessionCtx, new MqttPublishMessage(fixedHeader, originalMessage.variableHeader(), this.payload.retain()))));
        this.publishRetransmissionHandler.start(scheduler);
    }

    void onPubAckReceived() {
        this.publishRetransmissionHandler.stop();
    }

    public void setPubRelMessage(MqttMessage pubRelMessage) {
        this.pubRelRetransmissionHandler.setOriginalMessage(pubRelMessage);
    }

    public void startPubRelRetransmissionTimer(ScheduledExecutorService scheduler, BiConsumer<ClientSessionCtx, MqttMessage> sendPacket) {
        this.pubRelRetransmissionHandler.setHandler((fixedHeader, originalMessage) ->
                sendPacket.accept(sessionCtx, new MqttMessage(fixedHeader, originalMessage.variableHeader())));
        this.pubRelRetransmissionHandler.start(scheduler);
    }

    public void onPubCompReceived() {
        this.pubRelRetransmissionHandler.stop();
    }

    public void onChannelClosed() {
        if (this.publishRetransmissionHandler != null) {
            this.publishRetransmissionHandler.stop();
        }
        if (this.pubRelRetransmissionHandler != null) {
            this.pubRelRetransmissionHandler.stop();
        }
        if (payload != null) {
            payload.release();
        }
    }
}
