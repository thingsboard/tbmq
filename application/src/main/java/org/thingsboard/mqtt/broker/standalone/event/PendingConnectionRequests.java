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
package org.thingsboard.mqtt.broker.standalone.event;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ConnectionResponse;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The connection requests that wait for the answer of the client actor.
 * A request nobody answers in {@code max-request-timeout} ms fails with a {@link TimeoutException}.
 */
@Slf4j
@Component
@StandaloneComponent
public class PendingConnectionRequests {

    private final ConcurrentMap<UUID, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

    private final long maxPendingRequests;
    private final long maxRequestTimeoutMs;
    private final long cleanupIntervalMs;

    private ScheduledExecutorService cleanupScheduler;

    public PendingConnectionRequests(
            @Value("${queue.client-session-event.max-pending-requests}") long maxPendingRequests,
            @Value("${queue.client-session-event-response.max-request-timeout}") long maxRequestTimeoutMs,
            @Value("${queue.client-session-event-response.cleanup-interval}") long cleanupIntervalMs) {
        this.maxPendingRequests = maxPendingRequests;
        this.maxRequestTimeoutMs = maxRequestTimeoutMs;
        this.cleanupIntervalMs = cleanupIntervalMs;
    }

    @PostConstruct
    public void init() {
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(ThingsBoardThreadFactory.forName("client-session-event-response-clean-up-scheduler"));
        cleanupScheduler.scheduleWithFixedDelay(this::cleanup, cleanupIntervalMs, cleanupIntervalMs, TimeUnit.MILLISECONDS);
    }

    public ListenableFuture<ConnectionResponse> register(UUID requestId) {
        if (pendingRequests.size() >= maxPendingRequests) {
            return Futures.immediateFailedFuture(new RuntimeException("Cannot send CONNECTION_REQUEST. Pending request map is full!"));
        }
        SettableFuture<ConnectionResponse> future = SettableFuture.create();
        pendingRequests.put(requestId, new PendingRequest(System.currentTimeMillis() + maxRequestTimeoutMs, future));
        return future;
    }

    public void complete(UUID requestId, ConnectionResponse response) {
        PendingRequest request = pendingRequests.remove(requestId);
        if (request == null) {
            log.debug("[{}] Invalid or stale request.", requestId);
            return;
        }
        request.future().set(response);
    }

    public void fail(UUID requestId, Throwable t) {
        PendingRequest request = pendingRequests.remove(requestId);
        if (request == null) {
            log.debug("[{}] Invalid or stale request.", requestId);
            return;
        }
        request.future().setException(t);
    }

    int size() {
        return pendingRequests.size();
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        pendingRequests.forEach((requestId, request) -> {
            if (request.expTime() < now && pendingRequests.remove(requestId, request)) {
                log.debug("[{}] Request timeout detected, expTime [{}], now [{}]", requestId, request.expTime(), now);
                request.future().setException(new TimeoutException());
            }
        });
    }

    @PreDestroy
    public void destroy() {
        if (cleanupScheduler != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(cleanupScheduler, "Pending connection requests cleanup");
        }
    }

    private record PendingRequest(long expTime, SettableFuture<ConnectionResponse> future) {
    }

}
