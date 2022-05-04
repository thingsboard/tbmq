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
package org.thingsboard.mqtt.broker.actors.client.service.handlers;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.actors.TbActorRef;
import org.thingsboard.mqtt.broker.actors.client.messages.DisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.PubAckResponseMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.PubRecResponseMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPublishMsg;
import org.thingsboard.mqtt.broker.actors.client.state.OrderedProcessingQueue;
import org.thingsboard.mqtt.broker.common.data.MqttQoS;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.exception.NotSupportedQoSLevelException;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.validation.TopicValidationService;
import org.thingsboard.mqtt.broker.service.processing.MsgDispatcherService;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;
import org.thingsboard.mqtt.broker.session.IncomingMessagesCtx;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
@Slf4j
public class MqttPublishHandler {
    private final MqttMessageGenerator mqttMessageGenerator;
    private final MsgDispatcherService msgDispatcherService;
    private final TopicValidationService topicValidationService;
    private final AuthorizationRuleService authorizationRuleService;
    private final ClientMqttActorManager clientMqttActorManager;
    private final ClientLogger clientLogger;

    public void process(ClientSessionCtx ctx, MqttPublishMsg msg, TbActorRef actorRef) throws MqttException {
        PublishMsg publishMsg = msg.getPublishMsg();
        int msgId = publishMsg.getPacketId();

        log.trace("[{}][{}] Processing publish msg: {}", ctx.getClientId(), ctx.getSessionId(), msgId);
        validatePubMsg(ctx, publishMsg);

        if (publishMsg.getQosLevel() == MqttQoS.EXACTLY_ONCE.value()) {
            if (processExactlyOnce(ctx, actorRef, msgId)) return;
        } else if (publishMsg.getQosLevel() == MqttQoS.AT_LEAST_ONCE.value()) {
            processAtLeastOnce(ctx, msgId);
        }

        clientLogger.logEvent(ctx.getClientId(), this.getClass(), "Sending PUBLISH");
        persistPubMsg(ctx, publishMsg, actorRef);
    }

    private void validatePubMsg(ClientSessionCtx ctx, PublishMsg publishMsg) {
        topicValidationService.validateTopic(publishMsg.getTopicName());
        validateClientAccess(ctx, publishMsg.getTopicName());
    }

    private boolean processExactlyOnce(ClientSessionCtx ctx, TbActorRef actorRef, int msgId) {
        addAwaiting(ctx.getRequestOrderCtx().getQos2PublishResponseMsgs(), msgId);
        return preprocessQoS2Msg(ctx, actorRef, msgId);
    }

    private void processAtLeastOnce(ClientSessionCtx ctx, int msgId) {
        addAwaiting(ctx.getRequestOrderCtx().getQos1PublishResponseMsgs(), msgId);
    }

    private void addAwaiting(OrderedProcessingQueue qosPublishResponseMsgs, int msgId) {
        qosPublishResponseMsgs.addAwaiting(msgId);
    }

    private void persistPubMsg(ClientSessionCtx ctx, PublishMsg publishMsg, TbActorRef actorRef) {
        msgDispatcherService.persistPublishMsg(ctx.getSessionInfo(), publishMsg, new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                clientLogger.logEvent(ctx.getClientId(), this.getClass(), "PUBLISH acknowledged");
                log.trace("[{}][{}] Successfully acknowledged msg: {}", ctx.getClientId(), ctx.getSessionId(), publishMsg.getPacketId());
                sendMsgFinishEventToActor(actorRef, ctx.getSessionId(), publishMsg.getPacketId(), MqttQoS.valueOf(publishMsg.getQosLevel()));
            }

            @Override
            public void onFailure(Throwable t) {
                log.warn("[{}][{}] Failed to publish msg: {}", ctx.getClientId(), ctx.getSessionId(), publishMsg.getPacketId(), t);
                clientMqttActorManager.disconnect(ctx.getClientId(), new DisconnectMsg(
                        ctx.getSessionId(),
                        new DisconnectReason(DisconnectReasonType.ON_ERROR, "Failed to publish msg")));
            }
        });
    }

    public void processPubAckResponse(ClientSessionCtx ctx, int msgId) {
        List<Integer> finishedMsgIds = ctx.getRequestOrderCtx().getQos1PublishResponseMsgs().finish(msgId);
        for (Integer finishedMsgId : finishedMsgIds) {
            ctx.getChannel().writeAndFlush(mqttMessageGenerator.createPubAckMsg(finishedMsgId));
        }
    }

    public void processPubRecResponse(ClientSessionCtx ctx, int msgId) {
        // TODO: test performance impact
        List<Integer> finishedMsgIds = ctx.getRequestOrderCtx().getQos2PublishResponseMsgs().finishAll(msgId);
        for (Integer finishedMsgId : finishedMsgIds) {
            ctx.getChannel().writeAndFlush(mqttMessageGenerator.createPubRecMsg(finishedMsgId));
        }
        IncomingMessagesCtx.QoS2PacketInfo awaitingPacketInfo = ctx.getIncomingMessagesCtx().getAwaitingPacket(msgId);
        if (awaitingPacketInfo != null && !awaitingPacketInfo.isPersisted()) {
            awaitingPacketInfo.setPersisted(true);
        }
    }

    // need this logic to ensure message was stored in Kafka before PUBREC response (and not duplicate processing of message)
    private boolean preprocessQoS2Msg(ClientSessionCtx ctx, TbActorRef actorRef, int msgId) {
        String clientId = ctx.getClientId();
        UUID sessionId = ctx.getSessionId();
        IncomingMessagesCtx.QoS2PacketInfo awaitingPacketInfo = ctx.getIncomingMessagesCtx().getAwaitingPacket(msgId);
        if (awaitingPacketInfo == null) {
            ctx.getIncomingMessagesCtx().await(msgId);
        } else if (!awaitingPacketInfo.isPersisted()) {
            log.trace("[{}][{}] Message {} is awaiting to be persisted.", clientId, sessionId, msgId);
        } else {
            log.trace("[{}][{}] Message {} is awaiting for PUBREL packet.", clientId, sessionId, msgId);
            sendMsgFinishEventToActor(actorRef, sessionId, msgId, MqttQoS.EXACTLY_ONCE);
        }
        return awaitingPacketInfo != null;
    }

    private void sendMsgFinishEventToActor(TbActorRef actorRef, UUID sessionId, int packetId, MqttQoS mqttQoS) {
        try {
            switch (mqttQoS) {
                case AT_MOST_ONCE:
                    break;
                case AT_LEAST_ONCE:
                    actorRef.tell(new PubAckResponseMsg(sessionId, packetId));
                    break;
                case EXACTLY_ONCE:
                    actorRef.tell(new PubRecResponseMsg(sessionId, packetId));
                    break;
                default:
                    throw new NotSupportedQoSLevelException("QoS level " + mqttQoS + " is not supported.");
            }
        } catch (Exception e) {
            log.error("[{}][{}] Failed to send msg finished event to actor. Exception - {}, message - {}", actorRef.getActorId(), sessionId,
                    e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void validateClientAccess(ClientSessionCtx ctx, String topic) {
        if (CollectionUtils.isEmpty(ctx.getAuthorizationRules())) {
            return;
        }
        boolean isClientAuthorized = authorizationRuleService.isAuthorized(topic, ctx.getAuthorizationRules());
        if (!isClientAuthorized) {
            log.warn("[{}][{}] Client is not authorized to publish to the topic {}",
                    ctx.getClientId(), ctx.getSessionId(), topic);
            throw new MqttException("Client is not authorized to publish to the topic");
        }
    }

}
