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
import io.netty.channel.ConnectTimeoutException;
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
import io.netty.handler.codec.mqtt.MqttProperties;
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
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.MqttOrderedAcknowledgementCtx.MqttMsgWrapper;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
final class MqttChannelHandler extends SimpleChannelInboundHandler<MqttMessage> {

    /**
     * Set on a channel whose CONNACK accepted the connection: only such a channel was ever up, so only its close is
     * reported as {@link MqttClientCallback#connectionLost}.
     */
    static final AttributeKey<Boolean> CONNACK_ACCEPTED = AttributeKey.valueOf(MqttChannelHandler.class, "connackAccepted");

    /** The MQTT 5 Session Expiry Interval that never expires: 0xFFFFFFFF, a four-byte unsigned integer. */
    static final int SESSION_NEVER_EXPIRES = 0xFFFFFFFF;

    /**
     * The result of a delivery whose handler had not started when the client was disconnected: it skips the handler and
     * is never acked, so a kept session redelivers it to the next client.
     */
    static final RuntimeException DELIVERY_SKIPPED = new DeliverySkippedException();

    private static final class DeliverySkippedException extends RuntimeException {
        private DeliverySkippedException() {
            super("Delivery skipped: the client was disconnected before its handler started", null, false, false);
        }
    }

    /** An inbound PUBLISH on its way to its handler: the handler's future, and whether the handler was called. */
    record Delivery(ListenableFuture<Void> future, AtomicBoolean handlerCalled) {
    }

    private final AtomicLong publishMsgCount = new AtomicLong(0);

    private final boolean backPressureEnabled;
    private final int highWatermark;
    private final int lowWatermark;

    private final MqttClientImpl client;
    private final Promise<MqttConnectResult> connectFuture;
    private final MqttOrderedAcknowledgementCtx mqttOrderedAcknowledgementCtxQoS1;
    private final MqttOrderedAcknowledgementCtx mqttOrderedAcknowledgementCtxQoS2;
    /**
     * Whether this channel bounds its wait for the CONNACK: only a channel the client connects does, so the rest of
     * its connect timeout runs from channelActive.
     */
    private final boolean connectTimeout;
    /** When the connect timeout ends (System.nanoTime()): set when the channel is built, as its TCP connect starts. */
    private final long connectDeadlineNanos;
    private ScheduledFuture<?> connackTimeout;
    /** Deliveries of this channel whose handler was called and whose ack is not yet written; see {@link #drain}. */
    private final AtomicInteger startedDeliveries = new AtomicInteger();
    private volatile Promise<Void> drained;
    /**
     * Handler failures since the last delivery a handler completed, on this channel: a handler forwarding to a store
     * that is down fails every message, so only the first of a run is logged as a warning; see {@link #onHandlerFailed}.
     */
    private final AtomicInteger handlerFailureRun = new AtomicInteger();

    MqttChannelHandler(MqttClientImpl client, Promise<MqttConnectResult> connectFuture) {
        this(client, connectFuture, false);
    }

