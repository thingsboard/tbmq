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
package org.thingsboard.mqtt.broker.actors.client.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.TbActorRef;
import org.thingsboard.mqtt.broker.actors.client.messages.PubAckResponseMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.PubRecResponseMsg;
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
import org.thingsboard.mqtt.broker.common.stats.StatsConstantNames;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogContext;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
// not thread safe for one Client
public class MqttMessageHandlerImpl implements MqttMessageHandler {

    private static final String PROCESS_MSG_LABEL = "Process msg";

    private final MqttMessageHandlers messageHandlers;
    private final KeepAliveService keepAliveService;
    private final ClientLogger clientLogger;

    @Override
    public boolean process(ClientSessionCtx clientSessionCtx, QueueableMqttMsg msg, TbActorRef actorRef) {
        MsgType msgType = msg.getMsgType();
        keepAliveService.acknowledgeControlPacket(clientSessionCtx.getSessionId());
        switch (msgType) {
            case MQTT_SUBSCRIBE_MSG:
                MqttSubscribeMsg subscribeMsg = (MqttSubscribeMsg) msg;
                logProcessMsg(clientSessionCtx, msgType, ctx -> ctx
                        .kv("topics", subscribeMsg.getTopicSubscriptions()));
                messageHandlers.getSubscribeHandler().process(clientSessionCtx, subscribeMsg);
                break;
            case MQTT_UNSUBSCRIBE_MSG:
                MqttUnsubscribeMsg unsubscribeMsg = (MqttUnsubscribeMsg) msg;
                logProcessMsg(clientSessionCtx, msgType, ctx -> ctx
                        .kv("topics", unsubscribeMsg.getTopics()));
                messageHandlers.getUnsubscribeHandler().process(clientSessionCtx, unsubscribeMsg);
                break;
            case MQTT_PUBLISH_MSG:
                MqttPublishMsg publishMsg = (MqttPublishMsg) msg;
                logProcessMsg(clientSessionCtx, msgType, ctx -> ctx
                        .kv("msgId", publishMsg.getPublishMsg().getPacketId())
                        .kv("qos", publishMsg.getPublishMsg().getQos())
                        .kv("topic", publishMsg.getPublishMsg().getTopicName()));
                messageHandlers.getPublishHandler().process(clientSessionCtx, publishMsg, actorRef);
                break;
            case MQTT_PING_MSG:
                logProcessMsg(clientSessionCtx, msgType, ctx -> {
                    // no extra fields for ping yet; keep hook for future
                });
                messageHandlers.getPingHandler().process(clientSessionCtx);
                break;
            case MQTT_PUBACK_MSG:
                MqttPubAckMsg pubAckMsg = (MqttPubAckMsg) msg;
                logProcessMsg(clientSessionCtx, msgType, ctx -> ctx
                        .kv("msgId", pubAckMsg.getMessageId()));
                messageHandlers.getPubAckHandler().process(clientSessionCtx, pubAckMsg.getMessageId());
                break;
            case MQTT_PUBREC_MSG:
                MqttPubRecMsg pubRecMsg = (MqttPubRecMsg) msg;
                logProcessMsg(clientSessionCtx, msgType, ctx -> ctx
                        .kv("msgId", pubRecMsg.getMessageId()));
                messageHandlers.getPubRecHandler().process(clientSessionCtx, pubRecMsg);
                break;
            case MQTT_PUBREL_MSG:
                MqttPubRelMsg pubRelMsg = (MqttPubRelMsg) msg;
                logProcessMsg(clientSessionCtx, msgType, ctx -> ctx
                        .kv("msgId", pubRelMsg.getMessageId()));
                messageHandlers.getPubRelHandler().process(clientSessionCtx, pubRelMsg.getMessageId());
                break;
            case MQTT_PUBCOMP_MSG:
                MqttPubCompMsg pubCompMsg = (MqttPubCompMsg) msg;
                logProcessMsg(clientSessionCtx, msgType, ctx -> ctx
                        .kv("msgId", pubCompMsg.getMessageId()));
                messageHandlers.getPubCompHandler().process(clientSessionCtx, pubCompMsg.getMessageId());
                break;
            default:
                return false;
        }
        return true;
    }

    @Override
    public void processPubAckResponse(ClientSessionCtx clientSessionCtx, PubAckResponseMsg msg) {
        logProcessMsg(clientSessionCtx, msg.getMsgType(), ctx -> ctx
                .kv("msgId", msg.getMessageId()));
        messageHandlers.getPublishHandler().processPubAckResponse(clientSessionCtx, msg);
    }

    @Override
    public void processPubRecResponse(ClientSessionCtx clientSessionCtx, PubRecResponseMsg msg) {
        logProcessMsg(clientSessionCtx, msg.getMsgType(), ctx -> ctx
                .kv("msgId", msg.getMessageId()));
        messageHandlers.getPublishHandler().processPubRecResponse(clientSessionCtx, msg);
    }

    private void logProcessMsg(ClientSessionCtx clientSessionCtx,
                               MsgType msgType,
                               Consumer<ClientLogContext> extra) {
        clientLogger.logEventWithDetails(clientSessionCtx.getClientId(), getClass(), ctx -> {
            ctx.msg(PROCESS_MSG_LABEL)
                    .kv(StatsConstantNames.MSG_TYPE, msgType);
            extra.accept(ctx);
        });
    }
}
