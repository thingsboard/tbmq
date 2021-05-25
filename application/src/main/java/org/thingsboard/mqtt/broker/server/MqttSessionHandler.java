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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.exception.ProtocolViolationException;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;
import org.thingsboard.mqtt.broker.session.SessionContext;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.util.UUID;

@Slf4j
public class MqttSessionHandler extends ChannelInboundHandlerAdapter implements GenericFutureListener<Future<? super Void>>, SessionContext {

    private final ClientMqttActorManager clientMqttActorManager;

    @Getter
    private final UUID sessionId = UUID.randomUUID();
    private String clientId;
    private final ClientSessionCtx clientSessionCtx ;

    public MqttSessionHandler(ClientMqttActorManager clientMqttActorManager, SslHandler sslHandler) {
        this.clientMqttActorManager = clientMqttActorManager;
        this.clientSessionCtx = new ClientSessionCtx(sessionId, sslHandler);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        log.trace("[{}] Processing msg: {}", sessionId, msg);
        clientSessionCtx.setChannel(ctx);
        try {
            if (msg instanceof MqttMessage) {
                MqttMessage message = (MqttMessage) msg;
                if (message.decoderResult().isSuccess()) {
                    processMqttMsg(message);
                } else {
                    log.warn("[{}] Message decoding failed: {}", sessionId, message.decoderResult().cause().getMessage());
                    disconnect(new DisconnectReason(DisconnectReasonType.ON_ERROR, "Message decoding failed"));
                }
            } else {
                log.warn("[{}] Received unknown message", sessionId);
                disconnect(new DisconnectReason(DisconnectReasonType.ON_ERROR, "Received unknown message"));
            }
        } catch (Exception e) {
            ReferenceCountUtil.safeRelease(msg);
            throw e;
        }
    }

    private void processMqttMsg(MqttMessage msg) {
        if (msg.fixedHeader() == null) {
            throw new ProtocolViolationException("Invalid message received");
        }

        MqttMessageType msgType = msg.fixedHeader().messageType();
        if (StringUtils.isEmpty(clientId)) {
            if (msgType != MqttMessageType.CONNECT) {
                throw new ProtocolViolationException("First received message was not CONNECT");
            }
            clientId = ((MqttConnectMessage) msg).payload().clientIdentifier();
            boolean isClientIdGenerated = StringUtils.isEmpty(clientId);
            clientId = isClientIdGenerated ? UUID.randomUUID().toString() : clientId;
            clientMqttActorManager.initSession(clientId, isClientIdGenerated, clientSessionCtx);
        }

        clientMqttActorManager.processMqttMsg(clientId, sessionId, msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // TODO push msg to the client before closing
        String exceptionMessage = null;
        if (cause.getCause() instanceof SSLHandshakeException) {
            log.warn("[{}] Exception on SSL handshake. Reason - {}", sessionId, cause.getCause().getMessage());
            exceptionMessage = cause.getCause().getMessage();
        } else if (cause.getCause() instanceof NotSslRecordException) {
            log.warn("[{}] NotSslRecordException: {}", sessionId, cause.getCause().getMessage());
            exceptionMessage = cause.getCause().getMessage();
        } else if (cause instanceof IOException) {
            log.warn("[{}] IOException: {}", sessionId, cause.getMessage());
            exceptionMessage = cause.getMessage();
        } else if (cause instanceof ProtocolViolationException) {
            log.warn("[{}] ProtocolViolationException: {}", sessionId, cause.getMessage());
            exceptionMessage = cause.getMessage();
        } else {
            log.error("[{}] Unexpected Exception", sessionId, cause);
            exceptionMessage = cause.getMessage();
        }
        disconnect(new DisconnectReason(DisconnectReasonType.ON_ERROR, exceptionMessage));
    }

    @Override
    public void operationComplete(Future<? super Void> future) {
        if (clientId != null) {
            clientMqttActorManager.disconnect(clientId, sessionId, new DisconnectReason(DisconnectReasonType.ON_CHANNEL_CLOSED));
        }
    }

    void disconnect(DisconnectReason reason) {
        if (clientId == null) {
            log.debug("[{}] Session wasn't initialized yet, closing channel. Reason - {}.", sessionId, reason);
            try {
                clientSessionCtx.getChannel().close();
            } catch (Exception e) {
                log.debug("[{}] Failed to close channel. Reason - {}.", sessionId, e.getMessage());
            }
        } else {
            clientMqttActorManager.disconnect(clientId, sessionId, reason);
        }
    }
}
