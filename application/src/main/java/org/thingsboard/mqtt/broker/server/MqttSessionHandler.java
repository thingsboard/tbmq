/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPubReplyMessageVariableHeader;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttReasonCodeAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.handler.ssl.NotSslRecordException;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.actors.client.messages.EnhancedAuthInitMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.SessionInitMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPublishMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttSubscribeMsg;
import org.thingsboard.mqtt.broker.adaptor.NettyMqttConverter;
import org.thingsboard.mqtt.broker.common.data.StringUtils;
import org.thingsboard.mqtt.broker.common.data.client.credentials.ScramAlgorithm;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.exception.ProtocolViolationException;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.limits.RateLimitBatchProcessor;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;
import org.thingsboard.mqtt.broker.session.SessionContext;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.UUID;

@Slf4j
public class MqttSessionHandler extends ChannelInboundHandlerAdapter implements GenericFutureListener<Future<? super Void>>, SessionContext {

    public static final AttributeKey<InetSocketAddress> ADDRESS = AttributeKey.newInstance("SRC_ADDRESS");

    private final ClientMqttActorManager clientMqttActorManager;
    private final ClientLogger clientLogger;
    private final RateLimitService rateLimitService;
    private final MqttMessageGenerator mqttMessageGenerator;
    private final RateLimitBatchProcessor rateLimitBatchProcessor;
    private final TbMessageStatsReportClient tbMessageStatsReportClient;
    private final ClientSessionCtx clientSessionCtx;
    @Getter
    private final UUID sessionId = UUID.randomUUID();

    private String clientId;
    private InetSocketAddress address;

