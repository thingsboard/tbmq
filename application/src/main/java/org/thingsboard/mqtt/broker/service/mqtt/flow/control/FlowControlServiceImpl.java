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
package org.thingsboard.mqtt.broker.service.mqtt.flow.control;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.state.PublishedInFlightCtx;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class FlowControlServiceImpl implements FlowControlService {

    private static final int PARALLELISM = 1;

    @Setter
    @Value("${mqtt.flow-control.enabled:true}")
    private boolean flowControlEnabled;
    @Setter
    @Value("${mqtt.flow-control.timeout:1000}")
    private int timeout;
    @Value("${mqtt.flow-control.ttl:600}")
    private int ttlSecs;

    @Getter
    @Setter
    private ConcurrentMap<String, PublishedInFlightCtx> clientsWithDelayedMsgMap;
    @Setter
    private ExecutorService service;
    private long ttlMs;
    private volatile boolean stopped = false;

    @PostConstruct
    public void init() {
        if (!flowControlEnabled) {
            return;
        }
        clientsWithDelayedMsgMap = new ConcurrentHashMap<>();
        ttlMs = TimeUnit.SECONDS.toMillis(ttlSecs);
        service = ThingsBoardExecutors.initExecutorService(PARALLELISM, "flow-control-executor");
        launchProcessing();
    }

    void launchProcessing() {
        service.submit(() -> {
            while (!stopped) {
                try {
                    if (clientsWithDelayedMsgMap.isEmpty()) {
                        sleep();
                        continue;
                    }
                    boolean atLeastOneMsgProcessed = false;
                    for (Map.Entry<String, PublishedInFlightCtx> entry : clientsWithDelayedMsgMap.entrySet()) {
                        boolean isMessageProcessed = entry.getValue().processMsg(ttlMs);
                        if (isMessageProcessed) {
                            atLeastOneMsgProcessed = true;
                        }
                    }
                    if (!atLeastOneMsgProcessed) {
                        sleep();
                    }
                } catch (Exception e) {
                    if (e instanceof InterruptedException) {
                        log.info("Flow control processing was interrupted");
                        break;
                    }
                    if (!stopped) {
                        log.error("Failed to process msg", e);
                        try {
                            sleep();
                        } catch (InterruptedException e2) {
                            log.info("Thread was interrupted!");
                            break;
                        }
                    }
                }
            }
        });
    }

    @Override
    public void addToMap(String clientId, PublishedInFlightCtx ctx) {
        if (flowControlEnabled && clientId != null && ctx != null) {
            clientsWithDelayedMsgMap.put(clientId, ctx);
        }
    }

    @Override
    public void removeFromMap(String clientId) {
        if (flowControlEnabled && clientId != null) {
            clientsWithDelayedMsgMap.remove(clientId);
        }
    }

    void sleep() throws InterruptedException {
        Thread.sleep(timeout);
    }

    @PreDestroy
    public void destroy() {
        stopped = true;
        if (service != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(service, "Flow control");
        }
    }

}
