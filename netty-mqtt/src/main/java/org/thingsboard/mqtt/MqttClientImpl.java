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

import com.google.common.collect.Sets;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttSubscribePayload;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribePayload;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.util.ListeningExecutor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents an MqttClientImpl connected to a single MQTT server. Will try to keep the connection going at all times
 */
@SuppressWarnings({"WeakerAccess", "unused"})
@Slf4j
final class MqttClientImpl implements MqttClient {

    @Getter(AccessLevel.PACKAGE)
    private final Set<String> serverSubscriptions = new HashSet<>();
    @Getter(AccessLevel.PACKAGE)
    private final ConcurrentMap<Integer, MqttPendingUnsubscription> pendingServerUnsubscribes = new ConcurrentHashMap<>();
    @Getter(AccessLevel.PACKAGE)
    private final Set<Integer> qos2PendingMsgIds = ConcurrentHashMap.newKeySet();
    @Getter(AccessLevel.PACKAGE)
    private final ConcurrentMap<Integer, MqttPendingPublish> pendingPublishes = new ConcurrentHashMap<>();
    @Getter(AccessLevel.PACKAGE)
    private final CopyOnWriteArrayList<MqttSubscription> subscriptions = new CopyOnWriteArrayList<>();
    /**
     * Serialises every mutation of {@link #subscriptions}, so a replace-by-index can never interleave with a removal.
     * Delivery iterates the copy-on-write snapshot and takes no lock.
     */
    private final Object registryLock = new Object();
    @Getter(AccessLevel.PACKAGE)
    private final ConcurrentMap<Integer, MqttPendingSubscription> pendingSubscriptions = new ConcurrentHashMap<>();
    @Getter(AccessLevel.PACKAGE)
    private final Set<String> pendingSubscribeTopics = new HashSet<>();
    private final AtomicInteger nextMessageId = new AtomicInteger(1);

    @Getter
    private final MqttClientConfig clientConfig;

    @Getter(AccessLevel.PACKAGE)
    private final MqttHandler defaultHandler;

    private final ReconnectStrategy reconnectStrategy;

    private EventLoopGroup eventLoop;

    private volatile Channel channel;

    private volatile boolean disconnected = false;
    @Getter
    private volatile boolean reconnect = false;
    private String host;
    private int port;
    @Getter
    @Setter
    private MqttClientCallback callback;

    @Getter
    private final ListeningExecutor handlerExecutor;

    private final static int DISCONNECT_FALLBACK_DELAY_SECS = 1;

    /**
     * Construct the MqttClientImpl with default config
     */
    public MqttClientImpl(MqttHandler defaultHandler, ListeningExecutor handlerExecutor) {
        this(new MqttClientConfig(), defaultHandler, handlerExecutor);
    }

    /**
     * Construct the MqttClientImpl with additional config.
     * This config can also be changed using the {@link #getClientConfig()} function
     *
     * @param clientConfig The config object to use while looking for settings
     */
    public MqttClientImpl(MqttClientConfig clientConfig, MqttHandler defaultHandler, ListeningExecutor handlerExecutor) {
        this.clientConfig = clientConfig;
        this.defaultHandler = defaultHandler;
        this.handlerExecutor = handlerExecutor;
        this.reconnectStrategy = new ReconnectStrategyExponential(getClientConfig().getReconnectDelay());
    }

    /**
     * Connect to the specified hostname/ip. By default uses port 1883.
     * If you want to change the port number, see {@link #connect(String, int)}
     *
     * @param host The ip address or host to connect to
     * @return A future which will be completed when the connection is opened and we received an CONNACK
     */
    @Override
    public Promise<MqttConnectResult> connect(String host) {
        return connect(host, 1883);
    }

    /**
     * Connect to the specified hostname/ip using the specified port
     *
     * @param host The ip address or host to connect to
     * @param port The tcp port to connect to
     * @return A future which will be completed when the connection is opened and we received an CONNACK
     */
    @Override
    public Promise<MqttConnectResult> connect(String host, int port) {
        return connect(host, port, false);
    }

