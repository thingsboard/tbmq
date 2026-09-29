/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
package org.thingsboard.mqtt;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectPayload;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttConnectVariableHeader;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPubReplyMessageVariableHeader;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttReasonCodes.PubAck;
import io.netty.handler.codec.mqtt.MqttReasonCodes.PubComp;
import io.netty.handler.codec.mqtt.MqttReasonCodes.PubRec;
import io.netty.handler.codec.mqtt.MqttReasonCodes.PubRel;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttUnsubAckMessage;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;
import org.thingsboard.mqtt.MqttOrderedAcknowledgementCtx.MqttMsgWrapper;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
final class MqttChannelHandler extends SimpleChannelInboundHandler<MqttMessage> {

    private final AtomicLong publishMsgCount = new AtomicLong(0);

    private final boolean backPressureEnabled;
    private final int highWatermark;
    private final int lowWatermark;

    private final MqttClientImpl client;
    private final Promise<MqttConnectResult> connectFuture;
    private final MqttOrderedAcknowledgementCtx mqttOrderedAcknowledgementCtxQoS1;
    private final MqttOrderedAcknowledgementCtx mqttOrderedAcknowledgementCtxQoS2;

    MqttChannelHandler(MqttClientImpl client, Promise<MqttConnectResult> connectFuture) {
        this.client = client;
        this.connectFuture = connectFuture;
        this.backPressureEnabled = client.getClientConfig().isBackPressureEnabled();
        this.highWatermark = client.getClientConfig().getBackPressureHighWatermark();
        this.lowWatermark = client.getClientConfig().getBackPressureLowWatermark();
        MqttVersion mqttVersion = client.getClientConfig().getProtocolVersion();
        this.mqttOrderedAcknowledgementCtxQoS1 = new MqttOrderedAcknowledgementCtx(client.getClientConfig().getClientId(), mqttVersion, MqttMessageType.PUBACK);
        this.mqttOrderedAcknowledgementCtxQoS2 = new MqttOrderedAcknowledgementCtx(client.getClientConfig().getClientId(), mqttVersion, MqttMessageType.PUBREC);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) {
        if (msg.decoderResult().isSuccess()) {
            switch (msg.fixedHeader().messageType()) {
                case CONNACK:
                    handleConack(ctx.channel(), (MqttConnAckMessage) msg);
                    break;
                case SUBACK:
                    handleSubAck((MqttSubAckMessage) msg);
                    break;
                case PUBLISH:
                    handlePublish(ctx.channel(), (MqttPublishMessage) msg);
                    break;
                case UNSUBACK:
                    handleUnsuback((MqttUnsubAckMessage) msg);
                    break;
                case PUBACK:
                    handlePuback((MqttPubAckMessage) msg);
                    break;
                case PUBREC:
                    handlePubrec(ctx.channel(), msg);
                    break;
                case PUBREL:
                    handlePubrel(ctx.channel(), msg);
                    break;
                case PUBCOMP:
                    handlePubcomp(msg);
                    break;
                case DISCONNECT:
                    handleDisconnect(msg);
                    break;
            }
        } else {
            log.error("[{}] Message decoding failed: {}", client.getClientConfig().getClientId(), msg.decoderResult().cause().getMessage());
            ctx.close();
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.CONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttConnectVariableHeader variableHeader = new MqttConnectVariableHeader(
                this.client.getClientConfig().getProtocolVersion().protocolName(),  // Protocol Name
                this.client.getClientConfig().getProtocolVersion().protocolLevel(), // Protocol Level
                this.client.getClientConfig().getUsername() != null,                // Has Username
                this.client.getClientConfig().getPassword() != null,                // Has Password
                this.client.getClientConfig().getLastWill() != null                 // Will Retain
                        && this.client.getClientConfig().getLastWill().isRetain(),
                this.client.getClientConfig().getLastWill() != null                 // Will QOS
                        ? this.client.getClientConfig().getLastWill().getQos().value()
                        : 0,
                this.client.getClientConfig().getLastWill() != null,                // Has Will
                this.client.getClientConfig().isCleanSession(),                     // Clean Session
                this.client.getClientConfig().getTimeoutSeconds()                   // Timeout
        );
        MqttConnectPayload payload = new MqttConnectPayload(
                this.client.getClientConfig().getClientId(),
                this.client.getClientConfig().getLastWill() != null ? this.client.getClientConfig().getLastWill().getTopic() : null,
                this.client.getClientConfig().getLastWill() != null ? this.client.getClientConfig().getLastWill().getMessage().getBytes(CharsetUtil.UTF_8) : null,
                this.client.getClientConfig().getUsername(),
                this.client.getClientConfig().getPassword() != null ? this.client.getClientConfig().getPassword().getBytes(CharsetUtil.UTF_8) : null
        );
        ctx.channel().writeAndFlush(new MqttConnectMessage(fixedHeader, variableHeader, payload));
    }

    /**
     * Fails the connect future if the channel closes before a CONNACK completed it: a failed TLS handshake with the
     * handshake's cause, anything else - a broker closing the connection, a disconnect() - as a closed channel. Once a
     * CONNACK completed the future this is a no-op. An SslHandler ahead of this handler has failed its handshake by the
     * time this runs, since it does so in its own channelInactive before passing the event on.
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (!this.connectFuture.isDone()) {
            SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
            Throwable handshakeFailure = sslHandler != null ? sslHandler.handshakeFuture().cause() : null;
            this.connectFuture.tryFailure(handshakeFailure != null ? tlsFailureCause(handshakeFailure)
                    : new ChannelClosedException("Channel closed before CONNACK"));
        }
        super.channelInactive(ctx);
    }

    /**
     * The TLS failure to report for a failed handshake. When the peer closes the connection mid-handshake - a TLS
     * client meeting a plain listener, say - netty fails the handshake with a bare ClosedChannelException and carries
     * the SSLHandshakeException saying so only as a suppressed exception; that one is reported instead.
     */
    private static Throwable tlsFailureCause(Throwable handshakeFailure) {
        if (!(handshakeFailure instanceof SSLException)) {
            for (Throwable suppressed : handshakeFailure.getSuppressed()) {
                if (suppressed instanceof SSLException) {
                    return suppressed;
                }
            }
        }
        return handshakeFailure;
    }

    ListenableFuture<Void> invokeHandlerForIncomingPublish(MqttPublishMessage message) {
        String topic = message.variableHeader().topicName();
        ByteBuf payload = message.payload();

        MqttHandler handler = resolveHandler(topic);
        if (handler == null) {
            payload.release();
            return Futures.immediateVoidFuture();
        }

        // never run a handler on the netty event loop
        ListenableFuture<Void> future;
        try {
            future = Futures.submitAsync(() -> adaptFuture(handler.onMessage(message)), client.getHandlerExecutor());
        } catch (RejectedExecutionException e) {
            // submitAsync throws where transformAsync failed the future; keep failing it so the payload is released
            future = Futures.immediateFailedFuture(e);
        }
        // releases the reference MqttPingHandler retained, which is what keeps the payload valid past channelRead0
        future.addListener(payload::release, MoreExecutors.directExecutor());
        return future;
    }

    /**
     * The handler of the first registered subscription whose filter matches {@code topic}, else the client's default
     * handler, which may be {@code null}.
     */
    private MqttHandler resolveHandler(String topic) {
        String[] topicLevels = MqttTopicFilter.split(topic);
        for (MqttSubscription subscription : this.client.getSubscriptions()) {
            if (subscription.matches(topic, topicLevels)) {
                return subscription.getHandler();
            }
        }
        return client.getDefaultHandler();
    }

    private void handleConack(Channel channel, MqttConnAckMessage message) {
        if (log.isTraceEnabled()) {
            log.trace("[{}][{}] Handling CONNACK: {}", client.getClientConfig().getOwnerId(), client.getClientConfig().getClientId(), message);
        }
        switch (message.variableHeader().connectReturnCode()) {
            case CONNECTION_ACCEPTED:
                this.connectFuture.setSuccess(new MqttConnectResult(true, MqttConnectReturnCode.CONNECTION_ACCEPTED, channel.closeFuture()));

                this.client.getPendingSubscriptions().forEach((id, subscription) -> {
                    // claim the write, or on() may write the same SUBSCRIBE too
                    if (subscription.markSent()) {
                        // the claim makes this write the SUBSCRIBE's only first write, so its timer starts here, once
                        // started whatever the write's outcome, as on()'s own write does: a write that fails while the
                        // channel stays open must still be retried, or the future waits for the next close
                        channel.write(subscription.getSubscribeMessage()).addListener((ChannelFutureListener) f ->
                                subscription.startRetransmitTimer(this.client.retransmissionLoop(f.channel()), this.client::sendAndFlushPacket));
                    }
                });

                this.client.getPendingPublishes().forEach((id, publish) -> {
                    // claim the first write, or publish() would write the same message (and consume its reference) again
                    if (!publish.markSent()) return;
                    // publish() lost the claim and skips its write, so this write is the one to complete
                    channel.write(publish.getMessage())
                            .addListener((ChannelFutureListener) f -> this.client.onFirstWriteComplete(publish, f));
                });
                channel.flush();
                if (this.client.isReconnect()) {
                    this.client.onSuccessfulReconnect();
                }
                break;

            case CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD:
            case CONNECTION_REFUSED_IDENTIFIER_REJECTED:
            case CONNECTION_REFUSED_NOT_AUTHORIZED:
            case CONNECTION_REFUSED_SERVER_UNAVAILABLE:
            case CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION:
            default: // every other code refuses too, e.g. the MQTT 5 reason codes
                this.connectFuture.setSuccess(new MqttConnectResult(false, message.variableHeader().connectReturnCode(), channel.closeFuture()));
                channel.close();
                // Don't start reconnecting logic here
                break;
        }
        if (this.client.getCallback() != null) {
            this.client.getCallback().onConnAck(message);
        }
    }

    private void handleSubAck(MqttSubAckMessage message) {
        MqttPendingSubscription pendingSubscription = this.client.getPendingSubscriptions().remove(message.variableHeader().messageId());
        if (pendingSubscription == null) {
            return;
        }
        pendingSubscription.onSubackReceived();
        String topic = pendingSubscription.getTopic();

        // each SUBSCRIBE carries exactly one filter, so its SUBACK carries exactly one code; none is a malformed SUBACK
        List<Integer> codes = message.payload().grantedQoSLevels();
        int code = codes.isEmpty() ? MqttQoS.FAILURE.value() : codes.get(0);
        if (code == MqttQoS.FAILURE.value()) {
            log.debug("[{}][{}] Server refused the subscription to {}", client.getClientConfig().getOwnerId(), client.getClientConfig().getClientId(), topic);
            pendingSubscription.getFuture().tryFailure(new MqttSubscriptionFailedException(
                    codes.isEmpty() ? "SUBACK for topic filter '" + topic + "' carries no return code"
                            : "Server refused the subscription to topic filter '" + topic + "'"));
        } else {
            MqttQoS grantedQos = MqttQoS.valueOf(code);
            this.client.register(new MqttSubscription(topic, pendingSubscription.getHandler()));
            this.client.getServerSubscriptions().put(topic, grantedQos);
            pendingSubscription.getFuture().trySuccess(grantedQos);
        }
        // only after recording a grant: an on() racing this SUBACK that finds the topic no longer pending then finds the grant
        this.client.getPendingSubscribeTopics().remove(topic);
        if (this.client.getCallback() != null) {
            this.client.getCallback().onSubAck(message);
        }
    }

    private void handlePublish(Channel channel, MqttPublishMessage message) {
        if (log.isTraceEnabled()) {
            log.trace("[{}][{}] Handling PUBLISH: {}", client.getClientConfig().getOwnerId(), client.getClientConfig().getClientId(), message);
        }

        MqttQoS qoS = message.fixedHeader().qosLevel();
        checkBackPressure(channel, true);

        switch (qoS) {
            case AT_MOST_ONCE -> {
                var future = invokeHandlerForIncomingPublish(message);
                DonAsynchron.withCallback(future,
                        (_) -> checkBackPressure(channel, false),
                        (t) -> {
                            log.error("Error invoke future for client {} with QoS {}", client.getClientConfig().getClientId(), MqttQoS.AT_MOST_ONCE, t);
                            checkBackPressure(channel, false);
                        }
                );
            }

            case AT_LEAST_ONCE -> {
                final int msgId = message.variableHeader().packetId();
                var msgWrapper = mqttOrderedAcknowledgementCtxQoS1.addMsgId(msgId);

                if (msgWrapper == null) {
                    dropPublish(channel, message);
                    return;
                }

                var future = invokeHandlerForIncomingPublish(message);
                DonAsynchron.withCallback(future,
                        (_) -> {
                            processPubAck(channel, msgWrapper, PubAck.SUCCESS.byteValue());
                            checkBackPressure(channel, false);
                        },
                        (t) -> {
                            log.error("Error invoke future for client {} with QoS {}", client.getClientConfig().getClientId(), MqttQoS.AT_LEAST_ONCE, t);
                            processPubAck(channel, msgWrapper, PubAck.UNSPECIFIED_ERROR.byteValue());
                            checkBackPressure(channel, false);
                        }
                );
            }

            case EXACTLY_ONCE -> {
                final int msgId = message.variableHeader().packetId();

                if (!client.getQos2PendingMsgIds().add(msgId)) {
                    log.debug("Duplicate QoS2 message received for client {} with msgId {}. Skipping processing.", client.getClientConfig().getClientId(), msgId);
                    processPubRec(channel, msgId, PubRec.PACKET_IDENTIFIER_IN_USE.byteValue());
                    dropPublish(channel, message);
                    return;
                }

                var msgWrapper = mqttOrderedAcknowledgementCtxQoS2.addMsgId(msgId);
                if (msgWrapper == null) {
                    dropPublish(channel, message);
                    return;
                }
                var future = invokeHandlerForIncomingPublish(message);
                DonAsynchron.withCallback(future,
                        (_) -> {
                            processPubRec(channel, msgWrapper, PubRec.SUCCESS.byteValue());
                            checkBackPressure(channel, false);
                        },
                        (t) -> {
                            log.error("Error invoke future for client {} with QoS {}", client.getClientConfig().getClientId(), MqttQoS.EXACTLY_ONCE, t);
                            processPubRec(channel, msgWrapper, PubRec.UNSPECIFIED_ERROR.byteValue());
                            client.getQos2PendingMsgIds().remove(msgId);
                            checkBackPressure(channel, false);
                        }
                );
            }
        }
    }

    /**
     * Drops an inbound PUBLISH that no handler will see: releases the reference MqttPingHandler retained, which
     * {@link #invokeHandlerForIncomingPublish} releases for one a handler does see, and undoes its backpressure count.
     */
    private void dropPublish(Channel channel, MqttPublishMessage message) {
        message.release();
        checkBackPressure(channel, false);
    }

    private void processPubAck(Channel channel, MqttMsgWrapper msgWrapper, byte reasonCode) {
        processPubAck(channel, msgWrapper, reasonCode, true);
    }

    private void processPubAck(Channel channel, MqttMsgWrapper msgWrapper, byte reasonCode, boolean sendAck) {
        this.mqttOrderedAcknowledgementCtxQoS1.ack(channel, msgWrapper, reasonCode, sendAck);
    }

    private void processPubRec(Channel channel, MqttMsgWrapper msgWrapper, byte reasonCode) {
        processPubRec(channel, msgWrapper, reasonCode, true);
    }

    private void processPubRec(Channel channel, MqttMsgWrapper msgWrapper, byte reasonCode, boolean sendAck) {
        this.mqttOrderedAcknowledgementCtxQoS2.ack(channel, msgWrapper, reasonCode, sendAck);
    }

    private void handleUnsuback(MqttUnsubAckMessage message) {
        // remove first and complete only what was removed here, so a concurrent close or max-retransmission cannot also
        // complete it
        MqttPendingUnsubscription unsubscription = this.client.getPendingServerUnsubscribes().remove(message.variableHeader().messageId());
        if (unsubscription == null) {
            return;
        }
        unsubscription.onUnsubackReceived();
        this.client.getServerSubscriptions().remove(unsubscription.getTopic());
        unsubscription.getFuture().trySuccess(null);
        if (this.client.getCallback() != null) {
            this.client.getCallback().onUnsubAck(message);
        }
    }

    private void handlePuback(MqttPubAckMessage message) {
        // remove first and act outside any map operation: completing the future runs its listeners, which may publish
        // and so update the pending publishes, which ConcurrentHashMap forbids from inside a computation on it
        MqttPendingPublish pendingPublish = this.client.getPendingPublishes().remove(message.variableHeader().messageId());
        if (pendingPublish == null) {
            return;
        }
        pendingPublish.onPubackReceived();
        pendingPublish.getPayload().release();
        pendingPublish.getFuture().setSuccess(null);
        if (this.client.getCallback() != null) {
            this.client.getCallback().onPubAck(message);
        }
    }

    private void handlePubrec(Channel channel, MqttMessage message) {
        final int msgId = ((MqttMessageIdVariableHeader) message.variableHeader()).messageId();
        MqttPendingPublish pendingPublish = this.client.getPendingPublishes().get(msgId);
        if (pendingPublish == null) {
            // no publish is pending for it - its retransmissions ran out before this PUBREC arrived, say. Release it
            // all the same, or the server keeps the packet id in use; there is nothing left to retransmit or complete
            log.debug("[{}][{}] PUBREC for unknown packet id {}", client.getClientConfig().getOwnerId(), client.getClientConfig().getClientId(), msgId);
            processPubRel(channel, msgId, PubRel.PACKET_IDENTIFIER_NOT_FOUND.byteValue());
            return;
        }
        pendingPublish.onPubackReceived();

        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_LEAST_ONCE, false, 0);
        MqttMessageIdVariableHeader variableHeader = (MqttMessageIdVariableHeader) message.variableHeader();
        MqttMessage pubrelMessage = new MqttMessage(fixedHeader, variableHeader);
        channel.writeAndFlush(pubrelMessage);

        pendingPublish.setPubrelMessage(pubrelMessage);
        pendingPublish.startPubrelRetransmissionTimer(this.client.retransmissionLoop(channel), this.client::sendAndFlushPacket);
    }

