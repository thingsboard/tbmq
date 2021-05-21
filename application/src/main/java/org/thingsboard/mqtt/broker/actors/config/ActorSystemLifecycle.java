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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.TbActorSystem;
import org.thingsboard.mqtt.broker.actors.device.DeviceActorConfiguration;
import org.thingsboard.mqtt.broker.actors.session.ClientSessionActorConfiguration;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActorSystemLifecycle {
    public static final String PERSISTED_DEVICE_DISPATCHER_NAME = "persisted-device-dispatcher";
    public static final String CLIENT_SESSION_DISPATCHER_NAME = "client-session-dispatcher";

    private final TbActorSystem actorSystem;
    private final DeviceActorConfiguration deviceActorConfiguration;
    private final ClientSessionActorConfiguration clientSessionActorConfiguration;

    @PostConstruct
    public void init() {
        actorSystem.createDispatcher(PERSISTED_DEVICE_DISPATCHER_NAME, initDispatcherExecutor(PERSISTED_DEVICE_DISPATCHER_NAME, deviceActorConfiguration.getDispatcherSize()));
        actorSystem.createDispatcher(CLIENT_SESSION_DISPATCHER_NAME, initDispatcherExecutor(CLIENT_SESSION_DISPATCHER_NAME, clientSessionActorConfiguration.getDispatcherSize()));
    }

    @PreDestroy
    public void destroy() {
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
