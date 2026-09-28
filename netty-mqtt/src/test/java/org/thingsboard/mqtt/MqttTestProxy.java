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
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

@Slf4j
public class MqttTestProxy {

    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;

    private volatile Channel clientToProxyChannel;
    private Channel proxyToBrokerChannel;

    // closed by stop() itself, so that the port is free for a new proxy once stop() returns; the event loop groups
    // shut down gracefully, and would only close it after their quiet period
    private final Channel serverChannel;

    private final int assignedPort;

    private boolean stopped;

    private final Predicate<MqttMessage> brokerToClientInterceptor;

    private final UnaryOperator<MqttMessage> brokerToClientRewriter;

    private MqttTestProxy(Builder builder) {
        log.info("Starting MQTT proxy...");

        brokerToClientInterceptor = builder.brokerToClientInterceptor != null ? builder.brokerToClientInterceptor : msg -> true;
        brokerToClientRewriter = builder.brokerToClientRewriter;
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(1);

        ServerBootstrap proxyBootstrap = new ServerBootstrap();
        proxyBootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        clientToProxyChannel = channel;
                        clientToProxyChannel.config().setAutoRead(false); // do not accept data before we connected to a broker

                        connectToBroker(builder.brokerHost, builder.brokerPort).addListener(future -> {
                            if (future.isSuccess()) {
                                clientToProxyChannel.pipeline().addLast("mqttDecoder", new MqttDecoder());
                                clientToProxyChannel.pipeline().addLast("mqttToBroker", new MqttRelayHandler(proxyToBrokerChannel, null, null));
                                clientToProxyChannel.pipeline().addLast("mqttEncoder", MqttEncoder.INSTANCE);

                                clientToProxyChannel.config().setAutoRead(true); // start accepting data for a client
                            } else {
                                log.error("Failed to connect to broker", future.cause());
                                clientToProxyChannel.close();
                            }
                        });
                    }
                });

        try {
            serverChannel = proxyBootstrap.bind(builder.localPort).sync().channel();
            assignedPort = ((InetSocketAddress) serverChannel.localAddress()).getPort();
        } catch (Exception e) {
            log.error("Failed to start MQTT proxy", e);
            throw new RuntimeException("Failed to start MQTT proxy", e);
        }

        log.info("MQTT proxy started on port {}", assignedPort);
    }

    private ChannelFuture connectToBroker(String brokerHost, int brokerPort) {
        Bootstrap proxyToBrokerBootstrap = new Bootstrap();
        proxyToBrokerBootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        proxyToBrokerChannel = channel;
                        proxyToBrokerChannel.pipeline().addLast(new MqttDecoder());
                        proxyToBrokerChannel.pipeline().addLast("mqttToClient", new MqttRelayHandler(clientToProxyChannel, brokerToClientInterceptor, brokerToClientRewriter));
                        proxyToBrokerChannel.pipeline().addLast(MqttEncoder.INSTANCE);
                    }
                });
        return proxyToBrokerBootstrap.connect(brokerHost, brokerPort);
    }

    private static class MqttRelayHandler extends SimpleChannelInboundHandler<MqttMessage> {

        private final Channel targetChannel;
        private final Predicate<MqttMessage> interceptor;
        private final UnaryOperator<MqttMessage> rewriter;

        private MqttRelayHandler(Channel targetChannel, Predicate<MqttMessage> interceptor, UnaryOperator<MqttMessage> rewriter) {
            this.targetChannel = targetChannel;
            this.interceptor = interceptor;
            this.rewriter = rewriter;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) {
            log.debug("Received message: {}", msg.fixedHeader().messageType());
            if (interceptor != null && !interceptor.test(msg)) {
                log.info("Dropping message: {}", msg.fixedHeader().messageType());
                return;
            }
            MqttMessage out = rewriter != null ? rewriter.apply(msg) : msg;
            if (out == null) {
                log.info("Withholding message: {}", msg.fixedHeader().messageType());
                return;
            }
            if (targetChannel.isActive()) {
                // the inbound handler releases msg after this call, so relaying msg itself needs a reference of its own
                targetChannel.writeAndFlush(out == msg ? ReferenceCountUtil.retain(msg) : out);
            } else if (out != msg) {
                ReferenceCountUtil.release(out);
            }
        }

    }

    public void stop() {
        if (stopped) {
            log.info("MQTT proxy was already stopped");
            return;
        }

        stopped = true;

        log.info("Stopping MQTT proxy...");

        serverChannel.close().syncUninterruptibly();
        if (clientToProxyChannel != null) {
            clientToProxyChannel.close();
        }
        if (proxyToBrokerChannel != null) {
            proxyToBrokerChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        log.info("MQTT proxy stopped");
    }

    /**
     * Writes {@code msg} to the client, e.g. a message a {@link Builder#brokerToClientRewriter rewriter} withheld
     * earlier. The write takes ownership of {@code msg}.
     */
    public void sendToClient(MqttMessage msg) {
        clientToProxyChannel.writeAndFlush(msg);
    }

    public int getPort() {
        return assignedPort;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private int localPort;
        private String brokerHost;
        private int brokerPort;
        private Predicate<MqttMessage> brokerToClientInterceptor;
        private UnaryOperator<MqttMessage> brokerToClientRewriter;

        public Builder localPort(int localPort) {
            this.localPort = localPort;
            return this;
        }

        public Builder brokerHost(String brokerHost) {
            this.brokerHost = brokerHost;
            return this;
        }

        public Builder brokerPort(int brokerPort) {
            this.brokerPort = brokerPort;
            return this;
        }

        public Builder brokerToClientInterceptor(Predicate<MqttMessage> interceptor) {
            this.brokerToClientInterceptor = interceptor;
            return this;
        }

        /**
         * Rewrites each message relayed from the broker to the client, after the interceptor let it through: return
         * the message itself to relay it unchanged, a new message to relay that instead, or {@code null} to withhold
         * it - {@link MqttTestProxy#sendToClient} can deliver it later. A rewriter that keeps a reference-counted
         * message beyond the call must retain it itself.
         */
        public Builder brokerToClientRewriter(UnaryOperator<MqttMessage> rewriter) {
            this.brokerToClientRewriter = rewriter;
            return this;
        }

        public MqttTestProxy build() {
            return new MqttTestProxy(this);
        }

    }
}
