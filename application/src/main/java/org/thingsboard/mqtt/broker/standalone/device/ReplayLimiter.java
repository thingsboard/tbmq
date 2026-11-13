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
package org.thingsboard.mqtt.broker.standalone.device;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/**
 * Bounds how many persisted DEVICE clients replay their stored backlog at the same time. A permit belongs to a client
 * id: acquiring it again while holding it takes no second permit, and releasing it without holding it does nothing.
 * <p>
 * A holder renews its permit each time its replay goes on. A holder that has not renewed it for the stall timeout,
 * such as a client that stopped acknowledging, loses it to the next client that asks for a permit while none is free;
 * to go on, it must acquire a permit again like any other client.
 */
@Slf4j
@Component
@StandaloneComponent
public class ReplayLimiter {

    private final int maxConcurrentReplays;
    private final long stallTimeoutMs;
    private final Clock clock;
    // the holders, each with the time it last acquired or renewed its permit
    private final Map<String, Long> holders = new HashMap<>();

    @Autowired
    public ReplayLimiter(@Value("${standalone.persist.max-concurrent-replays:16}") int maxConcurrentReplays,
                         @Value("${standalone.persist.replay-stall-timeout-ms:30000}") long stallTimeoutMs) {
        this(maxConcurrentReplays, stallTimeoutMs, Clock.systemUTC());
    }

    ReplayLimiter(int maxConcurrentReplays, long stallTimeoutMs, Clock clock) {
        if (maxConcurrentReplays < 1) {
            throw new IllegalArgumentException("standalone.persist.max-concurrent-replays must be at least 1, but was " + maxConcurrentReplays);
        }
        if (stallTimeoutMs < 1) {
            throw new IllegalArgumentException("standalone.persist.replay-stall-timeout-ms must be at least 1, but was " + stallTimeoutMs);
        }
        this.maxConcurrentReplays = maxConcurrentReplays;
        this.stallTimeoutMs = stallTimeoutMs;
        this.clock = clock;
    }

    /**
     * @return {@code true} when the client holds a permit now
     */
    public synchronized boolean tryAcquire(String clientId) {
        if (holders.containsKey(clientId)) {
            return true;
        }
        long now = clock.millis();
        if (holders.size() >= maxConcurrentReplays && !takeStalledPermit(now)) {
            return false;
        }
        holders.put(clientId, now);
        return true;
    }

    /**
     * Records that the holder's replay goes on.
     *
     * @return {@code false} when the client holds no permit, for example because it lost it to another client while
     * its replay stalled
     */
    public synchronized boolean renew(String clientId) {
        if (!holders.containsKey(clientId)) {
            return false;
        }
        holders.put(clientId, clock.millis());
        return true;
    }

    public synchronized void release(String clientId) {
        holders.remove(clientId);
    }

    /**
     * Frees the permit of the holder that has gone longest without renewing it, if that is at least the stall timeout.
     */
    private boolean takeStalledPermit(long now) {
        Map.Entry<String, Long> stalest = null;
        for (Map.Entry<String, Long> holder : holders.entrySet()) {
            if (stalest == null || holder.getValue() < stalest.getValue()) {
                stalest = holder;
            }
        }
        if (stalest == null || now - stalest.getValue() < stallTimeoutMs) {
            return false;
        }
        log.debug("[{}] Replay made no progress for {} ms, its replay permit goes to a waiting client",
                stalest.getKey(), now - stalest.getValue());
        holders.remove(stalest.getKey());
        return true;
    }

}
