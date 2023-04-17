/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.actors.client.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.TbActorRef;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPubAckMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPubCompMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPubRecMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPubRelMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPublishMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttSubscribeMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttUnsubscribeMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.QueueableMqttMsg;
import org.thingsboard.mqtt.broker.actors.client.service.handlers.MqttMessageHandlers;
import org.thingsboard.mqtt.broker.actors.msg.MsgType;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

@Slf4j
@Component
@RequiredArgsConstructor
// not thread safe for one Client
public class MqttMessageHandlerImpl implements MqttMessageHandler {

    private final MqttMessageHandlers messageHandlers;
    private final KeepAliveService keepAliveService;

    @Override
    public boolean process(ClientSessionCtx clientSessionCtx, QueueableMqttMsg msg, TbActorRef actorRef) {
        MsgType msgType = msg.getMsgType();
        keepAliveService.acknowledgeControlPacket(clientSessionCtx.getSessionId());
        switch (msgType) {
            case MQTT_SUBSCRIBE_MSG:
                // TODO: maybe better to send separate msg to actor
                messageHandlers.getSubscribeHandler().process(clientSessionCtx, (MqttSubscribeMsg) msg);
                break;
            case MQTT_UNSUBSCRIBE_MSG:
                // TODO: maybe better to send separate msg to actor
                messageHandlers.getUnsubscribeHandler().process(clientSessionCtx, (MqttUnsubscribeMsg) msg);
                break;
            case MQTT_PUBLISH_MSG:
                messageHandlers.getPublishHandler().process(clientSessionCtx, (MqttPublishMsg) msg, actorRef);
                break;
            case MQTT_PING_MSG:
                messageHandlers.getPingHandler().process(clientSessionCtx);
                break;
            case MQTT_PUBACK_MSG:
                messageHandlers.getPubAckHandler().process(clientSessionCtx, ((MqttPubAckMsg) msg).getMessageId());
                break;
            case MQTT_PUBREC_MSG:
                messageHandlers.getPubRecHandler().process(clientSessionCtx, ((MqttPubRecMsg) msg).getMessageId());
                break;
            case MQTT_PUBREL_MSG:
                messageHandlers.getPubRelHandler().process(clientSessionCtx, ((MqttPubRelMsg) msg).getMessageId());
                break;
            case MQTT_PUBCOMP_MSG:
                messageHandlers.getPubCompHandler().process(clientSessionCtx, ((MqttPubCompMsg) msg).getMessageId());
                break;
            default:
                return false;
        }
        return true;
    }

    @Override
    public void processPubAckResponse(ClientSessionCtx clientSessionCtx, int msgId) {
        messageHandlers.getPublishHandler().processPubAckResponse(clientSessionCtx, msgId);
    }

    @Override
    public void processPubRecResponse(ClientSessionCtx clientSessionCtx, int msgId) {
        messageHandlers.getPublishHandler().processPubRecResponse(clientSessionCtx, msgId);
    }
}
