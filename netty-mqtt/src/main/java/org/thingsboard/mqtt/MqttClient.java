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

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.thingsboard.mqtt.broker.common.util.ListeningExecutor;

public interface MqttClient {

    /**
     * Connect to the specified hostname/ip. By default uses port 1883.
     * If you want to change the port number, see {@link #connect(String, int)}
     *
     * @param host The ip address or host to connect to
     * @return A future which will be completed when the connection is opened and we received an CONNACK; it fails
     * with the TCP connect's cause, an {@link javax.net.ssl.SSLException} for a failed TLS handshake, or a
     * {@link ChannelClosedException} when the channel closes before the CONNACK
     */
    Promise<MqttConnectResult> connect(String host);

    /**
     * Connect to the specified hostname/ip using the specified port
     *
     * @param host The ip address or host to connect to
     * @param port The tcp port to connect to
     * @return A future which will be completed when the connection is opened and we received an CONNACK; it fails
     * with the TCP connect's cause, an {@link javax.net.ssl.SSLException} for a failed TLS handshake, or a
     * {@link ChannelClosedException} when the channel closes before the CONNACK
     */
    Promise<MqttConnectResult> connect(String host, int port);

    /**
     *
     * @return boolean value indicating if channel is active
     */
    boolean isConnected();

    /**
     * Attempt reconnect to the host that was attempted with {@link #connect(String, int)} method before
     *
     * @return A future which will be completed when the connection is opened and we received an CONNACK; it fails
     * with the TCP connect's cause, an {@link javax.net.ssl.SSLException} for a failed TLS handshake, or a
     * {@link ChannelClosedException} when the channel closes before the CONNACK
     * @throws IllegalStateException if no previous {@link #connect(String, int)} calls were attempted
     */
    Promise<MqttConnectResult> reconnect();

    /**
     * Retrieve the netty {@link EventLoopGroup} we are using
     * @return The netty {@link EventLoopGroup} we use for the connection
     */
    EventLoopGroup getEventLoop();

    /**
     * By default we use the netty {@link NioEventLoopGroup}.
     * If you change the EventLoopGroup to another type, make sure to change the {@link Channel} class using {@link MqttClientConfig#setChannelClass(Class)}
     * If you want to force the MqttClient to use another {@link EventLoopGroup}, call this function before calling {@link #connect(String, int)}
     *
     * @param eventLoop The new eventloop to use
     */
    void setEventLoop(EventLoopGroup eventLoop);

    ListeningExecutor getHandlerExecutor();

    /**
     * Subscribe on the given topic. When a message is received, MqttClient will invoke the {@link MqttHandler#onMessage(MqttPublishMessage)} function of the given handler
     * <p>
     * A topic filter has at most one handler: calling {@code on} again for the same filter replaces its handler and
     * keeps the filter's position in delivery order. Handlers survive a reconnect, but the server-side subscription
     * does not - after a clean-session reconnect call {@code on} again. {@link #off(String)} stops routing to a filter.
     *
     * @param topic The topic filter to subscribe to
     * @param handler The handler to invoke when we receive a message
     * @return A future which completes with the QoS the server granted - for a filter already subscribed on the
     * server, the QoS granted then - or fails with {@link MqttSubscriptionFailedException} when the server refuses
     * the filter, in which case this call registers nothing; it also fails with {@link ChannelClosedException} when the
     * connection closes or the client disconnects before the SUBACK, or {@link MaxRetransmissionsReachedException} when
     * the retransmissions run out
     */
    Future<MqttQoS> on(String topic, MqttHandler handler);

    /**
     * Subscribe on the given topic, with the given qos. When a message is received, MqttClient will invoke the {@link MqttHandler#onMessage(MqttPublishMessage)} function of the given handler
     * <p>
     * A topic filter has at most one handler: calling {@code on} again for the same filter replaces its handler and
     * keeps the filter's position in delivery order. Handlers survive a reconnect, but the server-side subscription
     * does not - after a clean-session reconnect call {@code on} again. {@link #off(String)} stops routing to a filter.
     *
     * @param topic The topic filter to subscribe to
     * @param handler The handler to invoke when we receive a message
     * @param qos The qos to request to the server; ignored when the filter is already subscribed or its SUBSCRIBE is
     *            in flight
     * @return A future which completes with the QoS the server granted - for a filter already subscribed on the
     * server, the QoS granted then - or fails with {@link MqttSubscriptionFailedException} when the server refuses
     * the filter, in which case this call registers nothing; it also fails with {@link ChannelClosedException} when the
     * connection closes or the client disconnects before the SUBACK, or {@link MaxRetransmissionsReachedException} when
     * the retransmissions run out
     */
    Future<MqttQoS> on(String topic, MqttHandler handler, MqttQoS qos);

