/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.handler.ssl.NotSslRecordException;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.actors.client.messages.DisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.SessionInitMsg;
import org.thingsboard.mqtt.broker.adaptor.NettyMqttConverter;
import org.thingsboard.mqtt.broker.exception.ProtocolViolationException;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
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
    private final ClientLogger clientLogger;
    private final RateLimitService rateLimitService;
    private final ClientSessionCtx clientSessionCtx;
    @Getter
    private final UUID sessionId = UUID.randomUUID();

    private String clientId;

    public MqttSessionHandler(ClientMqttActorManager clientMqttActorManager, ClientLogger clientLogger,
                              RateLimitService rateLimitService, SslHandler sslHandler, int maxInFlightMsgs) {
        this.clientMqttActorManager = clientMqttActorManager;
        this.clientLogger = clientLogger;
        this.rateLimitService = rateLimitService;
        this.clientSessionCtx = new ClientSessionCtx(sessionId, sslHandler, maxInFlightMsgs);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        log.trace("[{}][{}] Processing msg: {}", clientId, sessionId, msg);
        clientSessionCtx.setChannel(ctx);
        try {
            if (!(msg instanceof MqttMessage)) {
                log.warn("[{}][{}] Received unknown message", clientId, sessionId);
                disconnect(new DisconnectReason(DisconnectReasonType.ON_ERROR, "Received unknown message"));
                return;
            }

            MqttMessage message = (MqttMessage) msg;
            if (!message.decoderResult().isSuccess()) {
                log.warn("[{}][{}] Message decoding failed: {}", clientId, sessionId, message.decoderResult().cause().getMessage());
                disconnect(new DisconnectReason(DisconnectReasonType.ON_ERROR, "Message decoding failed"));
                return;
            }

            processMqttMsg(message);
        } finally {
            ReferenceCountUtil.safeRelease(msg);
        }
    }

    private void processMqttMsg(MqttMessage msg) {
        if (msg.fixedHeader() == null) {
            throw new ProtocolViolationException("Invalid message received");
        }

        MqttMessageType msgType = msg.fixedHeader().messageType();
        if (StringUtils.isEmpty(clientId) && msgType == MqttMessageType.CONNECT) {
            initSession((MqttConnectMessage) msg);
        }

        if (StringUtils.isEmpty(clientId)) {
            throw new ProtocolViolationException("Received " + msgType + " while session wasn't initialized");
        }

        clientLogger.logEvent(clientId, this.getClass(), "Received msg " + msgType);
        switch (msgType) {
            case DISCONNECT:
                disconnect(new DisconnectReason(DisconnectReasonType.ON_DISCONNECT_MSG));
                break;
            case CONNECT:
                clientMqttActorManager.connect(clientId, NettyMqttConverter.createMqttConnectMsg(sessionId, (MqttConnectMessage) msg));
                break;
            case SUBSCRIBE:
                clientMqttActorManager.processMqttMsg(clientId, NettyMqttConverter.createMqttSubscribeMsg(sessionId, (MqttSubscribeMessage) msg));
                break;
            case UNSUBSCRIBE:
                clientMqttActorManager.processMqttMsg(clientId, NettyMqttConverter.createMqttUnsubscribeMsg(sessionId, (MqttUnsubscribeMessage) msg));
                break;
            case PUBLISH:
                processPublish(msg);
                break;
            case PUBACK:
                clientMqttActorManager.processMqttMsg(clientId, NettyMqttConverter.createMqttPubAckMsg(sessionId, (MqttPubAckMessage) msg));
                break;
            case PUBREC:
                clientMqttActorManager.processMqttMsg(clientId, NettyMqttConverter.createMqttPubRecMsg(sessionId, (MqttMessageIdVariableHeader) msg.variableHeader()));
                break;
            case PUBREL:
                clientMqttActorManager.processMqttMsg(clientId, NettyMqttConverter.createMqttPubRelMsg(sessionId, (MqttMessageIdVariableHeader) msg.variableHeader()));
                break;
            case PUBCOMP:
                clientMqttActorManager.processMqttMsg(clientId, NettyMqttConverter.createMqttPubCompMsg(sessionId, (MqttMessageIdVariableHeader) msg.variableHeader()));
                break;
            case PINGREQ:
                clientMqttActorManager.processMqttMsg(clientId, NettyMqttConverter.createMqttPingMsg(sessionId));
                break;
        }
    }

    private void processPublish(MqttMessage msg) {
        if (checkLimits(msg)) {
            clientMqttActorManager.processMqttMsg(clientId, NettyMqttConverter.createMqttPublishMsg(sessionId, (MqttPublishMessage) msg));
        } else {
            log.debug("[{}][{}] Disconnecting client on rate limits detection!", clientId, sessionId);
            disconnect(new DisconnectReason(DisconnectReasonType.ON_RATE_LIMITS, "Rate limits detected"));
        }
    }

    private boolean checkLimits(MqttMessage msg) {
        return rateLimitService.checkLimits(clientId, sessionId, msg);
    }

    private void initSession(MqttConnectMessage connectMessage) {
        clientId = connectMessage.payload().clientIdentifier();
        boolean isClientIdGenerated = StringUtils.isEmpty(clientId);
        clientId = isClientIdGenerated ? UUID.randomUUID().toString() : clientId;
        clientSessionCtx.setMqttVersion(getMqttVersion(connectMessage));
        clientMqttActorManager.initSession(clientId, isClientIdGenerated, new SessionInitMsg(
                clientSessionCtx,
                connectMessage.payload().userName(),
                connectMessage.payload().passwordInBytes()));
    }

    private MqttVersion getMqttVersion(MqttConnectMessage connectMessage) {
        var version = (byte) connectMessage.variableHeader().version();
        var protocolName = version > 3 ? "MQTT" : "MQIsdp";
        return MqttVersion.fromProtocolNameAndLevel(protocolName, version);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String exceptionMessage;
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
            disconnect(new DisconnectReason(DisconnectReasonType.ON_CHANNEL_CLOSED));
        }
    }

    void disconnect(DisconnectReason reason) {
        if (clientId == null) {
            log.debug("[{}] Session wasn't initialized yet, closing channel. Reason - {}.", sessionId, reason);
            try {
                clientSessionCtx.closeChannel();
            } catch (Exception e) {
                log.debug("[{}] Failed to close channel. Reason - {}.", sessionId, e.getMessage());
            }
        } else {
            clientMqttActorManager.disconnect(clientId, new DisconnectMsg(sessionId, reason));
        }
    }
}