    private Promise<MqttConnectResult> connect(String host, int port, boolean reconnect) {
        log.trace("[{}] Connecting to server, isReconnect - {}", channel != null ? channel.id() : "UNKNOWN", reconnect);
        if (this.eventLoop == null) {
            this.eventLoop = new NioEventLoopGroup();
        }
        this.host = host;
        this.port = port;
        Promise<MqttConnectResult> connectFuture = new DefaultPromise<>(this.eventLoop.next());
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(this.eventLoop);
        bootstrap.channel(clientConfig.getChannelClass());
        bootstrap.remoteAddress(host, port);
        bootstrap.handler(new MqttChannelInitializer(connectFuture, host, port, clientConfig.getSslContext()));
        ChannelFuture future = bootstrap.connect();

        future.addListener((ChannelFutureListener) f -> {
            if (f.isSuccess()) {
                // Assign first, then re-check: disconnect() is a no-op while the channel is null, so a connect
                // completing after it would otherwise leave a live session that nobody holds a reference to.
                MqttClientImpl.this.channel = f.channel();
                if (disconnected) {
                    log.debug("[{}][{}] Connected after disconnect(); closing channel {}", host, port, f.channel().id());
                    f.channel().close();
                    return;
                }
                log.debug("[{}][{}] Connected successfully {}!", host, port, this.channel.id());
                MqttClientImpl.this.channel.closeFuture().addListener((ChannelFutureListener) channelFuture -> {
                    if (isConnected()) {
                        return;
                    }
                    log.debug("[{}][{}] Channel is closed {}!", host, port, this.channel.id());
                    ChannelClosedException e = new ChannelClosedException("Channel is closed!");
                    if (callback != null) {
                        callback.connectionLost(e);
                    }
                    pendingSubscriptions.forEach((id, mqttPendingSubscription) -> mqttPendingSubscription.onChannelClosed());
                    pendingSubscriptions.clear();
                    serverSubscriptions.clear();
                    pendingServerUnsubscribes.forEach((id, mqttPendingServerUnsubscribes) -> mqttPendingServerUnsubscribes.onChannelClosed());
                    pendingServerUnsubscribes.clear();
                    qos2PendingMsgIds.clear();
                    // remove each entry before releasing it: this path owns only what it removed itself, so an entry a
                    // concurrent write listener or ACK already took is not released twice
                    for (Integer id : pendingPublishes.keySet()) {
                        MqttPendingPublish mqttPendingPublish = pendingPublishes.remove(id);
                        if (mqttPendingPublish != null) {
                            mqttPendingPublish.onChannelClosed();
                        }
                    }
                    pendingSubscribeTopics.clear();
                    scheduleConnectIfRequired(host, port, true);
                });
            } else {
                log.debug("[{}][{}] Connect failed, trying reconnect!", host, port);
                scheduleConnectIfRequired(host, port, reconnect);
            }
        });
        return connectFuture;
    }

    private void scheduleConnectIfRequired(String host, int port, boolean reconnect) {
        log.trace("[{}][{}][{}] Scheduling connect to server, isReconnect - {}", host, port, channel != null ? channel.id() : "UNKNOWN", reconnect);
        if (clientConfig.isReconnect() && !disconnected) {
            if (reconnect) {
                this.reconnect = true;
            }

            final long nextReconnectDelay = reconnectStrategy.getNextReconnectDelay();
            log.debug("[{}][{}][{}] Scheduling reconnect in [{}] sec", host, port, channel != null ? channel.id() : "UNKNOWN", nextReconnectDelay);
            eventLoop.schedule((Runnable) () -> connect(host, port, reconnect), nextReconnectDelay, TimeUnit.SECONDS);
        }
    }

    @Override
    public boolean isConnected() {
        return !disconnected && channel != null && channel.isActive();
    }

    @Override
    public Promise<MqttConnectResult> reconnect() {
        log.trace("[{}] Reconnecting to server, isReconnect - {}", channel != null ? channel.id() : "UNKNOWN", reconnect);
        if (host == null) {
            throw new IllegalStateException("Cannot reconnect. Call connect() first");
        }
        return connect(host, port);
    }

    /**
     * Retrieve the netty {@link EventLoopGroup} we are using
     *
     * @return The netty {@link EventLoopGroup} we use for the connection
     */
    @Override
    public EventLoopGroup getEventLoop() {
        return eventLoop;
    }

    /**
     * By default we use the netty {@link NioEventLoopGroup}.
     * If you change the EventLoopGroup to another type, make sure to change the {@link Channel} class using {@link MqttClientConfig#setChannelClass(Class)}
     * If you want to force the MqttClient to use another {@link EventLoopGroup}, call this function before calling {@link #connect(String, int)}
     *
     * @param eventLoop The new eventloop to use
     */
    @Override
    public void setEventLoop(EventLoopGroup eventLoop) {
        this.eventLoop = eventLoop;
    }

