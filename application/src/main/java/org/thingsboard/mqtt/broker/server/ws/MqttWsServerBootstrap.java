/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.server.ws;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.server.AbstractMqttChannelInitializer;
import org.thingsboard.mqtt.broker.server.AbstractMqttServerBootstrap;

import javax.annotation.PreDestroy;

@Service
@RequiredArgsConstructor
@Getter
@Slf4j
@ConditionalOnProperty(prefix = "listener.ws", value = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttWsServerBootstrap extends AbstractMqttServerBootstrap {

    @Value("${listener.ws.bind_address}")
    private String host;
    @Value("${listener.ws.bind_port}")
    private int port;

    @Value("${listener.ws.netty.leak_detector_level}")
    private String leakDetectorLevel;
    @Value("${listener.ws.netty.boss_group_thread_count}")
    private int bossGroupThreadCount;
    @Value("${listener.ws.netty.worker_group_thread_count}")
    private int workerGroupThreadCount;
    @Value("${listener.ws.netty.so_keep_alive}")
    private boolean keepAlive;

    @Value("${listener.ws.netty.shutdown_quiet_period:0}")
    private int shutdownQuietPeriod;
    @Value("${listener.ws.netty.shutdown_timeout:5}")
    private int shutdownTimeout;

    private final MqttWsChannelInitializer mqttWsChannelInitializer;

    @EventListener(ApplicationReadyEvent.class)
    @Order(value = 102)
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) throws Exception {
        super.init();
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        super.shutdown();
    }

    @Override
    public AbstractMqttChannelInitializer getChannelInitializer() {
        return mqttWsChannelInitializer;
    }

    @Override
    public String getServerName() {
        return "WS Server";
    }
}
