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
import io.netty.handler.ssl.NotSslRecordException;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.mqtt.client.connect.ConnectService;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectService;
import org.thingsboard.mqtt.broker.service.mqtt.handlers.MqttMessageHandler;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.SessionState;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.UUID;

@Slf4j
public class MqttSessionHandler extends ChannelInboundHandlerAdapter implements GenericFutureListener<Future<? super Void>> {

    private final MqttMessageHandler messageHandler;
    private final ConnectService connectService;
    private final DisconnectService disconnectService;

    private final UUID sessionId = UUID.randomUUID();
    private final ClientSessionCtx clientSessionCtx ;

    public MqttSessionHandler(MqttMessageHandler messageHandler, ConnectService connectService, DisconnectService disconnectService, SslHandler sslHandler) {
        this.messageHandler = messageHandler;
        this.connectService = connectService;
        this.disconnectService = disconnectService;
        this.clientSessionCtx = new ClientSessionCtx(sessionId, sslHandler);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        log.trace("[{}] Processing msg: {}", sessionId, msg);
        clientSessionCtx.setChannel(ctx);
        boolean releaseMessage = true;
        try {
            if (msg instanceof MqttMessage) {
                MqttMessage message = (MqttMessage) msg;
                if (message.decoderResult().isSuccess()) {
                    releaseMessage = processMqttMsg(ctx, message);
                } else {
                    log.warn("[{}] Message decoding failed: {}", sessionId, message.decoderResult().cause().getMessage());
                    disconnectService.disconnect(clientSessionCtx, DisconnectReason.ON_ERROR);
                }
            } else {
                log.warn("[{}] Received unknown message", sessionId);
                disconnectService.disconnect(clientSessionCtx, DisconnectReason.ON_ERROR);
            }
        } finally {
            if (releaseMessage) {
                ReferenceCountUtil.safeRelease(msg);
            }
        }
    }

    private boolean processMqttMsg(ChannelHandlerContext ctx, MqttMessage msg) {
        InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
        if (msg.fixedHeader() == null) {
            log.warn("[{}][{}:{}] Invalid message received", sessionId, address.getHostName(), address.getPort());
            disconnectService.disconnect(clientSessionCtx, DisconnectReason.ON_ERROR);
            return true;
        }
        MqttMessageType msgType = msg.fixedHeader().messageType();
        if (wrongOrder(msgType)) {
            log.warn("[{}] Closing current session due to invalid msg order: {}", sessionId, msg);
            disconnectService.disconnect(clientSessionCtx, DisconnectReason.ON_ERROR);
            return true;
        }
        clientSessionCtx.getConnectionLock().lock();
        try {
            if (wrongOrder(msgType)) {
                log.warn("[{}] Closing current session due to invalid msg order: {}", sessionId, msg);
                disconnectService.disconnect(clientSessionCtx, DisconnectReason.ON_ERROR);
                return true;
            }
            if (msgType == MqttMessageType.CONNECT) {
                connectService.connect(clientSessionCtx, (MqttConnectMessage) msg);
            } else if (msgType == MqttMessageType.DISCONNECT) {
                disconnectService.disconnect(clientSessionCtx, DisconnectReason.ON_DISCONNECT_MSG);
            } else if (clientSessionCtx.getSessionState() == SessionState.CONNECTED) {
                if (clientSessionCtx.getUnprocessedMessagesQueue().isProcessing()) {
                    clientSessionCtx.getUnprocessedMessagesQueue().queueMessage(msg);
                    return false;
                } else {
                    messageHandler.process(clientSessionCtx, msg);
                }
            } else {
                throw new RuntimeException("Wrong session state!");
            }
        } catch (MqttException e) {
            log.debug("[{}] Failed to process {} msg. Reason - {}.",
                    sessionId, msgType, e.getMessage());
            disconnectService.disconnect(clientSessionCtx, DisconnectReason.ON_ERROR);
        } finally {
            clientSessionCtx.getConnectionLock().unlock();
        }
        return true;
    }

    private boolean wrongOrder(MqttMessageType messageType) {
        SessionState sessionState = clientSessionCtx.getSessionState();
        return sessionState == SessionState.DISCONNECTED
                || (messageType == MqttMessageType.CONNECT && sessionState != SessionState.CREATED)
                || (messageType != MqttMessageType.CONNECT && sessionState == SessionState.CREATED);
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
        } else if (cause instanceof IOException) {
            log.warn("[{}] IOException: {}", sessionId, cause.getMessage());
        } else {
            log.error("[{}] Unexpected Exception", sessionId, cause);
        }
        disconnectService.disconnect(clientSessionCtx, DisconnectReason.ON_ERROR);
    }

    @Override
    public void operationComplete(Future<? super Void> future) {
        disconnectService.disconnect(clientSessionCtx, DisconnectReason.ON_CHANNEL_CLOSED, false);
    }
}