    /**
     * Remove the given topic filter, but only if its current handler equals {@code handler}; otherwise this is a
     * no-op whose future completes successfully. Removing the filter unsubscribes it on the server.
     *
     * @param topic The topic filter to unsubscribe for
     * @param handler The handler the filter must currently have
     * @return A future which will be completed when the server acknowledges our unsubscribe request, or fails
     * with {@link ChannelClosedException} when the connection closes before the UNSUBACK, or
     * {@link MaxRetransmissionsReachedException} when the retransmissions run out
     */
    Future<Void> off(String topic, MqttHandler handler);

    /**
     * Remove the given topic filter and its handler, and unsubscribe the filter on the server.
     *
     * @param topic The topic filter to unsubscribe for
     * @return A future which will be completed when the server acknowledges our unsubscribe request, or fails
     * with {@link ChannelClosedException} when the connection closes before the UNSUBACK, or
     * {@link MaxRetransmissionsReachedException} when the retransmissions run out
     */
    Future<Void> off(String topic);

    /**
     * Publish a message to the given payload.
     * <p>
     * All {@code publish} methods take ownership of {@code payload}: the client releases the caller's reference once
     * the message has been written, or failed to be written, and releases its own references when the publish
     * completes or the connection closes. The caller must not release the payload, nor use it after this call.
     *
     * @param topic The topic to publish to
     * @param payload The payload to send; ownership passes to the client, so the caller must not release it
     * @return A future which will be completed when the message is sent out of the MqttClient, or fails with the
     * write's cause, or with {@link ChannelClosedException} when the client is not connected
     */
    Future<Void> publish(String topic, ByteBuf payload);

    /**
     * Publish a message to the given payload, using the given qos
     * @param topic The topic to publish to
     * @param payload The payload to send; ownership passes to the client, so the caller must not release it
     * @param qos The qos to use while publishing
     * @return A future which will be completed when the message is delivered to the server, or fails with the
     * write's cause, {@link ChannelClosedException} when the client is not connected or the connection closes
     * before the acknowledgement, or {@link MaxRetransmissionsReachedException} when the retransmissions run out
     */
    Future<Void> publish(String topic, ByteBuf payload, MqttQoS qos);

    /**
     * Publish a message to the given payload, using optional retain
     * @param topic The topic to publish to
     * @param payload The payload to send; ownership passes to the client, so the caller must not release it
     * @param retain true if you want to retain the message on the server, false otherwise
     * @return A future which will be completed when the message is sent out of the MqttClient, or fails with the
     * write's cause, or with {@link ChannelClosedException} when the client is not connected
     */
    Future<Void> publish(String topic, ByteBuf payload, boolean retain);

    /**
     * Publish a message to the given payload, using the given qos and optional retain
     * @param topic The topic to publish to
     * @param payload The payload to send; ownership passes to the client, so the caller must not release it
     * @param qos The qos to use while publishing
     * @param retain true if you want to retain the message on the server, false otherwise
     * @return A future which will be completed when the message is delivered to the server, or fails with the
     * write's cause, {@link ChannelClosedException} when the client is not connected or the connection closes
     * before the acknowledgement, or {@link MaxRetransmissionsReachedException} when the retransmissions run out
     */
    Future<Void> publish(String topic, ByteBuf payload, MqttQoS qos, boolean retain);

    /**
     * Retrieve the MqttClient configuration
     * @return The {@link MqttClientConfig} instance we use
     */
    MqttClientConfig getClientConfig();


    /**
     * Construct the MqttClientImpl with additional config.
     * This config can also be changed using the {@link #getClientConfig()} function
     *
     * @param config The config object to use while looking for settings
     * @param defaultHandler The handler for incoming messages that do not match any topic subscriptions
     */
    static MqttClient create(MqttClientConfig config, MqttHandler defaultHandler, ListeningExecutor handlerExecutor) {
        return new MqttClientImpl(config, defaultHandler, handlerExecutor);
    }

    /**
     * Send disconnect and close channel
     *
     */
    void disconnect();

    /**
     * Sets the {@see #MqttClientCallback} object for this MqttClient
     * @param callback The callback to be set
     */
    void setCallback(MqttClientCallback callback);

}