    private void processPubRec(Channel channel, int msgId, byte reasonCodeValue) {
        sendMqttReply(channel, MqttMessageType.PUBREC, msgId, reasonCodeValue);
    }

    private void processPubRel(Channel channel, int msgId, byte reasonCodeValue) {
        sendMqttReply(channel, MqttMessageType.PUBREL, msgId, reasonCodeValue);
    }

    private void handlePubrel(Channel channel, MqttMessage message) {
        log.trace("[{}][{}] Handling PUBREL: {}", client.getClientConfig().getOwnerId(), client.getClientConfig().getClientId(), message);
        final int msgId = ((MqttMessageIdVariableHeader) message.variableHeader()).messageId();
        byte reasonCode = this.client.getQos2PendingMsgIds().remove(msgId)
                ? PubComp.SUCCESS.byteValue()
                : PubComp.PACKET_IDENTIFIER_NOT_FOUND.byteValue();
        processPubComp(channel, msgId, reasonCode);
    }

    private void processPubComp(Channel channel, int msgId, byte reasonCodeValue) {
        sendMqttReply(channel, MqttMessageType.PUBCOMP, msgId, reasonCodeValue);
    }

    private void sendMqttReply(Channel channel, MqttMessageType type, int msgId, byte reasonCodeValue) {
        // a PUBREL's fixed header flags are 0010, the other replies' 0000
        MqttQoS qos = type == MqttMessageType.PUBREL ? MqttQoS.AT_LEAST_ONCE : MqttQoS.AT_MOST_ONCE;
        MqttFixedHeader fixedHeader = new MqttFixedHeader(type, false, qos, false, 0);
        MqttMessage message = MqttVersion.MQTT_5.equals(client.getClientConfig().getProtocolVersion())
                ? new MqttMessage(fixedHeader, new MqttPubReplyMessageVariableHeader(msgId, reasonCodeValue, null))
                : new MqttMessage(fixedHeader, MqttMessageIdVariableHeader.from(msgId));
        channel.writeAndFlush(message);
    }

