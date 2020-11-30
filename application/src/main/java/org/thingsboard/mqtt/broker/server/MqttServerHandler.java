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
package org.thingsboard.mqtt.broker.server;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnAckVariableHeader;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttSubAckPayload;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.exception.NotSupportedQoSLevelException;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.SessionInfoCreator;
import org.thingsboard.mqtt.broker.sevice.processing.MsgDispatcherService;
import org.thingsboard.mqtt.broker.sevice.subscription.SubscriptionService;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_ACCEPTED;
import static io.netty.handler.codec.mqtt.MqttMessageType.CONNACK;
import static io.netty.handler.codec.mqtt.MqttMessageType.PINGRESP;
import static io.netty.handler.codec.mqtt.MqttMessageType.PUBACK;
import static io.netty.handler.codec.mqtt.MqttMessageType.SUBACK;
import static io.netty.handler.codec.mqtt.MqttMessageType.UNSUBACK;
import static io.netty.handler.codec.mqtt.MqttQoS.AT_LEAST_ONCE;
import static io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE;
import static io.netty.handler.codec.mqtt.MqttQoS.FAILURE;

@Slf4j
public class MqttServerHandler extends ChannelInboundHandlerAdapter implements GenericFutureListener<Future<? super Void>> {
    private static final MqttQoS MAX_SUPPORTED_QOS_LVL = AT_LEAST_ONCE;

    private final SubscriptionService subscriptionService;
    private final MsgDispatcherService msgDispatcherService;

    private final UUID sessionId;

    private final ClientSessionCtx clientSessionCtx;
    private volatile InetSocketAddress address;

    MqttServerHandler(SubscriptionService subscriptionService, MsgDispatcherService msgDispatcherService) {
        this.subscriptionService = subscriptionService;
        this.msgDispatcherService = msgDispatcherService;
        this.sessionId = UUID.randomUUID();
        this.clientSessionCtx = new ClientSessionCtx(sessionId);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        log.trace("[{}] Processing msg: {}", sessionId, msg);
        try {
            if (msg instanceof MqttMessage) {
                MqttMessage message = (MqttMessage) msg;
                if (message.decoderResult().isSuccess()) {
                    processMqttMsg(ctx, message);
                } else {
                    log.error("[{}] Message processing failed: {}", sessionId, message.decoderResult().cause().getMessage());
                    ctx.close();
                }
            } else {
                ctx.close();
            }
        } finally {
            ReferenceCountUtil.safeRelease(msg);
        }
    }

    private void processMqttMsg(ChannelHandlerContext ctx, MqttMessage msg) {
        address = (InetSocketAddress) ctx.channel().remoteAddress();
        if (msg.fixedHeader() == null) {
            log.info("[{}:{}] Invalid message received", address.getHostName(), address.getPort());
            processDisconnect(ctx);
            return;
        }
        if (!validOrder(msg.fixedHeader().messageType())) {
            log.info("[{}] Closing current session due to invalid msg order: {}", sessionId, msg);
            ctx.close();
            return;
        }
        clientSessionCtx.setChannel(ctx);
        switch (msg.fixedHeader().messageType()) {
            case CONNECT:
                processConnect(ctx, (MqttConnectMessage) msg);
                break;
            case PUBLISH:
                processPublish(ctx, (MqttPublishMessage) msg);
                break;
            case SUBSCRIBE:
                processSubscribe(ctx, (MqttSubscribeMessage) msg);
                break;
            case UNSUBSCRIBE:
                processUnsubscribe(ctx, (MqttUnsubscribeMessage) msg);
                break;
            case PINGREQ:
                processPing(ctx, (MqttConnectMessage) msg);
                break;
            case DISCONNECT:
                processDisconnect(ctx);
                break;
            default:
                break;
        }
    }

    private boolean validOrder(MqttMessageType messageType) {
        switch (messageType) {
            case CONNECT:
                return !clientSessionCtx.isConnected();
            case PUBLISH:
            case SUBSCRIBE:
            case UNSUBSCRIBE:
            case PINGREQ:
            case DISCONNECT:
                return clientSessionCtx.isConnected();
            default:
                return false;
        }
    }

    private void processPing(ChannelHandlerContext ctx, MqttConnectMessage msg) {
        ctx.writeAndFlush(new MqttMessage(new MqttFixedHeader(PINGRESP, false, AT_MOST_ONCE, false, 0)));
    }

    private void processUnsubscribe(ChannelHandlerContext ctx, MqttUnsubscribeMessage mqttMsg) {
        List<String> topics = mqttMsg.payload().topics();
        log.trace("[{}] Processing unsubscribe [{}], topics - {}", sessionId, mqttMsg.variableHeader().messageId(), topics);

        ListenableFuture<Void> unsubscribeFuture = subscriptionService.unsubscribe(
                clientSessionCtx.getSessionInfo().getClientInfo().getClientId(), topics);

        // TODO: test this manually
        unsubscribeFuture.addListener(() -> {
            ctx.writeAndFlush(createUnSubAckMessage(mqttMsg.variableHeader().messageId()));
        }, MoreExecutors.directExecutor());
    }