    /**
     * Subscribe on the given topic. When a message is received, MqttClient will invoke the {@link MqttHandler#onMessage(MqttPublishMessage)} function of the given handler
     *
     * @param topic   The topic filter to subscribe to
     * @param handler The handler to invoke when we receive a message
     * @return A future which will be completed when the server acknowledges our subscribe request
     */
    @Override
    public Future<Void> on(String topic, MqttHandler handler) {
        return on(topic, handler, MqttQoS.AT_MOST_ONCE);
    }

    /**
     * Subscribe on the given topic, with the given qos. When a message is received, MqttClient will invoke the {@link MqttHandler#onMessage(MqttPublishMessage)} function of the given handler
     *
     * @param topic   The topic filter to subscribe to
     * @param handler The handler to invoke when we receive a message
     * @param qos     The qos to request to the server
     * @return A future which will be completed when the server acknowledges our subscribe request
     */
    @Override
    public Future<Void> on(String topic, MqttHandler handler, MqttQoS qos) {
        return createSubscription(topic, handler, qos);
    }

    /**
     * Remove the subscription for the given topic and handler
     * If you want to unsubscribe from all handlers known for this topic, use {@link #off(String)}
     *
     * @param topic   The topic to unsubscribe for
     * @param handler The handler to unsubscribe
     * @return A future which will be completed when the server acknowledges our unsubscribe request
     */
    @Override
    public Future<Void> off(String topic, MqttHandler handler) {
        log.trace("[{}] Unsubscribing from {}", channel != null ? channel.id() : "UNKNOWN", topic);
        Promise<Void> future = new DefaultPromise<>(this.eventLoop.next());
        synchronized (this.registryLock) {
            this.subscriptions.removeIf(s -> s.getTopic().equals(topic) && s.getHandler().equals(handler));
        }
        this.checkSubscriptions(topic, future);
        return future;
    }

    /**
     * Remove all subscriptions for the given topic.
     * If you want to specify which handler to unsubscribe, use {@link #off(String, MqttHandler)}
     *
     * @param topic The topic to unsubscribe for
     * @return A future which will be completed when the server acknowledges our unsubscribe request
     */
    @Override
    public Future<Void> off(String topic) {
        log.trace("[{}] Unsubscribing from {}", channel != null ? channel.id() : "UNKNOWN", topic);
        Promise<Void> future = new DefaultPromise<>(this.eventLoop.next());
        synchronized (this.registryLock) {
            this.subscriptions.removeIf(s -> s.getTopic().equals(topic));
        }
        this.checkSubscriptions(topic, future);
        return future;
    }

    /**
     * Publish a message to the given payload
     *
     * @param topic   The topic to publish to
     * @param payload The payload to send; ownership passes to the client, so the caller must not release it
     * @return A future which will be completed when the message is sent out of the MqttClient
     */
    @Override
    public Future<Void> publish(String topic, ByteBuf payload) {
        return publish(topic, payload, MqttQoS.AT_MOST_ONCE, false);
    }

    /**
     * Publish a message to the given payload, using the given qos
     *
     * @param topic   The topic to publish to
     * @param payload The payload to send; ownership passes to the client, so the caller must not release it
     * @param qos     The qos to use while publishing
     * @return A future which will be completed when the message is delivered to the server
     */
    @Override
    public Future<Void> publish(String topic, ByteBuf payload, MqttQoS qos) {
        return publish(topic, payload, qos, false);
    }

    /**
     * Publish a message to the given payload, using optional retain
     *
     * @param topic   The topic to publish to
     * @param payload The payload to send; ownership passes to the client, so the caller must not release it
     * @param retain  true if you want to retain the message on the server, false otherwise
     * @return A future which will be completed when the message is sent out of the MqttClient
     */
    @Override
    public Future<Void> publish(String topic, ByteBuf payload, boolean retain) {
        return publish(topic, payload, MqttQoS.AT_MOST_ONCE, retain);
    }

