/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "listener.ssl", value = "enabled", havingValue = "true", matchIfMissing = false)
public class MqttSslServerBootstrap {
    @Value("${listener.ssl.bind_address}")
    private String host;
    @Value("${listener.ssl.bind_port}")
    private Integer port;

    @Value("${listener.ssl.netty.leak_detector_level}")
    private String leakDetectorLevel;
    @Value("${listener.ssl.netty.boss_group_thread_count}")
    private Integer bossGroupThreadCount;
    @Value("${listener.ssl.netty.worker_group_thread_count}")
    private Integer workerGroupThreadCount;
    @Value("${listener.ssl.netty.so_keep_alive}")
    private boolean keepAlive;

    @Value("${listener.ssl.netty.shutdown_quiet_period:0}")
    private Integer shutdownQuietPeriod;
    @Value("${listener.ssl.netty.shutdown_timeout:5}")
    private Integer shutdownTimeout;

    private final MqttSslChannelInitializer mqttSslChannelInitializer;

    private Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    @EventListener(ApplicationReadyEvent.class)
    @Order(value = 101)
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) throws Exception {
        log.info("[SSL Server] Setting resource leak detector level to {}", leakDetectorLevel);
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.valueOf(leakDetectorLevel.toUpperCase()));

        log.info("[SSL Server] Starting MQTT server...");
        bossGroup = new NioEventLoopGroup(bossGroupThreadCount);
        workerGroup = new NioEventLoopGroup(workerGroupThreadCount);
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(mqttSslChannelInitializer)
                .childOption(ChannelOption.SO_KEEPALIVE, keepAlive);

        serverChannel = b.bind(host, port).sync().channel();
        log.info("[SSL Server] Mqtt server started!");
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        log.info("[SSL Server] Stopping MQTT server!");

        Future<?> bossFuture = null;
        Future<?> workerFuture = null;

        if (serverChannel != null) {
            serverChannel.close().sync();
        }

        if (bossGroup != null) {
            bossFuture = bossGroup.shutdownGracefully(shutdownQuietPeriod, shutdownTimeout, TimeUnit.SECONDS);
        }
        if (workerGroup != null) {
            workerFuture = workerGroup.shutdownGracefully(shutdownQuietPeriod, shutdownTimeout, TimeUnit.SECONDS);
        }

        log.info("[SSL Server] Awaiting shutdown gracefully boss and worker groups...");

        if (bossFuture != null) {
            bossFuture.sync();
        }
        if (workerFuture != null) {
            workerFuture.sync();
        }

        log.info("[SSL Server] MQTT server stopped!");
    }
}
