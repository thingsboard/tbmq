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
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import io.netty.handler.ssl.NotSslRecordException;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.mqtt.client.ClientSessionManager;
import org.thingsboard.mqtt.broker.service.mqtt.handlers.MqttMessageHandlers;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.SessionDisconnectListener;

import javax.net.ssl.SSLHandshakeException;
import java.net.InetSocketAddress;
import java.util.UUID;

@Slf4j
public class MqttSessionHandler extends ChannelInboundHandlerAdapter implements GenericFutureListener<Future<? super Void>>, SessionDisconnectListener {

    private final MqttMessageHandlers messageHandlers;
    private final KeepAliveService keepAliveService;
    private final LastWillService lastWillService;
    private final SubscriptionManager subscriptionManager;
    private final ClientSessionManager clientSessionManager;
    private final SslHandler sslHandler;

    private final UUID sessionId;

    private final ClientSessionCtx clientSessionCtx;

    MqttSessionHandler(MqttMessageHandlers messageHandlers, KeepAliveService keepAliveService, LastWillService lastWillService, SubscriptionManager subscriptionManager, ClientSessionManager clientSessionManager, SslHandler sslHandler) {
        this.messageHandlers = messageHandlers;
        this.keepAliveService = keepAliveService;
        this.lastWillService = lastWillService;
        this.subscriptionManager = subscriptionManager;
        this.clientSessionManager = clientSessionManager;
        this.sslHandler = sslHandler;
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
                    log.warn("[{}] Message decoding failed: {}", sessionId, message.decoderResult().cause().getMessage());
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
        InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
        if (msg.fixedHeader() == null) {
            log.warn("[{}:{}] Invalid message received", address.getHostName(), address.getPort());
            messageHandlers.getDisconnectHandler().process(ctx, sessionId, this);
            return;
        }
        // TODO: we can leave order validation as long as we process connection synchronously
        MqttMessageType msgType = msg.fixedHeader().messageType();
        if (!validOrder(msgType)) {
            log.warn("[{}] Closing current session due to invalid msg order: {}", sessionId, msg);
            ctx.close();
            return;
        }
        clientSessionCtx.setChannel(ctx);
        try {
            processKeepAlive(ctx, msg);
            switch (msgType) {
                case CONNECT:
                    messageHandlers.getConnectHandler().process(clientSessionCtx, sslHandler, (MqttConnectMessage) msg);
                    break;
                case DISCONNECT:
                    messageHandlers.getDisconnectHandler().process(ctx, sessionId, this);
                    break;
                case SUBSCRIBE:
                    messageHandlers.getSubscribeHandler().process(clientSessionCtx, (MqttSubscribeMessage) msg);
                    break;
                case UNSUBSCRIBE:
                    messageHandlers.getUnsubscribeHandler().process(clientSessionCtx, (MqttUnsubscribeMessage) msg);
                    break;
                case PUBLISH:
                    messageHandlers.getPublishHandler().process(clientSessionCtx, (MqttPublishMessage) msg, this);
                    break;
                case PINGREQ:
                    messageHandlers.getPingHandler().process(clientSessionCtx);
                    break;
                case PUBACK:
                    messageHandlers.getPubAckHandler().process(clientSessionCtx, (MqttPubAckMessage) msg);
                default:
                    break;
            }
        } catch (MqttException e) {
            log.warn("[{}] Failed to process {} msg. Reason - {}.",
                    sessionId, msgType, e.getMessage());
            onSessionDisconnect(DisconnectReason.ON_ERROR);
            ctx.close();
        }
    }

    private void processKeepAlive(ChannelHandlerContext ctx, MqttMessage msg) throws MqttException {
        MqttMessageType msgType = msg.fixedHeader().messageType();
        if (msgType == MqttMessageType.CONNECT) {
            keepAliveService.registerSession(sessionId, ((MqttConnectMessage) msg).variableHeader().keepAliveTimeSeconds(),
                    () -> {
                        log.warn("[{}] Disconnecting client due to inactivity.", sessionId);
                        onSessionDisconnect(DisconnectReason.ON_ERROR);
                        ctx.close();
                    });
        } else {
            keepAliveService.acknowledgeControlPacket(sessionId);
        }
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
        if (cause.getCause() instanceof SSLHandshakeException) {
            log.warn("[{}] Exception on SSL handshake. Reason - {}", sessionId, cause.getCause().getMessage());
        } else if (cause.getCause() instanceof NotSslRecordException) {
            log.warn("[{}] NotSslRecordException: {}", sessionId, cause.getCause().getMessage());
        } else  {
            log.error("[{}] Unexpected Exception", sessionId, cause);
        }
        onSessionDisconnect(DisconnectReason.ON_ERROR);
        ctx.close();
    }

    @Override
    public void operationComplete(Future<? super Void> future) throws Exception {
        onSessionDisconnect(DisconnectReason.ON_CHANNEL_CLOSED);
    }

    @Override
    public void onSessionDisconnect(DisconnectReason reason) {
        boolean isStateCleared = clientSessionCtx.tryClearState();
        if (!isStateCleared) {
            clientSessionCtx.setDisconnected();
            boolean sendLastWill = !DisconnectReason.ON_DISCONNECT_MSG.equals(reason);
            lastWillService.removeLastWill(sessionId, sendLastWill);
            String clientId = getClientId(clientSessionCtx.getSessionInfo());
            keepAliveService.unregisterSession(sessionId);
            if (clientId != null) {
                if (!clientSessionCtx.getSessionInfo().isPersistent()) {
                    subscriptionManager.clearSubscriptions(clientId);
                }
                clientSessionManager.unregisterClient(clientId);
            }
        }
    }

    private String getClientId(SessionInfo sessionInfo) {
        if (sessionInfo == null || sessionInfo.getClientInfo() == null) {
            return null;
        }
        return sessionInfo.getClientInfo().getClientId();
    }
}
