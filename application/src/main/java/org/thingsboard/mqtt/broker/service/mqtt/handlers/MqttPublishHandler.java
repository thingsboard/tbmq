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

import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.MqttConverter;
import org.thingsboard.mqtt.broker.exception.AuthorizationException;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.exception.NotSupportedQoSLevelException;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.validation.TopicValidationService;
import org.thingsboard.mqtt.broker.service.processing.MsgDispatcherService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.IncomingMessagesCtx;

import java.util.Collections;
import java.util.UUID;

@Service
@AllArgsConstructor
@Slf4j
public class MqttPublishHandler {
    private final MqttMessageGenerator mqttMessageGenerator;
    private final MsgDispatcherService msgDispatcherService;
    private final TopicValidationService topicValidationService;
    private final AuthorizationRuleService authorizationRuleService;
    private final DisconnectService disconnectService;
    private final MsgPersistenceManager msgPersistenceManager;

    public void process(ClientSessionCtx ctx, MqttPublishMessage msg) throws MqttException {
        topicValidationService.validateTopic(msg.variableHeader().topicName());

        UUID sessionId = ctx.getSessionId();
        String clientId = ctx.getClientId();
        try {
            authorizationRuleService.validateAuthorizationRule(ctx.getAuthorizationRule(), Collections.singleton(msg.variableHeader().topicName()));
        } catch (AuthorizationException e) {
            log.debug("[{}][{}] Client doesn't have permission to publish to the topic {}, reason - {}",
                    clientId, sessionId, e.getDeniedTopic(), e.getMessage());
            throw new MqttException(e);
        }
        int msgId = msg.variableHeader().packetId();
        MqttQoS msgQoS = msg.fixedHeader().qosLevel();

        if (msgQoS == MqttQoS.EXACTLY_ONCE) {
            if (preprocessQoS2Msg(msgId, ctx)) return;
        }
        log.trace("[{}][{}] Processing publish msg: {}", clientId, sessionId, msgId);
        PublishMsg publishMsg = MqttConverter.convertToPublishMsg(msg);
        msgDispatcherService.persistPublishMsg(ctx.getSessionInfo(), publishMsg, new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                log.trace("[{}][{}] Successfully acknowledged msg: {}", clientId, sessionId, msgId);
                acknowledgePacket(ctx, msgId, msgQoS);
            }

            @Override
            public void onFailure(Throwable t) {
                log.trace("[{}][{}] Failed to publish msg: {}", clientId, sessionId, publishMsg, t);
                disconnectService.disconnect(ctx, DisconnectReason.ON_ERROR);
            }
        });
    }

    // need this logic to ensure message was stored in Kafka before PUBREC response (and not duplicate processing of message)
    private boolean preprocessQoS2Msg(int msgId, ClientSessionCtx ctx) {
        String clientId = ctx.getClientId();
        UUID sessionId = ctx.getSessionId();
        IncomingMessagesCtx.QoS2PacketInfo awaitingPacketInfo = ctx.getIncomingMessagesCtx().getAwaitingPacket(msgId);
        if (awaitingPacketInfo != null) {
            int currentPacketsToReply = awaitingPacketInfo.getPacketsToReply().get();
            if (awaitingPacketInfo.getPersisted().get() || currentPacketsToReply == 0) {
                log.trace("[{}][{}] Message {} is awaiting for PUBREL packet.", clientId, sessionId, msgId);
                acknowledgePacket(ctx, msgId, MqttQoS.EXACTLY_ONCE);
            } else {
                log.trace("[{}][{}] Message {} is awaiting to be persisted.", clientId, sessionId, msgId);
                int actualPacketsToReply = awaitingPacketInfo.getPacketsToReply().compareAndExchange(currentPacketsToReply, currentPacketsToReply + 1);
                if (actualPacketsToReply != currentPacketsToReply) {
                    // it means that packet was successfully stored
                    acknowledgePacket(ctx, msgId, MqttQoS.EXACTLY_ONCE);
                }
            }
            return true;
        } else {
            ctx.getIncomingMessagesCtx().await(msgId);
        }
        return false;
    }

    private void acknowledgePacket(ClientSessionCtx ctx, int packetId, MqttQoS mqttQoS) {
        switch (mqttQoS) {
            case AT_MOST_ONCE:
                break;
            case AT_LEAST_ONCE:
                ctx.getChannel().writeAndFlush(mqttMessageGenerator.createPubAckMsg(packetId));
                break;
            case EXACTLY_ONCE:
                int packetsToReply = 1;
                if (ctx.getSessionInfo().isPersistent()) {
                    IncomingMessagesCtx.QoS2PacketInfo awaitingPacketInfo = ctx.getIncomingMessagesCtx().getAwaitingPacket(packetId);
                    if (awaitingPacketInfo == null) {
                        log.warn("[{}][{}] Couldn't find awaiting packet info for packet {}.", ctx.getClientId(), ctx.getSessionId(), packetId);
                    } else {
                        packetsToReply += awaitingPacketInfo.getPacketsToReply().getAndSet(0);
                        awaitingPacketInfo.getPersisted().getAndSet(true);
                    }
                }
                for (int i = 0; i < packetsToReply; i++) {
                    ctx.getChannel().writeAndFlush(mqttMessageGenerator.createPubRecMsg(packetId));
                }
                break;
            default:
                throw new NotSupportedQoSLevelException("QoS level " + mqttQoS + " is not supported.");
        }
    }

}
