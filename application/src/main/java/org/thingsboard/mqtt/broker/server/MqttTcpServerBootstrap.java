/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.ResourceLeakDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Service
@Slf4j
public class MqttTcpServerBootstrap {
    @Value("${server.mqtt.tcp.bind_address}")
    private String host;
    @Value("${server.mqtt.tcp.bind_port}")
    private Integer port;

    @Value("${server.mqtt.tcp.netty.leak_detector_level}")
    private String leakDetectorLevel;
    @Value("${server.mqtt.tcp.netty.boss_group_thread_count}")
    private Integer bossGroupThreadCount;
    @Value("${server.mqtt.tcp.netty.worker_group_thread_count}")
    private Integer workerGroupThreadCount;

    @Autowired
    private MqttTcpChannelInitializer mqttTcpChannelInitializer;

    private Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    @PostConstruct
    public void init() throws Exception {
        log.info("[TCP Server] Setting resource leak detector level to {}", leakDetectorLevel);
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.valueOf(leakDetectorLevel.toUpperCase()));

        log.info("[TCP Server] Starting MQTT server...");
        bossGroup = new NioEventLoopGroup(bossGroupThreadCount);
        workerGroup = new NioEventLoopGroup(workerGroupThreadCount);
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(mqttTcpChannelInitializer)
        ;

        serverChannel = b.bind(host, port).sync().channel();
        log.info("[TCP Server] Mqtt server started!");
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        log.info("[TCP Server] Stopping MQTT server!");
        try {
            serverChannel.close().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
        log.info("[TCP Server] MQTT server stopped!");
    }
}