    public MqttSessionHandler(MqttHandlerCtx mqttHandlerCtx, SslHandler sslHandler, String initializerName) {
        this.clientMqttActorManager = mqttHandlerCtx.getActorManager();
        this.clientLogger = mqttHandlerCtx.getClientLogger();
        this.rateLimitService = mqttHandlerCtx.getRateLimitService();
        this.mqttMessageGenerator = mqttHandlerCtx.getMqttMessageGenerator();
        this.rateLimitBatchProcessor = mqttHandlerCtx.getRateLimitBatchProcessor();
        this.tbMessageStatsReportClient = mqttHandlerCtx.getTbMessageStatsReportClient();
        this.clientSessionCtx = new ClientSessionCtx(mqttHandlerCtx, sessionId, sslHandler, initializerName);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (address == null) {
            address = getAddress(ctx);
            clientSessionCtx.setAddress(address);
        }
        if (log.isTraceEnabled()) {
            log.trace("[{}][{}][{}] Processing msg: {}", address, clientId, sessionId, msg);
        }
        clientSessionCtx.setChannel(ctx);
        try {
            if (!(msg instanceof MqttMessage message)) {
                log.warn("[{}][{}] Received unknown message", clientId, sessionId);
                disconnect(new DisconnectReason(DisconnectReasonType.ON_PROTOCOL_ERROR, "Received unknown message"));
                return;
            }

            if (!message.decoderResult().isSuccess()) {
                log.warn("[{}][{}] Message decoding failed: {}", clientId, sessionId, message.decoderResult().cause().getMessage());
                if (message.decoderResult().cause() instanceof TooLongFrameException) {
                    disconnect(new DisconnectReason(DisconnectReasonType.ON_PACKET_TOO_LARGE));
                } else {
                    disconnect(new DisconnectReason(DisconnectReasonType.ON_MALFORMED_PACKET, "Message decoding failed"));
                }
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
        if (StringUtils.isEmpty(clientId)) {
            if (msgType != MqttMessageType.CONNECT) {
                throw new ProtocolViolationException("Received " + msgType + " while session wasn't initialized");
            }
            var connectMessage = (MqttConnectMessage) msg;
            MqttProperties properties = connectMessage.variableHeader().properties();
            String authMethod = MqttPropertiesUtil.getAuthenticationMethodValue(properties);
            if (authMethod == null) {
                initSession(connectMessage);
            } else {
                var scramAlgorithmOpt = ScramAlgorithm.fromMqttName(authMethod);
                if (scramAlgorithmOpt.isEmpty()) {
                    if (log.isDebugEnabled()) {
                        log.debug("[{}][{}] Unsupported authentication method: {}!", address, sessionId, authMethod);
                    }
                    connAckAndCloseCtx(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_AUTHENTICATION_METHOD);
                    return;
                }
                byte[] authData = MqttPropertiesUtil.getAuthenticationDataValue(properties);
                if (authData == null) {
                    if (log.isDebugEnabled()) {
                        log.debug("[{}][{}] No authentication data found!", address, sessionId);
                    }
                    connAckAndCloseCtx(MqttConnectReturnCode.CONNECTION_REFUSED_UNSPECIFIED_ERROR);
                    return;
                }
                initEnhancedAuth(connectMessage, authMethod, authData);
            }
        }

        clientLogger.logEvent(clientId, this.getClass(), "Received msg " + msgType);
        switch (msgType) {
            case DISCONNECT:
                reportTraffic(BrokerConstants.TLS_DISCONNECT_BYTES_OVERHEAD);
                disconnect(NettyMqttConverter.createMqttDisconnectMsg(clientSessionCtx, msg));
                break;
            case CONNECT:
                if (clientSessionCtx.isDefaultAuth()) {
                    reportTraffic(BrokerConstants.TLS_CONNECT_BYTES_OVERHEAD);
                    clientMqttActorManager.connect(clientId, NettyMqttConverter.createMqttConnectMsg(sessionId, (MqttConnectMessage) msg));
                }
                break;
            case SUBSCRIBE:
                MqttSubscribeMsg mqttSubscribeMsg = NettyMqttConverter.createMqttSubscribeMsg(sessionId, (MqttSubscribeMessage) msg);
                if (mqttSubscribeMsg == null) {
                    disconnect(new DisconnectReason(DisconnectReasonType.ON_PROTOCOL_ERROR, BrokerConstants.SUBSCRIPTION_ID_IS_0_ERROR_MSG));
                    return;
                }
                clientMqttActorManager.processMqttMsg(clientId, mqttSubscribeMsg);
                break;
            case UNSUBSCRIBE:
                clientMqttActorManager.processMqttMsg(clientId, NettyMqttConverter.createMqttUnsubscribeMsg(sessionId, (MqttUnsubscribeMessage) msg));
                break;
            case PUBLISH:
                processPublish(msg);
                break;
            case PUBACK:
                clientMqttActorManager.processMqttMsg(clientId, NettyMqttConverter.createMqttPubAckMsg(sessionId, (MqttPubReplyMessageVariableHeader) msg.variableHeader()));
                break;
            case PUBREC:
                clientMqttActorManager.processMqttMsg(clientId, NettyMqttConverter.createMqttPubRecMsg(sessionId, (MqttPubReplyMessageVariableHeader) msg.variableHeader()));
                break;
            case PUBREL:
                clientMqttActorManager.processMqttMsg(clientId, NettyMqttConverter.createMqttPubRelMsg(sessionId, (MqttPubReplyMessageVariableHeader) msg.variableHeader()));
                break;
            case PUBCOMP:
                clientMqttActorManager.processMqttMsg(clientId, NettyMqttConverter.createMqttPubCompMsg(sessionId, (MqttPubReplyMessageVariableHeader) msg.variableHeader()));
                break;
            case PINGREQ:
                clientMqttActorManager.processMqttMsg(clientId, NettyMqttConverter.createMqttPingMsg(sessionId));
                break;
            case AUTH:
                clientMqttActorManager.processMqttMsg(clientId,
                        NettyMqttConverter.createMqttAuthMsg(sessionId, (MqttReasonCodeAndPropertiesVariableHeader) msg.variableHeader()));
        }
    }

    private void connAckAndCloseCtx(MqttConnectReturnCode reasonCode) {
        var mqttConnAckMessage = MqttMessageBuilders.connAck().returnCode(reasonCode).build();
        clientSessionCtx.getChannel().writeAndFlush(mqttConnAckMessage);
        clientSessionCtx.getChannel().close();
    }

    private void processPublish(MqttMessage msg) {
        if (checkClientLimits(msg)) {
            MqttPublishMsg mqttPublishMsg = NettyMqttConverter.createMqttPublishMsg(sessionId, (MqttPublishMessage) msg);
            if (rateLimitService.isTotalMsgsLimitEnabled()) {
                rateLimitBatchProcessor.addMessage(mqttPublishMsg,
                        mqttMsg -> clientMqttActorManager.processMqttMsg(clientId, mqttMsg),
                        mqttMsg -> {
                            processMsgOnRateLimits(mqttMsg.getPublishMsg().getPacketId(), mqttMsg.getPublishMsg().getQosLevel(), "Total rate limits detected");
                            mqttMsg.release();
                        });
                return;
            }
            clientMqttActorManager.processMqttMsg(clientId, mqttPublishMsg);
        } else {
            MqttPublishMessage mqttPublishMessage = (MqttPublishMessage) msg;
            processMsgOnRateLimits(mqttPublishMessage.variableHeader().packetId(), mqttPublishMessage.fixedHeader().qosLevel().value(), "Client incoming messages rate limits detected");
        }
    }

    private void processMsgOnRateLimits(int packetId, int qos, String message) {
        if (MqttVersion.MQTT_5.equals(clientSessionCtx.getMqttVersion())) {
            replyWithAck(packetId, qos);
        } else {
            disconnect(new DisconnectReason(DisconnectReasonType.ON_RATE_LIMITS, message));
        }
    }

    private void replyWithAck(int packetId, int qos) {
        if (MqttQoS.AT_LEAST_ONCE.value() == qos) {
            clientSessionCtx.getChannel().writeAndFlush(mqttMessageGenerator.createPubAckMsg(packetId, MqttReasonCodes.PubAck.QUOTA_EXCEEDED));
        } else if (MqttQoS.EXACTLY_ONCE.value() == qos) {
            clientSessionCtx.getChannel().writeAndFlush(mqttMessageGenerator.createPubRecMsg(packetId, MqttReasonCodes.PubRec.QUOTA_EXCEEDED));
        }
    }

    private boolean checkClientLimits(MqttMessage msg) {
        return rateLimitService.checkIncomingLimits(clientId, sessionId, msg);
    }

    private void initSession(MqttConnectMessage connectMessage) {
        boolean generated = getClientIdOrElseGenerate(connectMessage);
        clientSessionCtx.setMqttVersion(getMqttVersion(connectMessage));
        clientMqttActorManager.initSession(clientId, generated, new SessionInitMsg(
                clientSessionCtx,
                connectMessage.payload().userName(),
                connectMessage.payload().passwordInBytes()));
    }

    private void initEnhancedAuth(MqttConnectMessage connectMessage, String authMethod, byte[] authData) {
        boolean generated = getClientIdOrElseGenerate(connectMessage);
        clientSessionCtx.setMqttVersion(getMqttVersion(connectMessage));
        clientSessionCtx.setConnectMsgFromEnhancedAuth(connectMessage);
        clientSessionCtx.setAuthMethod(authMethod);
        clientMqttActorManager.initEnhancedAuth(clientId, generated, new EnhancedAuthInitMsg(
                clientSessionCtx, authMethod, authData));
    }

    private boolean getClientIdOrElseGenerate(MqttConnectMessage connectMessage) {
        clientId = connectMessage.payload().clientIdentifier();
        boolean generated = StringUtils.isEmpty(clientId);
        clientId = generated ? generateClientId() : clientId;
        return generated;
    }

    private String generateClientId() {
        return UUID.randomUUID().toString().replaceAll("-", BrokerConstants.EMPTY_STR);
    }

    private MqttVersion getMqttVersion(MqttConnectMessage connectMessage) {
        var version = (byte) connectMessage.variableHeader().version();
        var protocolName = version > 3 ? BrokerConstants.MQTT_PROTOCOL_NAME : BrokerConstants.MQTT_V_3_1_PROTOCOL_NAME;
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
            if (log.isDebugEnabled()) {
                log.debug("[{}] Session wasn't initialized yet, closing channel. Reason - {}.", sessionId, reason);
            }
            try {
                clientSessionCtx.closeChannel();
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Failed to close channel.", sessionId, e);
                }
            }
        } else {
            disconnect(new MqttDisconnectMsg(sessionId, reason));
        }
    }

    void disconnect(MqttDisconnectMsg disconnectMsg) {
        clientMqttActorManager.disconnect(clientId, disconnectMsg);
    }

    InetSocketAddress getAddress(ChannelHandlerContext ctx) {
        var address = ctx.channel().attr(ADDRESS).get();
        if (address == null) {
            if (log.isTraceEnabled()) {
                log.trace("[{}] Received empty address.", ctx.channel().id());
            }
            InetSocketAddress remoteAddress = (InetSocketAddress) ctx.channel().remoteAddress();
            if (log.isTraceEnabled()) {
                log.trace("[{}] Going to use address: {}", ctx.channel().id(), remoteAddress);
            }
            return remoteAddress;
        } else {
            if (log.isTraceEnabled()) {
                log.trace("[{}] Received address: {}", ctx.channel().id(), address);
            }
        }
        return address;
    }

    private void reportTraffic(int bytes) {
        if (clientSessionCtx.getSslHandler() != null) {
            tbMessageStatsReportClient.reportTraffic(bytes);
        }
    }
}
