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
package org.thingsboard.mqtt.broker.actors.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thingsboard.mqtt.broker.actors.DefaultTbActorSystem;
import org.thingsboard.mqtt.broker.actors.TbActorSystem;
import org.thingsboard.mqtt.broker.actors.TbActorSystemSettings;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;

import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Configuration
public class ActorsConfiguration {
    public static final String PERSISTED_DEVICE_DISPATCHER_NAME = "persisted-device-dispatcher";

    @Value("${actors.system.throughput:5}")
    private int actorThroughput;
    @Value("${actors.system.max-actor-init-attempts:10}")
    private int maxActorInitAttempts;
    @Value("${actors.system.scheduler-pool-size:1}")
    private int schedulerPoolSize;

    @Value("${actors.system.persisted-device-dispatcher-pool-size:4}")
    private int persistedDeviceDispatcherSize;

    @Bean
    public TbActorSystemSettings actorSystemSettings() {
        return new TbActorSystemSettings(actorThroughput, schedulerPoolSize, maxActorInitAttempts);
    }

    @Bean
    public TbActorSystem actorSystem(TbActorSystemSettings actorSystemSettings) {
        TbActorSystem system = new DefaultTbActorSystem(actorSystemSettings);
        system.createDispatcher(PERSISTED_DEVICE_DISPATCHER_NAME, initDispatcherExecutor(PERSISTED_DEVICE_DISPATCHER_NAME, persistedDeviceDispatcherSize));
        return system;
    }

    @Autowired
    private TbActorSystem actorSystem;

    @PreDestroy
    public void stopActorSystem() {
        log.info("Stopping actor system.");
        actorSystem.stop();
        log.info("Actor system stopped.");
    }

    private ExecutorService initDispatcherExecutor(String dispatcherName, int poolSize) {
        if (poolSize == 0) {
            int cores = Runtime.getRuntime().availableProcessors();
            poolSize = Math.max(1, cores / 2);
        }
        if (poolSize == 1) {
            return Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName(dispatcherName));
        } else {
            return Executors.newWorkStealingPool(poolSize);
        }
    }
}
