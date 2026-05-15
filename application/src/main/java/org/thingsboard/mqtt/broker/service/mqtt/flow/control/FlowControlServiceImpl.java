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
    @Value("${mqtt.flow-control.ttl-sweep-interval-ms:10000}")
    private int sweepIntervalMs;
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
        if (sweepIntervalMs <= 0) {
            log.warn("mqtt.flow-control.ttl-sweep-interval-ms={} is invalid; falling back to 10000 ms", sweepIntervalMs);
            sweepIntervalMs = 10000;
        }
        clientsWithDelayedMsgMap = new ConcurrentHashMap<>();
        ttlMs = TimeUnit.SECONDS.toMillis(ttlSecs);
        service = ThingsBoardExecutors.initExecutorService(PARALLELISM, "flow-control-ttl-sweeper");
        launchProcessing();
    }

    void launchProcessing() {
        service.submit(() -> {
            while (!stopped) {
                try {
                    for (Map.Entry<String, PublishedInFlightCtx> entry : clientsWithDelayedMsgMap.entrySet()) {
                        try {
                            entry.getValue().expireTtl(ttlMs);
                        } catch (Exception perCtx) {
                            log.warn("[{}] TTL sweep failed for ctx", entry.getKey(), perCtx);
                        }
                    }
                    Thread.sleep(sweepIntervalMs);
                } catch (InterruptedException ie) {
                    log.info("Flow control TTL sweeper interrupted");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    if (stopped) break;
                    log.error("Flow control TTL sweeper error", e);
                    try {
                        Thread.sleep(sweepIntervalMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
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

    @PreDestroy
    public void destroy() {
        stopped = true;
        if (service != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(service, "Flow control");
        }
    }

}