    private void processSubscribe(ChannelHandlerContext ctx, MqttSubscribeMessage mqttMsg) {
        List<MqttTopicSubscription> subscriptions = mqttMsg.payload().topicSubscriptions();
        log.trace("[{}] Processing subscribe [{}], subscriptions - {}", sessionId, mqttMsg.variableHeader().messageId(), subscriptions);

        ListenableFuture<Void> subscribeFuture = subscriptionService.subscribe(clientSessionCtx.getSessionInfo().getClientInfo().getClientId(), subscriptions);

        subscribeFuture.addListener(() -> {
            List<Integer> grantedQoSList = subscriptions.stream().map(sub -> getMinSupportedQos(sub.qualityOfService())).collect(Collectors.toList());
            ctx.writeAndFlush(createSubAckMessage(mqttMsg.variableHeader().messageId(), grantedQoSList));
        }, MoreExecutors.directExecutor());
    }

    private void processConnect(ChannelHandlerContext ctx, MqttConnectMessage msg) {
        log.info("[{}] Processing connect msg for client: {}!", sessionId, msg.payload().clientIdentifier());
        String clientId = msg.payload().clientIdentifier();
        // TODO: login and get tenantId if there's such
        SessionInfo sessionInfo = SessionInfoCreator.create(sessionId, clientId, !msg.variableHeader().isCleanSession(), null);
        clientSessionCtx.setSessionInfo(sessionInfo);
        clientSessionCtx.setSessionInfoProto(SessionInfoCreator.createProto(sessionInfo));
        ctx.writeAndFlush(createMqttConnAckMsg(CONNECTION_ACCEPTED));
        log.info("[{}] Client connected!", sessionId);
    }

    private void processPublish(ChannelHandlerContext ctx, MqttPublishMessage mqttMsg) {
        validatePublish(mqttMsg);
        String topicName = mqttMsg.variableHeader().topicName();
        int msgId = mqttMsg.variableHeader().packetId();
        log.trace("[{}][{}] Processing publish msg [{}][{}]!", sessionId, clientSessionCtx.getSessionInfo().getClientInfo().getClientId(), topicName, msgId);

        msgDispatcherService.acknowledgePublishMsg(clientSessionCtx.getSessionInfoProto(), mqttMsg, new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                acknowledgeMsg(ctx, mqttMsg);
            }

            @Override
            public void onFailure(Throwable t) {
                log.trace("[{}] Failed to publish msg: {}", sessionId, mqttMsg, t);
                processDisconnect(ctx);
            }
        });
    }

    private void validatePublish(MqttPublishMessage mqttMsg) {
        MqttQoS mqttQoS = mqttMsg.fixedHeader().qosLevel();
        if (getMinSupportedQos(mqttQoS) != mqttQoS.value()) {
            throw new NotSupportedQoSLevelException("QoS level " + mqttQoS + " is not supported.");
        }
    }

    private void acknowledgeMsg(ChannelHandlerContext ctx, MqttPublishMessage mqttMsg) {
        MqttQoS mqttQoS = mqttMsg.fixedHeader().qosLevel();
        switch (mqttQoS) {
            case AT_MOST_ONCE:
                break;
            case AT_LEAST_ONCE:
                ctx.writeAndFlush(createPubAckMsg(mqttMsg.variableHeader().packetId()));
                break;
            default:
                throw new NotSupportedQoSLevelException("QoS level " + mqttQoS + " is not supported.");
        }
    }

    private static int getMinSupportedQos(MqttQoS reqQoS) {
        return Math.min(reqQoS.value(), MAX_SUPPORTED_QOS_LVL.value());
    }

    private static MqttConnAckMessage createMqttConnAckMsg(MqttConnectReturnCode returnCode) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(CONNACK, false, AT_MOST_ONCE, false, 0);
        MqttConnAckVariableHeader mqttConnAckVariableHeader =
                new MqttConnAckVariableHeader(returnCode, true);
        return new MqttConnAckMessage(mqttFixedHeader, mqttConnAckVariableHeader);
    }

    private static MqttMessage createUnSubAckMessage(int msgId) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(UNSUBACK, false, AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader mqttMessageIdVariableHeader = MqttMessageIdVariableHeader.from(msgId);
        return new MqttMessage(mqttFixedHeader, mqttMessageIdVariableHeader);
    }

    private static MqttSubAckMessage createSubAckMessage(int msgId, List<Integer> grantedQoSList) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(SUBACK, false, AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader mqttMessageIdVariableHeader = MqttMessageIdVariableHeader.from(msgId);
        MqttSubAckPayload mqttSubAckPayload = new MqttSubAckPayload(grantedQoSList);
        return new MqttSubAckMessage(mqttFixedHeader, mqttMessageIdVariableHeader, mqttSubAckPayload);
    }

    public static MqttPubAckMessage createPubAckMsg(int requestId) {
        MqttFixedHeader mqttFixedHeader =
                new MqttFixedHeader(PUBACK, false, AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader mqttMsgIdVariableHeader =
                MqttMessageIdVariableHeader.from(requestId);
        return new MqttPubAckMessage(mqttFixedHeader, mqttMsgIdVariableHeader);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("[{}] Unexpected Exception", sessionId, cause);
        ctx.close();
    }

    private void processDisconnect(ChannelHandlerContext ctx) {
        ctx.close();
        log.info("[{}] Client disconnected!", sessionId);
        doDisconnect();
    }

    @Override
    public void operationComplete(Future<? super Void> future) throws Exception {
        doDisconnect();
    }

    private void doDisconnect() {
        if (clientSessionCtx.isConnected()) {
            // TODO: add disconnect logic
            clientSessionCtx.setDisconnected();
        }
    }

}