    /**
     * Publish a message to the given payload, using the given qos and optional retain
     *
     * @param topic   The topic to publish to
     * @param payload The payload to send; ownership passes to the client, so the caller must not release it
     * @param qos     The qos to use while publishing
     * @param retain  true if you want to retain the message on the server, false otherwise
     * @return A future which will be completed when the message is delivered to the server
     */
    @Override
    public Future<Void> publish(String topic, ByteBuf payload, MqttQoS qos, boolean retain) {
        log.trace("[{}] Publishing message to {}", channel != null ? channel.id() : "UNKNOWN", topic);
        Promise<Void> future = new DefaultPromise<>(this.eventLoop.next());
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, false, qos, retain, 0);
        MqttPublishVariableHeader variableHeader = new MqttPublishVariableHeader(topic, getNewMessageId().messageId());
        // the message carries the caller's reference, which the write hands to netty; the pending publish holds its own
        MqttPublishMessage message = new MqttPublishMessage(fixedHeader, variableHeader, payload);

        final var self = new AtomicReference<MqttPendingPublish>();
        final var pendingPublish = MqttPendingPublish.builder()
                .messageId(variableHeader.packetId())
                .future(future)
                .payload(payload.retain())
                .message(message)
                .qos(qos)
                .ownerId(clientConfig.getOwnerId())
                .retransmissionConfig(clientConfig.getRetransmissionConfig())
                .pendingOperation(new PendingOperation() {
                    @Override
                    public boolean isCancelled() {
                        // identity, not the packet id: once this entry is gone its id may already belong to a new publish
                        return pendingPublishes.get(variableHeader.packetId()) != self.get();
                    }

                    @Override
                    public void onMaxRetransmissionAttemptsReached() {
                        MqttPendingPublish exhausted = self.get();
                        if (!pendingPublishes.remove(variableHeader.packetId(), exhausted)) {
                            return; // acknowledged, failed or closed meanwhile: whoever removed it released it
                        }
                        var message = "Unable to deliver publish message due to max retransmission attempts (%s) being reached for client '%s' on topic '%s' (message ID: %d)"
                                .formatted(clientConfig.getRetransmissionConfig().maxAttempts(), clientConfig.getClientId(), topic, variableHeader.packetId());
                        exhausted.getFuture().tryFailure(new MaxRetransmissionsReachedException(message));
                        exhausted.getPayload().release();
                    }
                }).build();
        self.set(pendingPublish);

        this.pendingPublishes.put(pendingPublish.getMessageId(), pendingPublish);
        if (!pendingPublish.markSent()) {
            // a CONNACK arriving meanwhile resent it and now owns the caller's reference through that write
            return future;
        }
        ChannelFuture channelFuture = this.sendAndFlushPacket(message);

