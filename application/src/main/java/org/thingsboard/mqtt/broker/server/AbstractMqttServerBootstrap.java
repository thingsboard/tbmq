/**
 * Copyright © 2016-2025 The Thingsboard Authors
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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class AbstractMqttServerBootstrap implements MqttServerBootstrap {

    private Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public void init() throws Exception {
        log.info("[{}] Setting resource leak detector level to {}", getServerName(), getLeakDetectorLevel());
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.valueOf(getLeakDetectorLevel().toUpperCase()));

        log.info("[{}] Starting MQTT server...", getServerName());
        bossGroup = new NioEventLoopGroup(getBossGroupThreadCount());
        workerGroup = new NioEventLoopGroup(getWorkerGroupThreadCount());
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(getChannelInitializer())
                .childOption(ChannelOption.SO_KEEPALIVE, isKeepAlive());

        serverChannel = b.bind(getHost(), getPort()).sync().channel();
        log.info("[{}] Mqtt server started on port {}!", getServerName(), getPort());
    }

    public void shutdown() throws InterruptedException {
        log.info("[{}] Stopping MQTT server!", getServerName());

        Future<?> bossFuture = null;
        Future<?> workerFuture = null;

        if (serverChannel != null) {
            serverChannel.close().sync();
        }

        if (bossGroup != null) {
            bossFuture = bossGroup.shutdownGracefully(getShutdownQuietPeriod(), getShutdownTimeout(), TimeUnit.SECONDS);
        }
        if (workerGroup != null) {
            workerFuture = workerGroup.shutdownGracefully(getShutdownQuietPeriod(), getShutdownTimeout(), TimeUnit.SECONDS);
        }

        log.info("[{}] Awaiting shutdown gracefully boss and worker groups...", getServerName());

        if (bossFuture != null) {
            bossFuture.sync();
        }
        if (workerFuture != null) {
            workerFuture.sync();
        }

        log.info("[{}] MQTT server stopped!", getServerName());
    }

}
