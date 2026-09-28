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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoop;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Represents an MqttClientImpl connected to a single MQTT server. Will try to keep the connection going at all times
 * <p>
 * Every future handed out completes exactly once, and exactly one path completes it:
 * <ul>
 * <li>a connect future - the connect listener on a failed TCP connect; otherwise {@link MqttChannelHandler}, on the
 * CONNACK or in its {@code channelInactive};</li>
 * <li>a pending subscribe, unsubscribe or publish - whoever removes its entry from the pending map: the ACK handler,
 * max retransmission, the close cleanup, {@link #disconnect()} or the no-channel sweep, through {@link #drain} or an
 * explicit remove-then-act. The remover also releases the entry's payload.</li>
 * </ul>
 */
@SuppressWarnings({"WeakerAccess", "unused"})
@Slf4j
final class MqttClientImpl implements MqttClient {

    /**
     * The topic filters subscribed on the server, each with the QoS the server granted it in its SUBACK.
     */
    @Getter(AccessLevel.PACKAGE)
    private final ConcurrentMap<String, MqttQoS> serverSubscriptions = new ConcurrentHashMap<>();
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
    private final Set<String> pendingSubscribeTopics = ConcurrentHashMap.newKeySet();
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
     * @return A future which will be completed when the connection is opened and we received an CONNACK; it fails
     * with the TCP connect's cause, an {@link javax.net.ssl.SSLException} for a failed TLS handshake, or a
     * {@link ChannelClosedException} when the channel closes before the CONNACK
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
     * @return A future which will be completed when the connection is opened and we received an CONNACK; it fails
     * with the TCP connect's cause, an {@link javax.net.ssl.SSLException} for a failed TLS handshake, or a
     * {@link ChannelClosedException} when the channel closes before the CONNACK
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

        // Once the TCP connect succeeds, MqttChannelHandler completes connectFuture: with the CONNACK, or failed when
        // the channel closes before one arrives. Here only a failed TCP connect is left to fail it.
        future.addListener((ChannelFutureListener) f -> {
            if (f.isSuccess()) {
                // Assign first, then re-check: disconnect() closes nothing while the channel is null, so a connect
                // completing after it would otherwise leave a live session that nobody holds a reference to.
                MqttClientImpl.this.channel = f.channel();
                // Before the re-check, so that closing a channel connected after disconnect() fails what waits for it too
                MqttClientImpl.this.channel.closeFuture().addListener((ChannelFutureListener) channelFuture -> onChannelClosed(host, port));
                if (disconnected) {
                    log.debug("[{}][{}] Connected after disconnect(); closing channel {}", host, port, f.channel().id());
                    f.channel().close();
                    return;
                }
                log.debug("[{}][{}] Connected successfully {}!", host, port, this.channel.id());
            } else {
                log.debug("[{}][{}] Connect failed, trying reconnect!", host, port);
                boolean reconnectScheduled = scheduleConnectIfRequired(host, port, reconnect);
                if (!reconnectScheduled) {
                    // no connection is coming to send them on; failed before the connect future, so that a listener
                    // on it retrying connect() keeps the subscriptions it makes for the retry
                    failPendingSubscriptions(new ChannelClosedException("Connect failed and no reconnect is scheduled", f.cause()));
                }
                connectFuture.tryFailure(f.cause());
            }
        });
        return connectFuture;
    }

    /**
     * The close cleanup of a channel. Clears the plain state first, then drains the pending operations, so the
     * listeners that completing them runs - which may subscribe or publish again - find the client fully cleaned up.
     */
    private void onChannelClosed(String host, int port) {
        if (isConnected()) {
            return;
        }
        log.debug("[{}][{}] Channel is closed {}!", host, port, this.channel.id());
        ChannelClosedException e = new ChannelClosedException("Channel is closed!");
        if (callback != null) {
            callback.connectionLost(e);
        }
        serverSubscriptions.clear();
        qos2PendingMsgIds.clear();
        pendingSubscribeTopics.clear();
        drain(pendingSubscriptions, MqttPendingSubscription::onChannelClosed);
        drain(pendingServerUnsubscribes, MqttPendingUnsubscription::onChannelClosed);
        drain(pendingPublishes, MqttPendingPublish::onChannelClosed);
        scheduleConnectIfRequired(host, port, true);
    }

    /**
     * Removes each entry, then hands it to onRemoved: the caller owns - completes and releases - only what it removed.
     * The keys are copied first, so an entry that onRemoved's listeners add - a retry - is left for its own
     * completion, whereas a live view might or might not reach it.
     */
    private static <V> void drain(Map<Integer, V> map, Consumer<V> onRemoved) {
        for (Integer id : List.copyOf(map.keySet())) {
            V v = map.remove(id);
            if (v != null) {
                onRemoved.accept(v);
            }
        }
    }

    /**
     * @return whether a connect attempt was scheduled
     */
    private boolean scheduleConnectIfRequired(String host, int port, boolean reconnect) {
        log.trace("[{}][{}][{}] Scheduling connect to server, isReconnect - {}", host, port, channel != null ? channel.id() : "UNKNOWN", reconnect);
        if (clientConfig.isReconnect() && !disconnected) {
            if (reconnect) {
                this.reconnect = true;
            }

            final long nextReconnectDelay = reconnectStrategy.getNextReconnectDelay();
            log.debug("[{}][{}][{}] Scheduling reconnect in [{}] sec", host, port, channel != null ? channel.id() : "UNKNOWN", nextReconnectDelay);
            eventLoop.schedule((Runnable) () -> connect(host, port, reconnect), nextReconnectDelay, TimeUnit.SECONDS);
            return true;
        }
        return false;
    }

    /**
     * Fails every subscription still waiting for its SUBACK, for when no channel will carry one: the connect failed
     * with no reconnect to follow, or the client was disconnected. Removes each entry before failing it, so no other
     * path completes it too.
     */
    private void failPendingSubscriptions(Throwable cause) {
        drain(pendingSubscriptions, pendingSubscription -> {
            pendingSubscribeTopics.remove(pendingSubscription.getTopic());
            pendingSubscription.fail(cause);
        });
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
     * @return A future which completes with the QoS the server granted, or fails with
     * {@link MqttSubscriptionFailedException} when the server refuses the filter, {@link ChannelClosedException} when the
     * connection closes or the client disconnects before the SUBACK, or {@link MaxRetransmissionsReachedException} when
     * the retransmissions run out
     */
    @Override
    public Future<MqttQoS> on(String topic, MqttHandler handler) {
        return on(topic, handler, MqttQoS.AT_MOST_ONCE);
    }

    /**
     * Subscribe on the given topic, with the given qos. When a message is received, MqttClient will invoke the {@link MqttHandler#onMessage(MqttPublishMessage)} function of the given handler
     *
     * @param topic   The topic filter to subscribe to
     * @param handler The handler to invoke when we receive a message
     * @param qos     The qos to request to the server; ignored when the filter is already subscribed or its SUBSCRIBE
     *                is in flight
     * @return A future which completes with the QoS the server granted, or fails with
     * {@link MqttSubscriptionFailedException} when the server refuses the filter, {@link ChannelClosedException} when the
     * connection closes or the client disconnects before the SUBACK, or {@link MaxRetransmissionsReachedException} when
     * the retransmissions run out
     */
    @Override
    public Future<MqttQoS> on(String topic, MqttHandler handler, MqttQoS qos) {
        return createSubscription(topic, handler, qos);
    }

    /**
     * Remove the given topic filter, but only if its current handler equals {@code handler}; otherwise this is a
     * no-op whose future completes successfully. Removing the filter unsubscribes it on the server.
     *
     * @param topic   The topic filter to unsubscribe for
     * @param handler The handler the filter must currently have
     * @return A future which will be completed when the server acknowledges our unsubscribe request, or fails
     * with {@link ChannelClosedException} when the connection closes before the UNSUBACK, or
     * {@link MaxRetransmissionsReachedException} when the retransmissions run out
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
     * Remove the given topic filter and its handler, and unsubscribe the filter on the server.
     *
     * @param topic The topic filter to unsubscribe for
     * @return A future which will be completed when the server acknowledges our unsubscribe request, or fails
     * with {@link ChannelClosedException} when the connection closes before the UNSUBACK, or
     * {@link MaxRetransmissionsReachedException} when the retransmissions run out
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
     * @return A future which will be completed when the message is sent out of the MqttClient, or fails with the
     * write's cause, or with {@link ChannelClosedException} when the client is not connected
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
     * @return A future which will be completed when the message is delivered to the server, or fails with the
     * write's cause, {@link ChannelClosedException} when the client is not connected or the connection closes
     * before the acknowledgement, or {@link MaxRetransmissionsReachedException} when the retransmissions run out
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
     * @return A future which will be completed when the message is sent out of the MqttClient, or fails with the
     * write's cause, or with {@link ChannelClosedException} when the client is not connected
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
     * @return A future which will be completed when the message is delivered to the server, or fails with the
     * write's cause, {@link ChannelClosedException} when the client is not connected or the connection closes
     * before the acknowledgement, or {@link MaxRetransmissionsReachedException} when the retransmissions run out
     */
    @Override
    public Future<Void> publish(String topic, ByteBuf payload, MqttQoS qos, boolean retain) {
        log.trace("[{}] Publishing message to {}", channel != null ? channel.id() : "UNKNOWN", topic);
        MqttPendingPublish pendingPublish = registerPendingPublish(topic, payload, qos, retain);
        Promise<Void> future = pendingPublish.getFuture();
        if (!pendingPublish.markSent()) {
            // a CONNACK arriving meanwhile resent it and now owns the caller's reference through that write
            return future;
        }
        ChannelFuture channelFuture = this.sendAndFlushPacket(pendingPublish.getMessage());

        if (channelFuture != null) {
            // netty consumed the caller's reference, whether the write succeeds or fails; an inactive channel's
            // refusal arrives here as an already failed future, and completes through the same path
            channelFuture.addListener((ChannelFutureListener) f -> onFirstWriteComplete(pendingPublish, f));
        } else {
            // no channel, so nothing was written: the caller's reference was never consumed either
            releaseIfRemoved(pendingPublish);
            pendingPublish.getMessage().release();
            future.tryFailure(new ChannelClosedException("Client is not connected"));
        }
        return future;
    }

    /**
     * Builds the pending publish for a new message and registers it in the pending publishes, unsent. Its message
     * carries the caller's reference to {@code payload}, and the entry holds one more of its own. Whoever then claims
     * the first write with {@link MqttPendingPublish#markSent()} writes the message and completes that write through
     * {@link #onFirstWriteComplete}.
     */
    MqttPendingPublish registerPendingPublish(String topic, ByteBuf payload, MqttQoS qos, boolean retain) {
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
        return pendingPublish;
    }

    /**
     * Completes the first write of a pending publish, from whichever path claimed it with
     * {@link MqttPendingPublish#markSent()}: {@link #publish} or the CONNACK resend. The write has consumed the caller's
     * reference either way. A failed write fails the future, and a QoS 0 write completes it, both releasing the entry's
     * reference if that entry is still theirs to remove; a QoS 1/2 write starts its retransmission on the channel it was
     * written on, and stays pending until acknowledged.
     */
    void onFirstWriteComplete(MqttPendingPublish pendingPublish, ChannelFuture f) {
        if (!f.isSuccess()) {
            releaseIfRemoved(pendingPublish);
            pendingPublish.getFuture().tryFailure(f.cause());
        } else if (pendingPublish.getQos() == MqttQoS.AT_MOST_ONCE) {
            releaseIfRemoved(pendingPublish);
            pendingPublish.getFuture().trySuccess(null); // We don't get an ACK for QOS 0
        } else {
            startPublishRetransmission(pendingPublish, f.channel());
        }
    }

    /**
     * Starts retransmitting a QoS 1/2 publish just written on {@code ch}. Only {@link #onFirstWriteComplete} calls it, once
     * per first write.
     */
    private void startPublishRetransmission(MqttPendingPublish pendingPublish, Channel ch) {
        pendingPublish.startPublishRetransmissionTimer(retransmissionLoop(ch), this::sendAndFlushPacket);
    }

    /**
     * The loop a retransmission timer runs on: the channel's own, where the ACK handlers, the write listeners and the
     * close cleanup that end a pending operation all run. The timer's cancelled check and its retransmit, which retains
     * a publish's payload, are then serialised with every release of that payload. This relies on a pending publish
     * never outliving its channel: the close cleanup removes every pending publish, so a timer that fires after its
     * channel closed finds itself cancelled and retains nothing. The one exception is a caller running connect() or
     * reconnect() while a channel is still live, which skips that cleanup - a known gap no caller currently hits. Only a
     * subscription made before the first connect has no channel yet; it carries no reference-counted payload, so any
     * loop of the group does for it.
     */
    EventLoop retransmissionLoop(Channel ch) {
        return ch != null ? ch.eventLoop() : this.eventLoop.next();
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
        // whatever the channel: with none, or one already closed while a reconnect was pending, no close is left to
        // fail what waits for a CONNACK; a live channel's close finds these gone, and a connect still in flight closes
        // the channel it gets, which fails whatever was added meanwhile
        failPendingSubscriptions(new ChannelClosedException("Client is disconnected"));
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

    private Future<MqttQoS> createSubscription(String topic, MqttHandler handler, MqttQoS qos) {
        log.trace("[{}] Creating subscription to {}", channel != null ? channel.id() : "UNKNOWN", topic);
        if (this.pendingSubscribeTopics.contains(topic)) {
            Optional<Map.Entry<Integer, MqttPendingSubscription>> subscriptionEntry = this.pendingSubscriptions.entrySet().stream().filter((e) -> e.getValue().getTopic().equals(topic)).findAny();
            if (subscriptionEntry.isPresent()) {
                // the SUBSCRIBE in flight registers the handler given last; every caller observes its grant or refusal
                subscriptionEntry.get().getValue().setHandler(handler);
                return subscriptionEntry.get().getValue().getFuture();
            }
        }
        MqttQoS grantedQos = this.serverSubscriptions.get(topic);
        if (grantedQos != null) {
            register(new MqttSubscription(topic, handler));
            // already subscribed on the server, which granted this QoS then; no SUBSCRIBE is sent for the QoS asked now
            return this.eventLoop.next().newSucceededFuture(grantedQos);
        }

        Promise<MqttQoS> future = new DefaultPromise<>(this.eventLoop.next());
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.SUBSCRIBE, false, MqttQoS.AT_LEAST_ONCE, false, 0);
        MqttTopicSubscription subscription = new MqttTopicSubscription(topic, qos);
        MqttMessageIdVariableHeader variableHeader = getNewMessageId();
        MqttSubscribePayload payload = new MqttSubscribePayload(Collections.singletonList(subscription));
        MqttSubscribeMessage message = new MqttSubscribeMessage(fixedHeader, variableHeader, payload);

        final var pendingSubscription = MqttPendingSubscription.builder()
                .future(future)
                .topic(topic)
                .handler(handler)
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
                        // remove, then fail outside the map: a listener that subscribes again updates this map
                        MqttPendingSubscription exhausted = pendingSubscriptions.remove(variableHeader.messageId());
                        if (exhausted == null) {
                            return;
                        }
                        var message = "Unable to deliver subscribe message due to max retransmission attempts (%s) being reached for client '%s' on topic '%s' (message ID: %d)"
                                .formatted(clientConfig.getRetransmissionConfig().maxAttempts(), clientConfig.getClientId(), topic, variableHeader.messageId());
                        exhausted.getFuture().tryFailure(new MaxRetransmissionsReachedException(message));
                    }
                }).build();

        this.pendingSubscriptions.put(variableHeader.messageId(), pendingSubscription);
        this.pendingSubscribeTopics.add(topic);
        if (this.disconnected) {
            // a disconnected client never connects again, and disconnect() may have swept the pending subscriptions
            // before this one was added
            failPendingSubscriptions(new ChannelClosedException("Client is disconnected"));
            return future;
        }
        final Channel ch = this.channel;
        pendingSubscription.setSent(this.sendAndFlushPacket(ch, message) != null); //If not sent, we will send it when the connection is opened

        pendingSubscription.startRetransmitTimer(retransmissionLoop(ch), this::sendAndFlushPacket);

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
                && this.serverSubscriptions.containsKey(topic)) {
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
                            // remove, then fail outside the map: a listener that unsubscribes again updates this map
                            MqttPendingUnsubscription exhausted = pendingServerUnsubscribes.remove(variableHeader.messageId());
                            if (exhausted == null) {
                                return;
                            }
                            var message = "Unable to deliver unsubscribe message due to max retransmission attempts (%s) being reached for client '%s' on topic '%s' (message ID: %d)"
                                    .formatted(clientConfig.getRetransmissionConfig().maxAttempts(), clientConfig.getClientId(), topic, variableHeader.messageId());
                            exhausted.getFuture().tryFailure(new MaxRetransmissionsReachedException(message));
                        }
                    }).build();

            this.pendingServerUnsubscribes.put(variableHeader.messageId(), pendingUnsubscription);
            final Channel ch = this.channel;
            pendingUnsubscription.startRetransmissionTimer(retransmissionLoop(ch), this::sendAndFlushPacket);

            this.sendAndFlushPacket(ch, message);
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