        if (channelFuture != null) {
            // netty consumed the caller's reference, whether the write succeeds or fails
            channelFuture.addListener(result -> {
                if (result.cause() != null) {
                    releaseIfRemoved(pendingPublish);
                    future.setFailure(result.cause());
                } else if (pendingPublish.getQos() == MqttQoS.AT_MOST_ONCE) {
                    releaseIfRemoved(pendingPublish);
                    pendingPublish.getFuture().setSuccess(null); //We don't get an ACK for QOS 0
                } else {
                    pendingPublish.startPublishRetransmissionTimer(eventLoop.next(), MqttClientImpl.this::sendAndFlushPacket);
                }
            });
        } else {
            // no channel, so nothing was written: the caller's reference was never consumed either
            releaseIfRemoved(pendingPublish);
            message.release();
        }
        return future;
    }

    /**
     * Releases the pending publish's payload reference if, and only if, this call removed the entry, so that no two
     * paths (write listener, ACK, max retransmissions, channel close) can both release it.
     */
    void releaseIfRemoved(MqttPendingPublish pendingPublish) {
        if (pendingPublishes.remove(pendingPublish.getMessageId(), pendingPublish)) {
            pendingPublish.getPayload().release();
        }
    }

    @Override
    public void disconnect() {
        if (disconnected) {
            return;
        }

        disconnected = true;
        // Pin the channel: a reconnect may replace the field, and the fallback below would then close the new one.
        final Channel ch = this.channel;
        log.trace("[{}] Disconnecting from server", ch != null ? ch.id() : "UNKNOWN");
        if (ch != null) {
            MqttMessage message = new MqttMessage(new MqttFixedHeader(MqttMessageType.DISCONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0));

            sendAndFlushPacket(ch, message).addListener((ChannelFutureListener) future -> future.channel().close());
            eventLoop.schedule(() -> {
                if (ch.isOpen()) {
                    log.trace("[{}] Channel still open after {} second; forcing close now", ch.id(), DISCONNECT_FALLBACK_DELAY_SECS);
                    ch.close();
                }
            }, DISCONNECT_FALLBACK_DELAY_SECS, TimeUnit.SECONDS);
        }
    }


    ///////////////////////////////////////////// PRIVATE API /////////////////////////////////////////////

    public void onSuccessfulReconnect() {
        if (callback != null) {
            callback.onSuccessfulReconnect();
        }
    }

    ChannelFuture sendAndFlushPacket(Object message) {
        return sendAndFlushPacket(this.channel, message);
    }

    /**
     * Sends on the channel the caller pinned, instead of re-reading the volatile field at each step.
     * Returns null when there is no channel yet; callers use that to defer delivery until the connection opens, and
     * still own the message then. Otherwise the message is consumed: written, or released if the channel is inactive.
     */
    private ChannelFuture sendAndFlushPacket(Channel ch, Object message) {
        if (ch == null) {
            return null;
        }
        if (ch.isActive()) {
            log.trace("[{}] Sending message {}", ch.id(), message);
            return ch.writeAndFlush(message);
        }
        // netty releases a message whose write fails; do the same for one that is refused before reaching netty
        ReferenceCountUtil.release(message);
        return ch.newFailedFuture(new ChannelClosedException("Channel is closed!"));
    }

    private MqttMessageIdVariableHeader getNewMessageId() {
        int messageId;
        synchronized (this.nextMessageId) {
            this.nextMessageId.compareAndSet(0xffff, 1);
            messageId = this.nextMessageId.getAndIncrement();
        }
        return MqttMessageIdVariableHeader.from(messageId);
    }

    private Future<Void> createSubscription(String topic, MqttHandler handler, MqttQoS qos) {
        log.trace("[{}] Creating subscription to {}", channel != null ? channel.id() : "UNKNOWN", topic);
        if (this.pendingSubscribeTopics.contains(topic)) {
            Optional<Map.Entry<Integer, MqttPendingSubscription>> subscriptionEntry = this.pendingSubscriptions.entrySet().stream().filter((e) -> e.getValue().getTopic().equals(topic)).findAny();
            if (subscriptionEntry.isPresent()) {
                subscriptionEntry.get().getValue().addHandler(handler);
                return subscriptionEntry.get().getValue().getFuture();
            }
        }
        if (this.serverSubscriptions.contains(topic)) {
            register(new MqttSubscription(topic, handler));
            return this.channel.newSucceededFuture();
        }

        Promise<Void> future = new DefaultPromise<>(this.eventLoop.next());
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.SUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, false, 0);
        MqttTopicSubscription subscription = new MqttTopicSubscription(topic, qos);
        MqttMessageIdVariableHeader variableHeader = getNewMessageId();
        MqttSubscribePayload payload = new MqttSubscribePayload(Collections.singletonList(subscription));
        MqttSubscribeMessage message = new MqttSubscribeMessage(fixedHeader, variableHeader, payload);

        final var pendingSubscription = MqttPendingSubscription.builder()
                .future(future)
                .topic(topic)
                .handlers(Sets.newLinkedHashSet(Collections.singleton(handler))) // ordered: the last handler added wins on SUBACK
                .subscribeMessage(message)
                .ownerId(clientConfig.getOwnerId())
                .retransmissionConfig(clientConfig.getRetransmissionConfig())
                .pendingOperation(new PendingOperation() {
                    @Override
                    public boolean isCancelled() {
                        return !pendingSubscriptions.containsKey(variableHeader.messageId());
                    }

                    @Override
                    public void onMaxRetransmissionAttemptsReached() {
                        pendingSubscriptions.computeIfPresent(variableHeader.messageId(), (__, pendingSubscription) -> {
                            var message = "Unable to deliver subscribe message due to max retransmission attempts (%s) being reached for client '%s' on topic '%s' (message ID: %d)"
                                    .formatted(clientConfig.getRetransmissionConfig().maxAttempts(), clientConfig.getClientId(), topic, variableHeader.messageId());
                            pendingSubscription.getFuture().tryFailure(new MaxRetransmissionsReachedException(message));
                            return null;
                        });
                    }
                }).build();

        this.pendingSubscriptions.put(variableHeader.messageId(), pendingSubscription);
        this.pendingSubscribeTopics.add(topic);
        pendingSubscription.setSent(this.sendAndFlushPacket(message) != null); //If not sent, we will send it when the connection is opened

        pendingSubscription.startRetransmitTimer(this.eventLoop.next(), this::sendAndFlushPacket);

        return future;
    }

    /**
     * Registers {@code subscription}. A topic filter has at most one handler: when the filter is already registered its
     * handler is replaced in place, keeping the filter's position in delivery order; otherwise the filter is appended.
     */
    void register(MqttSubscription subscription) {
        synchronized (this.registryLock) {
            for (int i = 0; i < this.subscriptions.size(); i++) {
                if (this.subscriptions.get(i).getTopic().equals(subscription.getTopic())) {
                    this.subscriptions.set(i, subscription);
                    return;
                }
            }
            this.subscriptions.add(subscription);
        }
    }

    private void checkSubscriptions(String topic, Promise<Void> promise) {
        if (this.subscriptions.stream().noneMatch(s -> s.getTopic().equals(topic))
                && this.serverSubscriptions.contains(topic)) {
            MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.UNSUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, false, 0);
            MqttMessageIdVariableHeader variableHeader = getNewMessageId();
            MqttUnsubscribePayload payload = new MqttUnsubscribePayload(Collections.singletonList(topic));
            MqttUnsubscribeMessage message = new MqttUnsubscribeMessage(fixedHeader, variableHeader, payload);

            final var pendingUnsubscription = MqttPendingUnsubscription.builder()
                    .future(promise)
                    .topic(topic)
                    .unsubscribeMessage(message)
                    .ownerId(clientConfig.getOwnerId())
                    .retransmissionConfig(clientConfig.getRetransmissionConfig())
                    .pendingOperation(new PendingOperation() {
                        @Override
                        public boolean isCancelled() {
                            return !pendingServerUnsubscribes.containsKey(variableHeader.messageId());
                        }

                        @Override
                        public void onMaxRetransmissionAttemptsReached() {
                            pendingServerUnsubscribes.computeIfPresent(variableHeader.messageId(), (__, pendingUnsubscription) -> {
                                var message = "Unable to deliver unsubscribe message due to max retransmission attempts (%s) being reached for client '%s' on topic '%s' (message ID: %d)"
                                        .formatted(clientConfig.getRetransmissionConfig().maxAttempts(), clientConfig.getClientId(), topic, variableHeader.messageId());
                                pendingUnsubscription.getFuture().tryFailure(new MaxRetransmissionsReachedException(message));
                                return null;
                            });
                        }
                    }).build();

            this.pendingServerUnsubscribes.put(variableHeader.messageId(), pendingUnsubscription);
            pendingUnsubscription.startRetransmissionTimer(this.eventLoop.next(), this::sendAndFlushPacket);

            this.sendAndFlushPacket(message);
        } else {
            promise.setSuccess(null);
        }
    }

    private class MqttChannelInitializer extends ChannelInitializer<SocketChannel> {

        private final Promise<MqttConnectResult> connectFuture;
        private final String host;
        private final int port;
        private final SslContext sslContext;


        public MqttChannelInitializer(Promise<MqttConnectResult> connectFuture, String host, int port, SslContext sslContext) {
            this.connectFuture = connectFuture;
            this.host = host;
            this.port = port;
            this.sslContext = sslContext;
        }

        @Override
        protected void initChannel(SocketChannel ch) {
            if (sslContext != null) {
                ch.pipeline().addLast(sslContext.newHandler(ch.alloc(), host, port));
            }

            ch.pipeline().addLast("mqttDecoder", new MqttDecoder(clientConfig.getMaxBytesInMessage()));
            ch.pipeline().addLast("mqttEncoder", MqttEncoder.INSTANCE);
            ch.pipeline().addLast("idleStateHandler", new IdleStateHandler(MqttClientImpl.this.clientConfig.getTimeoutSeconds(), MqttClientImpl.this.clientConfig.getTimeoutSeconds(), 0));
            ch.pipeline().addLast("mqttPingHandler", new MqttPingHandler(MqttClientImpl.this.clientConfig.getTimeoutSeconds()));
            ch.pipeline().addLast("mqttHandler", new MqttChannelHandler(MqttClientImpl.this, connectFuture));
        }

    }

}