    MqttChannelHandler(MqttClientImpl client, Promise<MqttConnectResult> connectFuture, boolean connectTimeout) {
        this.client = client;
        this.connectFuture = connectFuture;
        this.backPressureEnabled = client.getClientConfig().isBackPressureEnabled();
        this.highWatermark = client.getClientConfig().getBackPressureHighWatermark();
        this.lowWatermark = client.getClientConfig().getBackPressureLowWatermark();
        MqttVersion mqttVersion = client.getClientConfig().getProtocolVersion();
        this.mqttOrderedAcknowledgementCtxQoS1 = new MqttOrderedAcknowledgementCtx(client.getClientConfig().getClientId(), mqttVersion, MqttMessageType.PUBACK);
        this.mqttOrderedAcknowledgementCtxQoS2 = new MqttOrderedAcknowledgementCtx(client.getClientConfig().getClientId(), mqttVersion, MqttMessageType.PUBREC);
        this.connectTimeout = connectTimeout;
        this.connectDeadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(client.getClientConfig().getConnectTimeoutSec());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) {
        if (msg.decoderResult().isSuccess()) {
            switch (msg.fixedHeader().messageType()) {
                case CONNACK:
                    handleConack(ctx.channel(), (MqttConnAckMessage) msg);
                    break;
                case SUBACK:
                    handleSubAck(ctx.channel(), (MqttSubAckMessage) msg);
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
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof MqttOversizedPublish oversized) {
            handleOversizedPublish(ctx.channel(), oversized);
            return;
        }
        super.channelRead(ctx, msg);
    }

    /**
     * A PUBLISH over the limit, which MqttOversizedPublishGuard skipped: acked the way a failed handler is - 0x80 under
     * MQTT 5, a plain ack under 3.x - in order with the other acks, and reported to
     * {@link MqttClientCallback#onPublishTooLarge}. Under 3.x a QoS 2 one stays in the receive state until its PUBREL, so
     * a resend of it is answered again and not reported twice.
     */
    private void handleOversizedPublish(Channel channel, MqttOversizedPublish publish) {
        log.debug("[{}][{}] Skipped a PUBLISH to '{}' of {} bytes, over the limit of {}", client.getClientConfig().getOwnerId(),
                client.getClientConfig().getClientId(), publish.topic(), publish.remainingLength(), client.getClientConfig().getMaxBytesInMessage());
        switch (publish.qos()) {
            case AT_MOST_ONCE -> reportTooLarge(publish);
            case AT_LEAST_ONCE -> {
                processPubAck(channel, mqttOrderedAcknowledgementCtxQoS1.addMsgId(publish.packetId()), PubAck.UNSPECIFIED_ERROR.byteValue());
                reportTooLarge(publish);
            }
            case EXACTLY_ONCE -> {
                var msgWrapper = mqttOrderedAcknowledgementCtxQoS2.addMsgId(publish.packetId());
                ListenableFuture<Byte> received = client.getQos2Received().get(publish.packetId());
                if (received != null) {
                    received.addListener(() -> processPubRec(channel, msgWrapper, Futures.getUnchecked(received)), MoreExecutors.directExecutor());
                    return;
                }
                byte code = PubRec.UNSPECIFIED_ERROR.byteValue();
                if (!MqttVersion.MQTT_5.equals(client.getClientConfig().getProtocolVersion())) {
                    // under MQTT 5 the failure code ends the exchange; under 3.x a PUBREL follows
                    client.getQos2Received().put(publish.packetId(), Futures.immediateFuture(code));
                }
                processPubRec(channel, msgWrapper, code);
                reportTooLarge(publish);
            }
            default -> {
            }
        }
    }

    private void reportTooLarge(MqttOversizedPublish publish) {
        MqttClientCallback callback = client.getCallback();
        if (callback != null) {
            try {
                callback.onPublishTooLarge(publish.topic(), publish.qos(), publish.remainingLength());
            } catch (Exception e) {
                log.warn("[{}] onPublishTooLarge threw", client.getClientConfig().getOwnerId(), e);
            }
        }
    }

    /**
     * The CONNECT properties. MQTT 5 split Clean Session into Clean Start and a Session Expiry Interval, whose absence
     * means 0: the session ends when the connection closes [MQTT-3.1.2.11.2], so Clean Start 0 alone never finds one to
     * resume. A client that keeps its session asks for it never to expire (0xFFFFFFFF), which is what a 3.x Clean
     * Session of 0 means; the server may lower it in its CONNACK.
     */
    private MqttProperties connectProperties() {
        MqttClientConfig config = this.client.getClientConfig();
        if (config.getProtocolVersion() != MqttVersion.MQTT_5 || config.isCleanSession()) {
            return MqttProperties.NO_PROPERTIES;
        }
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL.value(),
                SESSION_NEVER_EXPIRES));
        return properties;
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
                this.client.getClientConfig().getTimeoutSeconds(),                  // Timeout
                connectProperties()
        );
        MqttConnectPayload payload = new MqttConnectPayload(
                this.client.getClientConfig().getClientId(),
                this.client.getClientConfig().getLastWill() != null ? this.client.getClientConfig().getLastWill().getTopic() : null,
                this.client.getClientConfig().getLastWill() != null ? this.client.getClientConfig().getLastWill().getMessage().getBytes(CharsetUtil.UTF_8) : null,
                this.client.getClientConfig().getUsername(),
                this.client.getClientConfig().getPassword() != null ? this.client.getClientConfig().getPassword().getBytes(CharsetUtil.UTF_8) : null
        );
        ctx.channel().writeAndFlush(new MqttConnectMessage(fixedHeader, variableHeader, payload));
        if (this.connectTimeout) {
            long remainingNanos = Math.max(0L, this.connectDeadlineNanos - System.nanoTime());
            this.connackTimeout = ctx.executor().schedule(() -> {
                SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
                String phase = sslHandler != null && !sslHandler.handshakeFuture().isDone() ? "TLS handshake" : "CONNACK";
                if (this.connectFuture.tryFailure(new ConnectTimeoutException("No " + phase + " within the connect timeout of "
                        + this.client.getClientConfig().getConnectTimeoutSec() + " s"))) {
                    ctx.close();
                }
            }, remainingNanos, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Fails the connect future if the channel closes before a CONNACK completed it: a failed TLS handshake with the
     * handshake's cause, a channel closed because the client was disconnected as "Client is disconnected", anything
     * else - a broker closing the connection - as a closed channel. Once a CONNACK completed the future this is a
     * no-op. An SslHandler ahead of this handler has failed its handshake by the time this runs, since it does so in its
     * own channelInactive before passing the event on.
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cancelConnackTimeout();
        if (!this.connectFuture.isDone()) {
            SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);
            Throwable handshakeFailure = sslHandler != null ? sslHandler.handshakeFuture().cause() : null;
            Throwable cause;
            if (handshakeFailure != null) {
                cause = tlsFailureCause(handshakeFailure);
            } else if (this.client.isDisconnected()) {
                cause = new ChannelClosedException("Client is disconnected");
            } else {
                cause = new ChannelClosedException("Channel closed before CONNACK");
            }
            this.connectFuture.tryFailure(cause);
        }
        super.channelInactive(ctx);
    }

    private void cancelConnackTimeout() {
        if (this.connackTimeout != null) {
            this.connackTimeout.cancel(false);
            this.connackTimeout = null;
        }
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

    Delivery invokeHandlerForIncomingPublish(MqttPublishMessage message) {
        String topic = message.variableHeader().topicName();
        ByteBuf payload = message.payload();
        AtomicBoolean handlerCalled = new AtomicBoolean();

        MqttHandler handler = resolveHandler(topic);
        if (handler == null) {
            payload.release();
            return new Delivery(Futures.immediateVoidFuture(), handlerCalled);
        }

        // never on the netty event loop, and one at a time per client, in arrival order
        ListenableFuture<Void> future;
        try {
            future = Futures.submitAsync(() -> {
                // counted before the check: disconnect() sets the flag before it reads this count, so either this
                // delivery sees the flag and skips, or the drain sees the count and waits for it
                startedDeliveries.incrementAndGet();
                if (client.isDisconnected()) {
                    finishDelivery();
                    return Futures.immediateFailedFuture(DELIVERY_SKIPPED);
                }
                handlerCalled.set(true);
                return adaptFuture(handler.onMessage(message));
            }, client.getDeliveryExecutor());
        } catch (RejectedExecutionException e) {
            // submitAsync throws where transformAsync failed the future; keep failing it so the payload is released
            future = Futures.immediateFailedFuture(e);
        }
        // releases the reference MqttPingHandler retained, which is what keeps the payload valid past channelRead0
        future.addListener(payload::release, MoreExecutors.directExecutor());
        return new Delivery(future, handlerCalled);
    }

    /**
     * Counts a delivery out of {@link #startedDeliveries} once the callback that writes its ack has run: registered
     * after that callback, and Guava runs a future's listeners in the order they were added.
     */
    private void trackForDrain(Delivery delivery) {
        delivery.future().addListener(() -> {
            if (delivery.handlerCalled().get()) {
                finishDelivery();
            }
        }, MoreExecutors.directExecutor());
    }

    private void finishDelivery() {
        if (startedDeliveries.decrementAndGet() == 0) {
            Promise<Void> promise = this.drained;
            if (promise != null) {
                promise.trySuccess(null);
            }
        }
    }

    /**
     * Completes once every delivery of this channel whose handler was called has its ack written. Only
     * {@link MqttClientImpl#disconnect(long, TimeUnit)} calls it, after setting the client's disconnected flag, from
     * which point no delivery calls its handler.
     */
    io.netty.util.concurrent.Future<Void> drain(Channel channel) {
        Promise<Void> promise = channel.eventLoop().newPromise();
        this.drained = promise;
        if (startedDeliveries.get() == 0) {
            promise.trySuccess(null);
        }
        return promise;
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
        cancelConnackTimeout();
        if (log.isTraceEnabled()) {
            log.trace("[{}][{}] Handling CONNACK: {}", client.getClientConfig().getOwnerId(), client.getClientConfig().getClientId(), message);
        }
        if (this.client.isDisconnected()) {
            // disconnect() ran while this CONNACK was on its way, and is closing the channel: the connection is never
            // reported as up, so its close is not reported as lost either. The channel is left to disconnect(), whose
            // DISCONNECT keeps the server from publishing the will.
            this.connectFuture.tryFailure(new ChannelClosedException("Client is disconnected"));
            return;
        }
        switch (message.variableHeader().connectReturnCode()) {
            case CONNECTION_ACCEPTED:
                channel.attr(CONNACK_ACCEPTED).set(Boolean.TRUE);
                // before the connect future completes, so that an on() its listeners make finds the session settled; the
                // resubscribes this makes go out with the CONNACK resend of the pending subscriptions just below
                this.client.onSessionEstablished(channel, message.variableHeader().isSessionPresent());
                this.connectFuture.trySuccess(new MqttConnectResult(true, MqttConnectReturnCode.CONNECTION_ACCEPTED, channel.closeFuture()));

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
                this.connectFuture.trySuccess(new MqttConnectResult(false, message.variableHeader().connectReturnCode(), channel.closeFuture()));
                channel.close();
                // Don't start reconnecting logic here
                break;
        }
        if (this.client.getCallback() != null) {
            this.client.getCallback().onConnAck(message);
        }
    }

    private void handleSubAck(Channel channel, MqttSubAckMessage message) {
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
            // also unregisters the handler its on() registered
            this.client.failSubscription(pendingSubscription, new MqttSubscriptionFailedException(
                    codes.isEmpty() ? "SUBACK for topic filter '" + topic + "' carries no return code"
                            : "Server refused the subscription to topic filter '" + topic + "'"));
        } else {
            MqttQoS grantedQos = MqttQoS.valueOf(code);
            // the handler was registered by on(), before the SUBSCRIBE went out
            this.client.getServerSubscriptions().put(topic, grantedQos);
            pendingSubscription.getFuture().trySuccess(grantedQos);
            if (pendingSubscription.isResubscribe() && grantedQos.value() < pendingSubscription.getRequestedQos().value()) {
                log.info("[{}][{}] Resubscribed {} at QoS {}, below the {} asked for", client.getClientConfig().getOwnerId(),
                        client.getClientConfig().getClientId(), topic, grantedQos.value(), pendingSubscription.getRequestedQos().value());
            }
            // only after recording the grant: an on() racing this SUBACK that finds the topic no longer pending finds the grant
            this.client.getPendingSubscribeTopics().remove(topic);
            if (!this.client.isRegistered(topic)) {
                // off() ran while this SUBSCRIBE was in flight, when there was nothing on the server to unsubscribe yet
                this.client.unsubscribeOnServer(channel, topic);
            }
        }
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
                var delivery = invokeHandlerForIncomingPublish(message);
                var future = delivery.future();
                DonAsynchron.withCallback(future,
                        (_) -> {
                            onHandlerSucceeded();
                            checkBackPressure(channel, false);
                        },
                        (t) -> {
                            if (t != DELIVERY_SKIPPED) {
                                onHandlerFailed(message, t);
                            }
                            checkBackPressure(channel, false);
                        }
                );
                trackForDrain(delivery);
            }

            case AT_LEAST_ONCE -> {
                final int msgId = message.variableHeader().packetId();
                var msgWrapper = mqttOrderedAcknowledgementCtxQoS1.addMsgId(msgId);

                if (msgWrapper == null) {
                    dropPublish(channel, message);
                    return;
                }

                var delivery = invokeHandlerForIncomingPublish(message);
                var future = delivery.future();
                DonAsynchron.withCallback(future,
                        (_) -> {
                            onHandlerSucceeded();
                            processPubAck(channel, msgWrapper, PubAck.SUCCESS.byteValue());
                            checkBackPressure(channel, false);
                        },
                        (t) -> {
                            if (t == DELIVERY_SKIPPED) {
                                // never acked: a kept session redelivers it to the next client
                                checkBackPressure(channel, false);
                                return;
                            }
                            onHandlerFailed(message, t);
                            processPubAck(channel, msgWrapper, PubAck.UNSPECIFIED_ERROR.byteValue());
                            checkBackPressure(channel, false);
                        }
                );
                trackForDrain(delivery);
            }

            case EXACTLY_ONCE -> {
                final int msgId = message.variableHeader().packetId();
                var msgWrapper = mqttOrderedAcknowledgementCtxQoS2.addMsgId(msgId);
                if (msgWrapper == null) {
                    dropPublish(channel, message);
                    return;
                }
                SettableFuture<Byte> outcome = SettableFuture.create();
                ListenableFuture<Byte> received = client.getQos2Received().putIfAbsent(msgId, outcome);
                if (received != null) {
                    // A resend of a message this client already has, from this connection or one before: it never reaches
                    // a handler again, and gets its PUBREC once the original has a result, with the original's code.
                    log.debug("Duplicate QoS2 message received for client {} with msgId {}. Skipping processing.", client.getClientConfig().getClientId(), msgId);
                    dropPublish(channel, message);
                    received.addListener(() -> processPubRec(channel, msgWrapper, Futures.getUnchecked(received)), MoreExecutors.directExecutor());
                    return;
                }
                var delivery = invokeHandlerForIncomingPublish(message);
                var future = delivery.future();
                DonAsynchron.withCallback(future,
                        (_) -> {
                            onHandlerSucceeded();
                            outcome.set(PubRec.SUCCESS.byteValue());
                            processPubRec(channel, msgWrapper, PubRec.SUCCESS.byteValue());
                            checkBackPressure(channel, false);
                        },
                        (t) -> {
                            if (t == DELIVERY_SKIPPED) {
                                // never acked, so nothing of it may stay in the receive state
                                client.getQos2Received().remove(msgId, outcome);
                                checkBackPressure(channel, false);
                                return;
                            }
                            onHandlerFailed(message, t);
                            byte code = PubRec.UNSPECIFIED_ERROR.byteValue();
                            if (MqttVersion.MQTT_5.equals(client.getClientConfig().getProtocolVersion())) {
                                // under MQTT 5 a failure code ends the exchange: no PUBREL follows to release the id
                                client.getQos2Received().remove(msgId, outcome);
                            }
                            outcome.set(code);
                            processPubRec(channel, msgWrapper, code);
                            checkBackPressure(channel, false);
                        }
                );
                trackForDrain(delivery);
            }
        }
    }

    /**
     * Logs a handler's failure: the first of a run as a warning, the rest at DEBUG, so that a handler forwarding to a
     * store that is down does not log a stack trace per message. {@link #onHandlerSucceeded} ends the run.
     */
    private void onHandlerFailed(MqttPublishMessage message, Throwable t) {
        String ownerId = client.getClientConfig().getOwnerId();
        String clientId = client.getClientConfig().getClientId();
        String topic = message.variableHeader().topicName();
        int qos = message.fixedHeader().qosLevel().value();
        if (handlerFailureRun.getAndIncrement() == 0) {
            log.warn("[{}][{}] The handler failed for a QoS {} message on '{}'; further failures are logged at DEBUG until a message is handled",
                    ownerId, clientId, qos, topic, t);
        } else {
            log.debug("[{}][{}] The handler failed for a QoS {} message on '{}'", ownerId, clientId, qos, topic, t);
        }
    }

    private void onHandlerSucceeded() {
        if (handlerFailureRun.get() == 0) {
            return;
        }
        int failed = handlerFailureRun.getAndSet(0);
        if (failed > 0) {
            log.info("[{}][{}] A message was handled again, after {} failed", client.getClientConfig().getOwnerId(),
                    client.getClientConfig().getClientId(), failed);
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
        final byte reasonCode = message.variableHeader() instanceof MqttPubReplyMessageVariableHeader pubrec
                ? pubrec.reasonCode()
                : PubRec.SUCCESS.byteValue();
        if (Byte.toUnsignedInt(reasonCode) >= 0x80) {
            // the server refused the publish: the QoS 2 exchange ends here, with no PUBREL, and frees the packet id
            MqttPendingPublish refused = this.client.getPendingPublishes().remove(msgId);
            if (refused != null) {
                refused.onPubackReceived();
                refused.getPayload().release();
                refused.getFuture().tryFailure(new MqttPublishFailedException("Server refused the QoS 2 publish to topic '%s' (message ID: %d) with PUBREC reason code 0x%02X"
                        .formatted(refused.getMessage().variableHeader().topicName(), msgId, reasonCode)));
            }
            return;
        }
        MqttPendingPublish pendingPublish = this.client.getPendingPublishes().get(msgId);
        if (pendingPublish == null) {
            // no publish is pending for it - its retransmissions ran out before this PUBREC arrived, say. Release it
            // all the same, or the server keeps the packet id in use; there is nothing left to retransmit or complete
            log.debug("[{}][{}] PUBREC for unknown packet id {}", client.getClientConfig().getOwnerId(), client.getClientConfig().getClientId(), msgId);
            processPubRel(channel, msgId, PubRel.PACKET_IDENTIFIER_NOT_FOUND.byteValue());
            return;
        }
        pendingPublish.onPubackReceived();

        // a PUBREL of its own: the PUBREC's reason code and properties are not ones a PUBREL may carry
        MqttMessage pubrelMessage = newReply(MqttMessageType.PUBREL, msgId, PubRel.SUCCESS.byteValue());
        channel.writeAndFlush(pubrelMessage);

        pendingPublish.setPubrelMessage(pubrelMessage);
        pendingPublish.startPubrelRetransmissionTimer(this.client.retransmissionLoop(channel), this.client::sendAndFlushPacket);
    }

    private void processPubRel(Channel channel, int msgId, byte reasonCodeValue) {
        sendMqttReply(channel, MqttMessageType.PUBREL, msgId, reasonCodeValue);
    }

    private void handlePubrel(Channel channel, MqttMessage message) {
        log.trace("[{}][{}] Handling PUBREL: {}", client.getClientConfig().getOwnerId(), client.getClientConfig().getClientId(), message);
        final int msgId = ((MqttMessageIdVariableHeader) message.variableHeader()).messageId();
        byte reasonCode = this.client.getQos2Received().remove(msgId) != null
                ? PubComp.SUCCESS.byteValue()
                : PubComp.PACKET_IDENTIFIER_NOT_FOUND.byteValue();
        processPubComp(channel, msgId, reasonCode);
    }

    private void processPubComp(Channel channel, int msgId, byte reasonCodeValue) {
        sendMqttReply(channel, MqttMessageType.PUBCOMP, msgId, reasonCodeValue);
    }

    private void sendMqttReply(Channel channel, MqttMessageType type, int msgId, byte reasonCodeValue) {
        channel.writeAndFlush(newReply(type, msgId, reasonCodeValue));
    }

    /**
     * A PUBREC, PUBREL or PUBCOMP for {@code msgId}, carrying {@code reasonCodeValue} under MQTT 5, which alone has
     * reason codes.
     */
    private MqttMessage newReply(MqttMessageType type, int msgId, byte reasonCodeValue) {
        // a PUBREL's fixed header flags are 0010, the other replies' 0000
        MqttQoS qos = type == MqttMessageType.PUBREL ? MqttQoS.AT_LEAST_ONCE : MqttQoS.AT_MOST_ONCE;
        MqttFixedHeader fixedHeader = new MqttFixedHeader(type, false, qos, false, 0);
        return MqttVersion.MQTT_5.equals(client.getClientConfig().getProtocolVersion())
                ? new MqttMessage(fixedHeader, new MqttPubReplyMessageVariableHeader(msgId, reasonCodeValue, null))
                : new MqttMessage(fixedHeader, MqttMessageIdVariableHeader.from(msgId));
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
        } else if (!increment && count < lowWatermark && !channel.config().isAutoRead() && !client.isDisconnected()) {
            // a disconnect() stopped reading for good: its drain must not start it again
            channel.config().setAutoRead(true);
            log.debug("Resumed MQTT reads: queue {} < {}", count, lowWatermark);
        }
    }

    /** PUBLISHes read and not yet done with, as the backpressure counts them. For tests. */
    long inFlightPublishes() {
        return publishMsgCount.get();
    }

}
