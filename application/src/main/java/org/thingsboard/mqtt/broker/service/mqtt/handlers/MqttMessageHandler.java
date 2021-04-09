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
package org.thingsboard.mqtt.broker.service.mqtt.handlers;

import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.SessionState;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttMessageHandler {
    private final MqttMessageHandlers messageHandlers;
    private final KeepAliveService keepAliveService;

    public void process(ClientSessionCtx clientSessionCtx, MqttMessage msg) throws MqttException {
        clientSessionCtx.getProcessingLock().lock();
        try {
            if (clientSessionCtx.getSessionState() == SessionState.DISCONNECTED) {
                throw new MqttException("Session is already disconnected.");
            }
            MqttMessageType msgType = msg.fixedHeader().messageType();
            keepAliveService.acknowledgeControlPacket(clientSessionCtx.getSessionId());
            switch (msgType) {
                case SUBSCRIBE:
                    messageHandlers.getSubscribeHandler().process(clientSessionCtx, (MqttSubscribeMessage) msg);
                    break;
                case UNSUBSCRIBE:
                    messageHandlers.getUnsubscribeHandler().process(clientSessionCtx, (MqttUnsubscribeMessage) msg);
                    break;
                case PUBLISH:
                    messageHandlers.getPublishHandler().process(clientSessionCtx, (MqttPublishMessage) msg);
                    break;
                case PINGREQ:
                    messageHandlers.getPingHandler().process(clientSessionCtx);
                    break;
                case PUBACK:
                    messageHandlers.getPubAckHandler().process(clientSessionCtx, (MqttPubAckMessage) msg);
                    break;
                case PUBREC:
                    messageHandlers.getPubRecHandler().process(clientSessionCtx, ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId());
                    break;
                case PUBREL:
                    messageHandlers.getPubRelHandler().process(clientSessionCtx, ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId());
                    break;
                case PUBCOMP:
                    messageHandlers.getPubCompHandler().process(clientSessionCtx, ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId());
                    break;
                default:
                    throw new MqttException("Processing of " + msgType + " message is not allowed.");
            }
        } finally {
            clientSessionCtx.getProcessingLock().unlock();
        }
    }
}
