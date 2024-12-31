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
package org.thingsboard.mqtt.broker.actors.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.TbActorSystem;
import org.thingsboard.mqtt.broker.actors.client.ClientActorConfiguration;
import org.thingsboard.mqtt.broker.actors.device.DeviceActorConfiguration;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActorSystemLifecycle {

    public static final String PERSISTED_DEVICE_DISPATCHER_NAME = "persisted-device-dispatcher";
    public static final String CLIENT_DISPATCHER_NAME = "client-dispatcher";

    private final TbActorSystem actorSystem;
    private final ClientSessionCtxService clientSessionCtxService;
    private final DeviceActorConfiguration deviceActorConfiguration;
    private final ClientActorConfiguration clientActorConfiguration;
    private final ClientSessionEventService clientSessionEventService;

    @Value("${actors.system.disconnect-wait-timeout-ms:2000}")
    private long waitTimeoutMs;

    @PostConstruct
    public void init() {
        actorSystem.createDispatcher(PERSISTED_DEVICE_DISPATCHER_NAME, initDispatcherExecutor(PERSISTED_DEVICE_DISPATCHER_NAME, deviceActorConfiguration.getDispatcherPoolSize()));
        actorSystem.createDispatcher(CLIENT_DISPATCHER_NAME, initDispatcherExecutor(CLIENT_DISPATCHER_NAME, clientActorConfiguration.getDispatcherPoolSize()));
    }

    @PreDestroy
    public void destroy() throws InterruptedException {
        notifyAboutDisconnectedClients();
    }

    private void notifyAboutDisconnectedClients() {
        if (log.isTraceEnabled()) {
            log.trace("Process notify about disconnected clients.");
        }
        Collection<ClientSessionCtx> clientSessionContexts = clientSessionCtxService.getAllClientSessionCtx();
        if (clientSessionContexts.isEmpty()) {
            if (log.isTraceEnabled()) {
                log.trace("No client sessions left to notify about disconnect.");
            }
            return;
        }

        CountDownLatch latch = new CountDownLatch(clientSessionContexts.size());
        TbQueueCallback callback = new DisconnectCallback(latch);

        log.info("Trying to send DISCONNECTED event for {} client contexts.", clientSessionContexts.size());
        for (ClientSessionCtx sessionCtx : clientSessionContexts) {
            clientSessionEventService.notifyClientDisconnected(sessionCtx.getSessionInfo().getClientInfo(), sessionCtx.getSessionId(), callback);
        }

        boolean waitSuccessful = false;
        try {
            waitSuccessful = latch.await(waitTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            log.warn("Disconnect notifications processing was interrupted");
        }
        if (!waitSuccessful) {
            log.warn("Failed to process [{}] disconnect notifications in time", latch.getCount());
        }
    }

    private ExecutorService initDispatcherExecutor(String dispatcherName, int poolSize) {
        if (poolSize == 0) {
            int cores = Runtime.getRuntime().availableProcessors();
            poolSize = Math.max(1, cores / 2);
        }
        if (poolSize == 1) {
            return Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName(dispatcherName));
        } else {
            return ThingsBoardExecutors.newWorkStealingPool(poolSize, dispatcherName);
        }
    }

    @AllArgsConstructor
    private static class DisconnectCallback implements TbQueueCallback {

        private final CountDownLatch countDownLatch;

        @Override
        public void onSuccess(TbQueueMsgMetadata metadata) {
            if (log.isTraceEnabled()) {
                log.trace("Disconnect request sent successfully: {}", metadata);
            }
            countDownLatch.countDown();
        }

        @Override
        public void onFailure(Throwable t) {
            log.warn("Failed to send disconnect request. Reason - {}.", t.getMessage());
            countDownLatch.countDown();
        }
    }
}