    private void handlePubcomp(MqttMessage message) {
        MqttMessageIdVariableHeader variableHeader = (MqttMessageIdVariableHeader) message.variableHeader();
        // remove first and release only what was removed here, so a concurrent close or max-retransmission cannot also release it
        MqttPendingPublish pendingPublish = this.client.getPendingPublishes().remove(variableHeader.messageId());
        if (pendingPublish == null) {
            return;
        }
        pendingPublish.getFuture().setSuccess(null);
        pendingPublish.getPayload().release();
        pendingPublish.onPubcompReceived();
    }

    private void handleDisconnect(MqttMessage message) {
        if (this.client.getCallback() != null) {
            this.client.getCallback().onDisconnect(message);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        try {
            if (cause instanceof IOException) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] IOException: ", client.getClientConfig().getOwnerId(), cause);
                } else {
                    log.info("[{}] IOException: {}", client.getClientConfig().getOwnerId(), cause.getMessage());
                }
            } else {
                log.warn("[{}] exceptionCaught", client.getClientConfig().getOwnerId(), cause);
            }
        } finally {
            ReferenceCountUtil.release(cause);
        }
    }

    private ListenableFuture<Void> adaptFuture(Future<Void> future) {
        if (future instanceof ListenableFuture<Void> lf) {
            return lf;
        }
        if (future instanceof CompletableFuture<Void> cf) {
            SettableFuture<Void> settable = SettableFuture.create();
            cf.whenComplete((result, error) -> {
                if (error != null) {
                    settable.setException(error);
                } else {
                    settable.set(result);
                }
            });
            return settable;
        }
        return JdkFutureAdapters.listenInPoolThread(future, client.getHandlerExecutor());
    }

    private void checkBackPressure(Channel channel, boolean increment) {
        if (!backPressureEnabled) {
            return;
        }
        long count = increment ? publishMsgCount.incrementAndGet() : publishMsgCount.decrementAndGet();
        if (increment && count >= highWatermark && channel.config().isAutoRead()) {
            channel.config().setAutoRead(false);
            log.debug("Paused MQTT reads: queue {} >= {}", count, highWatermark);
        } else if (!increment && count < lowWatermark && !channel.config().isAutoRead()) {
            channel.config().setAutoRead(true);
            log.debug("Resumed MQTT reads: queue {} < {}", count, lowWatermark);
        }
    }

}
