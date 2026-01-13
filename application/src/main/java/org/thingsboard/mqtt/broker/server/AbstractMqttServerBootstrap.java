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
package org.thingsboard.mqtt.broker.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.concurrent.Future;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.TimeUnit;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.WRITE_BUFFER_DEFAULT_HIGH_WATER_MARK;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.WRITE_BUFFER_DEFAULT_LOW_WATER_MARK;

@Slf4j
public abstract class AbstractMqttServerBootstrap implements MqttServerBootstrap {

    @Value("${listener.leak_detector_level:DISABLED}")
    private String leakDetectorLevel;

    @Value("#{${listener.write_buffer_high_water_mark:64} * 1024}")
    private int writeBufferHighWaterMark;
    @Value("#{${listener.write_buffer_low_water_mark:32} * 1024}")
    private int writeBufferLowWaterMark;
    @Value("#{${listener.so_receive_buffer:0} * 1024}")
    private int soReceiveBuffer;

    @Getter
    private Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public void init() throws Exception {
        log.info("[{}] Setting global resource leak detector level to {}", getServerName(), leakDetectorLevel);
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.valueOf(leakDetectorLevel.toUpperCase()));

        log.info("[{}] Starting MQTT server...", getServerName());
        bossGroup = new NioEventLoopGroup(getBossGroupThreadCount());
        workerGroup = new NioEventLoopGroup(getWorkerGroupThreadCount());
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(getChannelInitializer())
                .childOption(ChannelOption.SO_KEEPALIVE, isKeepAlive())
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, getWriteBufferWaterMark());
        if (soReceiveBuffer > 0) {
            b.childOption(ChannelOption.SO_RCVBUF, soReceiveBuffer);
        }

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

    private WriteBufferWaterMark getWriteBufferWaterMark() {
        if (writeBufferLowWaterMark == WRITE_BUFFER_DEFAULT_LOW_WATER_MARK && writeBufferHighWaterMark == WRITE_BUFFER_DEFAULT_HIGH_WATER_MARK) {
            return WriteBufferWaterMark.DEFAULT;
        }
        return new WriteBufferWaterMark(writeBufferLowWaterMark, writeBufferHighWaterMark);
    }
}
