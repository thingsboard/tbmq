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
package org.thingsboard.mqtt.broker.service.trace;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class ClientTraceStream {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2,
            Thread.ofPlatform().name("client-trace-stream-", 0).daemon().factory());

    @Value("${client-trace.stream.poll-interval:1000}")
    private long pollInterval;

    public SseEmitter subscribe(String clientId, ClickHouseClientTraceStore store) {
        SseEmitter emitter = new SseEmitter(0L);
        AtomicLong cursor = new AtomicLong(System.currentTimeMillis());
        Set<String> delivered = Collections.synchronizedSet(new LinkedHashSet<>());
        AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();
        Runnable cleanup = () -> {
            ScheduledFuture<?> task = future.get();
            if (task != null) {
                task.cancel(false);
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        future.set(scheduler.scheduleWithFixedDelay(
                () -> poll(clientId, store, emitter, cursor, delivered), 0, pollInterval, TimeUnit.MILLISECONDS));
        return emitter;
    }

    private void poll(String clientId, ClickHouseClientTraceStore store, SseEmitter emitter,
                      AtomicLong cursor, Set<String> delivered) {
        try {
            long to = System.currentTimeMillis();
            List<JsonNode> events = store.findSince(clientId, cursor.get(), to, 1000);
            for (JsonNode event : events) {
                long timestamp = event.path("ts_ms").asLong();
                String key = event.path("event_id").asText() + ':' + timestamp;
                if (delivered.add(key)) {
                    emitter.send(SseEmitter.event().name("trace").id(key).data(event.toString()));
                }
                cursor.accumulateAndGet(timestamp, Math::max);
            }
            trimDelivered(delivered);
            emitter.send(SseEmitter.event().comment("keepalive"));
        } catch (IOException e) {
            emitter.complete();
        } catch (Exception e) {
            log.warn("Failed to poll client trace events for {}", clientId, e);
        }
    }

    private void trimDelivered(Set<String> delivered) {
        synchronized (delivered) {
            while (delivered.size() > 2000) {
                delivered.remove(delivered.iterator().next());
            }
        }
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }
}
