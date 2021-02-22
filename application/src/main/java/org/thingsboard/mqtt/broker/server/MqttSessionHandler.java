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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.mqtt.client.DisconnectService;
import org.thingsboard.mqtt.broker.service.mqtt.handlers.MqttMessageHandlers;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;

import javax.net.ssl.SSLHandshakeException;
import java.net.InetSocketAddress;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class MqttSessionHandler extends ChannelInboundHandlerAdapter implements GenericFutureListener<Future<? super Void>> {

    private final MqttMessageHandlers messageHandlers;
    private final KeepAliveService keepAliveService;
    private final DisconnectService disconnectService;
    private final SslHandler sslHandler;

    private final UUID sessionId = UUID.randomUUID();
    private final ClientSessionCtx clientSessionCtx = new ClientSessionCtx(sessionId);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        log.trace("[{}] Processing msg: {}", sessionId, msg);
        clientSessionCtx.setChannel(ctx);
        try {
            if (msg instanceof MqttMessage) {
                MqttMessage message = (MqttMessage) msg;
                if (message.decoderResult().isSuccess()) {
                    processMqttMsg(ctx, message);
                } else {
                    log.warn("[{}] Message decoding failed: {}", sessionId, message.decoderResult().cause().getMessage());
                    disconnectService.disconnect(clientSessionCtx, DisconnectReason.ON_ERROR);
                }
            } else {
                log.warn("[{}] Received unknown message", sessionId);
                disconnectService.disconnect(clientSessionCtx, DisconnectReason.ON_ERROR);
            }
        } finally {
            ReferenceCountUtil.safeRelease(msg);
        }
    }

    private void processMqttMsg(ChannelHandlerContext ctx, MqttMessage msg) {
        InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
        if (msg.fixedHeader() == null) {
            log.warn("[{}][{}:{}] Invalid message received", sessionId, address.getHostName(), address.getPort());
            disconnectService.disconnect(clientSessionCtx, DisconnectReason.ON_ERROR);
            return;
        }
        // TODO: we can leave order validation as long as we process connection synchronously
        MqttMessageType msgType = msg.fixedHeader().messageType();
        if (!validOrder(msgType)) {
            log.warn("[{}] Closing current session due to invalid msg order: {}", sessionId, msg);
            disconnectService.disconnect(clientSessionCtx, DisconnectReason.ON_ERROR);
            return;
        }
        clientSessionCtx.getLock().lock();
        try {
            if (!validOrder(msgType)) {
                log.warn("[{}] Closing current session due to invalid msg order: {}", sessionId, msg);
                disconnectService.disconnect(clientSessionCtx, DisconnectReason.ON_ERROR);
                return;
            }
            processKeepAlive(ctx, msg);
            switch (msgType) {
                case CONNECT:
                    messageHandlers.getConnectHandler().process(clientSessionCtx, sslHandler, (MqttConnectMessage) msg);
                    break;
                case DISCONNECT:
                    messageHandlers.getDisconnectHandler().process(clientSessionCtx);
                    break;
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
                default:
                    break;
            }
        } catch (MqttException e) {
            log.warn("[{}] Failed to process {} msg. Reason - {}.",
                    sessionId, msgType, e.getMessage());
            disconnectService.disconnect(clientSessionCtx, DisconnectReason.ON_ERROR);
        } finally {
            clientSessionCtx.getLock().unlock();
        }
    }

    private void processKeepAlive(ChannelHandlerContext ctx, MqttMessage msg) throws MqttException {
        MqttMessageType msgType = msg.fixedHeader().messageType();
        if (msgType == MqttMessageType.CONNECT) {
            keepAliveService.registerSession(sessionId, ((MqttConnectMessage) msg).variableHeader().keepAliveTimeSeconds(),
                    () -> {
                        log.warn("[{}] Disconnecting client due to inactivity.", sessionId);
                        disconnectService.disconnect(clientSessionCtx, DisconnectReason.ON_ERROR);
                    });
        } else {
            keepAliveService.acknowledgeControlPacket(sessionId);
        }
    }

    private boolean validOrder(MqttMessageType messageType) {
        if (messageType == MqttMessageType.CONNECT) {
            return !clientSessionCtx.isConnected() && !clientSessionCtx.isCleared();
        } else {
            return clientSessionCtx.isConnected();
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
        disconnectService.disconnect(clientSessionCtx, DisconnectReason.ON_ERROR);
    }

    @Override
    public void operationComplete(Future<? super Void> future) {
        disconnectService.disconnect(clientSessionCtx, DisconnectReason.ON_CHANNEL_CLOSED, false);
    }
}
