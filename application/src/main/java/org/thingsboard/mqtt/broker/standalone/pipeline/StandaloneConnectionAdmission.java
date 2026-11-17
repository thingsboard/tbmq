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
package org.thingsboard.mqtt.broker.standalone.pipeline;

import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.client.service.connect.ConnectionAdmission;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Refuses new connections once the shutdown starts, with Server unavailable: a client accepted after the shutdown took
 * its sessions would be neither paused nor disconnected by it, and its Last Will would be lost. Tracks the CONNECTs it
 * admitted until each ends, so that the shutdown waits for them before it takes the sessions.
 */
@Component
@StandaloneComponent
public class StandaloneConnectionAdmission implements ConnectionAdmission {

    // a CONNECT holds the read lock from the check to being tracked, and close() takes the write lock: once close()
    // returns, every admitted CONNECT is tracked
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Set<UUID> admitted = ConcurrentHashMap.newKeySet();
    private boolean closed;

    @Override
    public boolean tryAdmit(UUID sessionId) {
        lock.readLock().lock();
        try {
            if (closed) {
                return false;
            }
            admitted.add(sessionId);
            return true;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void release(UUID sessionId) {
        admitted.remove(sessionId);
    }

    /**
     * Refuses every CONNECT from now on. Once it returns, {@link #inFlight()} counts every CONNECT admitted before.
     */
    public void close() {
        lock.writeLock().lock();
        try {
            closed = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * @return the number of admitted CONNECTs that have not ended yet
     */
    public int inFlight() {
        return admitted.size();
    }

}
