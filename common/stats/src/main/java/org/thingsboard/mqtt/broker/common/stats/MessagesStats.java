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
package org.thingsboard.mqtt.broker.common.stats;

import java.util.function.Supplier;

public interface MessagesStats {

    String getName();

    default void incrementTotal() {
        incrementTotal(1);
    }

    void incrementTotal(int amount);

    default void incrementSuccessful() {
        incrementSuccessful(1);
    }

    void incrementSuccessful(int amount);

    default void incrementFailed() {
        incrementFailed(1);
    }

    void incrementFailed(int amount);

    int getTotal();

    int getSuccessful();

    int getFailed();

    void reset();

    void updateQueueSize(Supplier<Integer> queueSizeSupplier);

    int getCurrentQueueSize();

    /**
     * Whether a queue-size supplier has been wired via {@link #updateQueueSize(Supplier)}. Only stats
     * backed by a real queue (e.g. the SQL blocking queues) report a meaningful depth; producer-style
     * stats never wire a supplier and would otherwise print a dead always-0 {@code queueSize} log field.
     * Consumed by {@link MessagesStatsLog} when formatting the periodic log line.
     */
    boolean isQueueSizeTracked();
}
