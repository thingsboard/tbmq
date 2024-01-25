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
package org.thingsboard.mqtt.broker.actors.client.service.handlers;

import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.netty.handler.codec.mqtt.MqttVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.TbActorRef;
import org.thingsboard.mqtt.broker.actors.client.messages.PubAckResponseMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.PubRecResponseMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPublishMsg;
import org.thingsboard.mqtt.broker.actors.client.state.OrderedProcessingQueue;
import org.thingsboard.mqtt.broker.common.data.MqttQoS;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.exception.FullMsgQueueException;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.exception.NotSupportedQoSLevelException;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgProcessor;
import org.thingsboard.mqtt.broker.service.mqtt.validation.TopicValidationService;
import org.thingsboard.mqtt.broker.service.processing.MsgDispatcherService;
import org.thingsboard.mqtt.broker.session.AwaitingPubRelPacketsCtx;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;
import org.thingsboard.mqtt.broker.session.TopicAliasCtx;
import org.thingsboard.mqtt.broker.util.MqttReasonCodeResolver;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Service
@RequiredArgsConstructor
@Slf4j
public class MqttPublishHandler {

    private final MqttMessageGenerator mqttMessageGenerator;
    private final MsgDispatcherService msgDispatcherService;
    private final TopicValidationService topicValidationService;
    private final AuthorizationRuleService authorizationRuleService;
    private final ClientMqttActorManager clientMqttActorManager;
    private final ClientLogger clientLogger;
    private final RetainedMsgProcessor retainedMsgProcessor;

    private final boolean isTraceEnabled = log.isTraceEnabled();

    @Value("${mqtt.handler.all_msg_callback_threads:0}")
    private int threadsCount;

    private ExecutorService callbackProcessor;

    @PostConstruct
    public void init() {
        this.callbackProcessor = ThingsBoardExecutors.initExecutorService(threadsCount, "publish-callback-processor");
    }

    public void process(ClientSessionCtx ctx, MqttPublishMsg msg, TbActorRef actorRef) throws MqttException {
        PublishMsg publishMsg = msg.getPublishMsg();
        int msgId = publishMsg.getPacketId();

        if (isTraceEnabled) {
            log.trace("[{}][{}] Processing publish msg: {}", ctx.getClientId(), ctx.getSessionId(), publishMsg);
        }

        String topicNameByAlias;
        try {
            topicNameByAlias = ctx.getTopicAliasCtx().getTopicNameByAlias(publishMsg);
        } catch (MqttException e) {
            log.warn("[{}][{}] Failed to process publish msg: {}", ctx.getClientId(), ctx.getSessionId(), publishMsg.getPacketId(), e);
            if (TopicAliasCtx.UNKNOWN_TOPIC_ALIAS_MSG.equals(e.getMessage())) {
                disconnectClient(ctx, DisconnectReasonType.ON_PROTOCOL_ERROR, e.getMessage());
            } else {
                disconnectClient(ctx, DisconnectReasonType.ON_TOPIC_ALIAS_INVALID, e.getMessage());
            }
            return;
        }
        if (topicNameByAlias != null) {
            publishMsg = buildPublishMsgWithTopicName(publishMsg, topicNameByAlias);
        }

        boolean validateSuccess = validatePubMsg(ctx, publishMsg);
        if (!validateSuccess) {
            return;
        }

        try {
            if (MqttQoS.EXACTLY_ONCE.value() == publishMsg.getQosLevel()) {
                if (processExactlyOnceAndCheckIfAlreadyPublished(ctx, actorRef, msgId)) return;
            } else if (MqttQoS.AT_LEAST_ONCE.value() == publishMsg.getQosLevel()) {
                processAtLeastOnce(ctx, msgId);
            }
        } catch (FullMsgQueueException e) {
            log.warn("[{}][{}] Failed to process publish msg: {}", ctx.getClientId(), ctx.getSessionId(), publishMsg.getPacketId(), e);
            disconnectClient(ctx, DisconnectReasonType.ON_RECEIVE_MAXIMUM_EXCEEDED, e.getMessage());
            return;
        }

        if (publishMsg.isRetained()) {
            if (isTraceEnabled) {
                log.trace("[{}] Processing retain msg {}", ctx.getClientId(), publishMsg);
            }
            publishMsg = retainedMsgProcessor.process(publishMsg);
        }

        clientLogger.logEvent(ctx.getClientId(), this.getClass(), "Sending PUBLISH");
        persistPubMsg(ctx, publishMsg, actorRef);
    }

