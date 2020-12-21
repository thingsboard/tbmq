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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.exception.NotSupportedQoSLevelException;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.SessionDisconnectListener;
import org.thingsboard.mqtt.broker.session.SessionListener;
import org.thingsboard.mqtt.broker.sevice.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.sevice.mqtt.MqttMessageHandlers;
import org.thingsboard.mqtt.broker.sevice.processing.PublishRetryService;
import org.thingsboard.mqtt.broker.sevice.processing.SuccessfulPublishService;
import org.thingsboard.mqtt.broker.sevice.subscription.SubscriptionService;

import java.net.InetSocketAddress;
import java.util.UUID;

@Slf4j
public class MqttSessionHandler extends ChannelInboundHandlerAdapter implements GenericFutureListener<Future<? super Void>>, SessionListener, SessionDisconnectListener {

    private final MqttMessageGenerator mqttMessageGenerator;
    private final MqttMessageHandlers messageHandlers;
    private final SubscriptionService subscriptionService;
    private final PublishRetryService retryService;
    private final SuccessfulPublishService successfulPublishService;

    private final UUID sessionId;

    private final ClientSessionCtx clientSessionCtx;
    private volatile InetSocketAddress address;

    MqttSessionHandler(MqttMessageGenerator mqttMessageGenerator, MqttMessageHandlers messageHandlers, SubscriptionService subscriptionService, PublishRetryService retryService, SuccessfulPublishService successfulPublishService) {
        this.mqttMessageGenerator = mqttMessageGenerator;
        this.messageHandlers = messageHandlers;
        this.subscriptionService = subscriptionService;
        this.retryService = retryService;
        this.successfulPublishService = successfulPublishService;
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
            messageHandlers.getDisconnectHandler().process(ctx, sessionId, this);
            return;
        }
        // TODO: we can leave order validation as long as we process connection synchronously
        if (!validOrder(msg.fixedHeader().messageType())) {
            log.info("[{}] Closing current session due to invalid msg order: {}", sessionId, msg);
            ctx.close();
            return;
        }
        clientSessionCtx.setChannel(ctx);
        switch (msg.fixedHeader().messageType()) {
            case CONNECT:
                messageHandlers.getConnectHandler().process(clientSessionCtx, (MqttConnectMessage) msg);
                break;
            case DISCONNECT:
                messageHandlers.getDisconnectHandler().process(ctx, sessionId, this);
                break;
            case SUBSCRIBE:
                messageHandlers.getSubscribeHandler().process(clientSessionCtx, (MqttSubscribeMessage) msg, this);
                break;
            case UNSUBSCRIBE:
                messageHandlers.getUnsubscribeHandler().process(clientSessionCtx, (MqttUnsubscribeMessage) msg);
                break;
            case PUBLISH:
                messageHandlers.getPublishHandler().process(clientSessionCtx, (MqttPublishMessage) msg, this);
                break;
            case PINGREQ:
                // TODO disconnect if there was no ping for a long time
                messageHandlers.getPingHandler().process(clientSessionCtx);
                break;
            case PUBACK:
            default:
                break;
        };
    }

    private boolean validOrder(MqttMessageType messageType) {
        switch (messageType) {
            case CONNECT:
                return !clientSessionCtx.isConnected();
            case PUBLISH:
            case PUBACK:
            case PUBREC:
            case PUBREL:
            case PUBCOMP:
            case SUBSCRIBE:
            case UNSUBSCRIBE:
            case PINGREQ:
            case DISCONNECT:
                return clientSessionCtx.isConnected();
            default:
                return false;
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // TODO push msg to the client before closing
        log.error("[{}] Unexpected Exception", sessionId, cause);
        ctx.close();
    }

    @Override
    public void operationComplete(Future<? super Void> future) throws Exception {
        onSessionDisconnect();
    }

    @Override
    public void onSessionDisconnect() {
        if (clientSessionCtx.isConnected()) {
            // TODO: add disconnect logic
            clientSessionCtx.setDisconnected();
            subscriptionService.unsubscribe(sessionId);
        }
    }

    @Override
    public void onPublishMsg(MqttQoS mqttQoS, QueueProtos.PublishMsgProto publishMessage) {
        try {
            MqttPublishMessage pubMsg = mqttMessageGenerator.createPubMsg(clientSessionCtx.nextMsgId(), publishMessage.getTopicName(),
                    mqttQoS, publishMessage.getPayload().toByteArray());
            String clientId = clientSessionCtx.getSessionInfo().getClientInfo().getClientId();
            switch (mqttQoS) {
                case AT_MOST_ONCE:
                    successfulPublishService.confirmSuccessfulPublish(clientId,
                            publishMessage.getPacketId());
                    break;
                case AT_LEAST_ONCE:
                    retryService.registerPublishRetry(clientSessionCtx.getChannel(), pubMsg, clientId, publishMessage.getPacketId());
                    break;
                default:
                    throw new NotSupportedQoSLevelException("QoS level " + mqttQoS + " is not supported.");
            }
            clientSessionCtx.getChannel().writeAndFlush(pubMsg);
        } catch (Exception e) {
            log.trace("[{}] Failed to send publish msg to MQTT client.", sessionId, e);
        }
    }
}
