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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;

import java.util.HashSet;
import java.util.Set;

/**
 * Bounds how many persisted DEVICE clients replay their stored backlog at the same time. A permit belongs to a client
 * id: acquiring it again while holding it takes no second permit, and releasing it without holding it does nothing.
 */
@Component
@StandaloneComponent
public class ReplayLimiter {

    private final int maxConcurrentReplays;
    private final Set<String> holders = new HashSet<>();

    public ReplayLimiter(@Value("${standalone.persist.max-concurrent-replays:16}") int maxConcurrentReplays) {
        if (maxConcurrentReplays < 1) {
            throw new IllegalArgumentException("standalone.persist.max-concurrent-replays must be at least 1, but was " + maxConcurrentReplays);
        }
        this.maxConcurrentReplays = maxConcurrentReplays;
    }

    /**
     * @return {@code true} when the client holds a permit now
     */
    public synchronized boolean tryAcquire(String clientId) {
        if (holders.contains(clientId)) {
            return true;
        }
        if (holders.size() >= maxConcurrentReplays) {
            return false;
        }
        holders.add(clientId);
        return true;
    }

    public synchronized void release(String clientId) {
        holders.remove(clientId);
    }

}