    boolean validatePubMsg(ClientSessionCtx ctx, PublishMsg publishMsg) {
        try {
            topicValidationService.validateTopic(publishMsg.getTopicName());
        } catch (DataValidationException e) {
            log.warn("[{}] Failed to validate topic for Pub msg {}", ctx.getClientId(), publishMsg, e);
            return handleTopicValidationException(ctx, publishMsg, e);
        }
        try {
            validateClientAccess(ctx, publishMsg.getTopicName());
        } catch (MqttException e) {
            return handleClientNotAuthException(ctx, publishMsg, e);
        }
        return true;
    }

    private boolean handleTopicValidationException(ClientSessionCtx ctx, PublishMsg publishMsg, DataValidationException e) {
        if (MqttVersion.MQTT_5 == ctx.getMqttVersion()) {
            if (publishMsg.getQosLevel() == 2) {
                pushPubRecErrorResponseWithReasonCode(ctx, publishMsg, MqttReasonCodeResolver.pubRecTopicNameInvalid());
            } else if (publishMsg.getQosLevel() == 1) {
                pushPubAckErrorResponseWithReasonCode(ctx, publishMsg, MqttReasonCodeResolver.pubAckTopicNameInvalid());
            } else {
                // QoS=0 - do nothing
            }
            return false;
        } else {
            throw e;
        }
    }

    private boolean handleClientNotAuthException(ClientSessionCtx ctx, PublishMsg publishMsg, MqttException e) {
        if (MqttVersion.MQTT_5 == ctx.getMqttVersion()) {
            if (publishMsg.getQosLevel() == 2) {
                pushPubRecErrorResponseWithReasonCode(ctx, publishMsg, MqttReasonCodeResolver.pubRecNotAuthorized());
            } else if (publishMsg.getQosLevel() == 1) {
                pushPubAckErrorResponseWithReasonCode(ctx, publishMsg, MqttReasonCodeResolver.pubAckNotAuthorized());
            } else {
                // QoS=0 - do nothing
            }
            return false;
        } else {
            throw e;
        }
    }

    private void pushPubAckErrorResponseWithReasonCode(ClientSessionCtx ctx, PublishMsg publishMsg, MqttReasonCodes.PubAck code) {
        ctx.getChannel().writeAndFlush(mqttMessageGenerator.createPubAckMsg(publishMsg.getPacketId(), code));
    }

    private void pushPubRecErrorResponseWithReasonCode(ClientSessionCtx ctx, PublishMsg publishMsg, MqttReasonCodes.PubRec code) {
        ctx.getChannel().writeAndFlush(mqttMessageGenerator.createPubRecMsg(publishMsg.getPacketId(), code));
    }

    boolean processExactlyOnceAndCheckIfAlreadyPublished(ClientSessionCtx ctx, TbActorRef actorRef, int msgId) {
        addAwaiting(ctx.getPubResponseProcessingCtx().getQos2PubRecResponseMessages(), msgId);
        return prepareForPubRelPacketAndCheckIfAlreadyProcessed(ctx, actorRef, msgId);
    }

    void processAtLeastOnce(ClientSessionCtx ctx, int msgId) {
        addAwaiting(ctx.getPubResponseProcessingCtx().getQos1PubAckResponseMessages(), msgId);
    }

    private void addAwaiting(OrderedProcessingQueue qosPublishResponseMessages, int msgId) {
        qosPublishResponseMessages.addAwaiting(msgId);
    }

    void persistPubMsg(ClientSessionCtx ctx, PublishMsg publishMsg, TbActorRef actorRef) {
        msgDispatcherService.persistPublishMsg(ctx.getSessionInfo(), publishMsg, new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                callbackProcessor.submit(() -> {
                    clientLogger.logEvent(ctx.getClientId(), this.getClass(), "PUBLISH acknowledged");
                    if (isTraceEnabled) {
                        log.trace("[{}][{}] Successfully acknowledged msg: {}", ctx.getClientId(), ctx.getSessionId(), publishMsg.getPacketId());
                    }
                    sendPubResponseEventToActor(actorRef, ctx.getSessionId(), publishMsg.getPacketId(), MqttQoS.valueOf(publishMsg.getQosLevel()));
                });
            }

            @Override
            public void onFailure(Throwable t) {
                callbackProcessor.submit(() -> {
                    log.warn("[{}][{}] Failed to publish msg: {}", ctx.getClientId(), ctx.getSessionId(), publishMsg.getPacketId(), t);
                    disconnectClient(ctx, DisconnectReasonType.ON_ERROR, "Failed to publish msg");
                });
            }
        });
    }

    public void processPubAckResponse(ClientSessionCtx ctx, int msgId) {
        MqttReasonCodes.PubAck code = MqttReasonCodeResolver.pubAckSuccess(ctx);
        List<Integer> finishedMsgIds = ctx.getPubResponseProcessingCtx().getQos1PubAckResponseMessages().finish(msgId);
        for (var finishedMsgId : finishedMsgIds) {
            ctx.getChannel().write(mqttMessageGenerator.createPubAckMsg(finishedMsgId, code));
        }
        ctx.getChannel().flush();
    }

    public void processPubRecResponse(ClientSessionCtx ctx, int msgId) {
        MqttReasonCodes.PubRec code = MqttReasonCodeResolver.pubRecSuccess(ctx);
        List<Integer> finishedMsgIds = ctx.getPubResponseProcessingCtx().getQos2PubRecResponseMessages().finishAll(msgId);
        for (var finishedMsgId : finishedMsgIds) {
            ctx.getChannel().write(mqttMessageGenerator.createPubRecMsg(finishedMsgId, code));
        }
        ctx.getChannel().flush();

        AwaitingPubRelPacketsCtx.QoS2PubRelPacketInfo awaitingPacketInfo = ctx.getAwaitingPubRelPacketsCtx().getAwaitingPacket(msgId);
        if (isNotPersisted(awaitingPacketInfo)) {
            awaitingPacketInfo.setPersisted(true);
        }
    }

    private boolean isNotPersisted(AwaitingPubRelPacketsCtx.QoS2PubRelPacketInfo awaitingPacketInfo) {
        return awaitingPacketInfo != null && !awaitingPacketInfo.isPersisted();
    }

    // need this logic to ensure message was stored in Kafka before PUBREC response (and not duplicate processing of message)
    private boolean prepareForPubRelPacketAndCheckIfAlreadyProcessed(ClientSessionCtx ctx, TbActorRef actorRef, int msgId) {
        String clientId = ctx.getClientId();
        UUID sessionId = ctx.getSessionId();
        AwaitingPubRelPacketsCtx.QoS2PubRelPacketInfo awaitingPacketInfo = ctx.getAwaitingPubRelPacketsCtx().getAwaitingPacket(msgId);
        if (awaitingPacketInfo == null) {
            ctx.getAwaitingPubRelPacketsCtx().await(clientId, msgId);
        } else if (!awaitingPacketInfo.isPersisted()) {
            if (isTraceEnabled) {
                log.trace("[{}][{}] Message {} is awaiting to be persisted.", clientId, sessionId, msgId);
            }
        } else {
            if (isTraceEnabled) {
                log.trace("[{}][{}] Message {} is awaiting for PUBREL packet.", clientId, sessionId, msgId);
            }
            sendPubResponseEventToActor(actorRef, sessionId, msgId, MqttQoS.EXACTLY_ONCE);
        }
        return awaitingPacketInfo != null;
    }

    private void sendPubResponseEventToActor(TbActorRef actorRef, UUID sessionId, int packetId, MqttQoS mqttQoS) {
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
            log.error("[{}][{}] Failed to send msg finished event to actor", actorRef.getActorId(), sessionId, e);
        }
    }

    void validateClientAccess(ClientSessionCtx ctx, String topic) {
        boolean isClientAuthorized = authorizationRuleService.isPubAuthorized(ctx.getClientId(), topic, ctx.getAuthRulePatterns());
        if (!isClientAuthorized) {
            log.warn("[{}][{}][{}] Client is not authorized to publish to the topic {}",
                    ctx.getClientId(), ctx.getSessionId(), ctx.getAuthRulePatterns(), topic);
            throw new MqttException("Client is not authorized to publish to the topic");
        }
    }

    private PublishMsg buildPublishMsgWithTopicName(PublishMsg publishMsg, String topicName) {
        return publishMsg.toBuilder().topicName(topicName).build();
    }

    private void disconnectClient(ClientSessionCtx ctx, DisconnectReasonType disconnectReasonType, String message) {
        clientMqttActorManager.disconnect(
                ctx.getClientId(),
                new MqttDisconnectMsg(
                        ctx.getSessionId(),
                        new DisconnectReason(disconnectReasonType, message)));
    }

    @PreDestroy
    public void destroy() {
        if (callbackProcessor != null) {
            callbackProcessor.shutdownNow();
        }
    }
}
