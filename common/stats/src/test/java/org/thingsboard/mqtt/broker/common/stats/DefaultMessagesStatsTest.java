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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMessagesStatsTest {

    private final StatsFactory statsFactory = new DefaultStatsFactory(new SimpleMeterRegistry());

    @Test
    void givenNoQueueSizeSupplierWired_whenIsQueueSizeTracked_thenFalseAndSizeZero() {
        MessagesStats stats = statsFactory.createMessagesStats("producer");

        // A producer-style MessagesStats never wires a queue-size supplier, so its queueSize log
        // field is a dead always-0 value that callers should omit.
        assertThat(stats.isQueueSizeTracked()).isFalse();
        assertThat(stats.getCurrentQueueSize()).isZero();
    }

    @Test
    void givenQueueSizeSupplierWired_whenIsQueueSizeTracked_thenTrueAndReflectsSupplier() {
        MessagesStats stats = statsFactory.createMessagesStats("sqlQueue.Events");

        stats.updateQueueSize(() -> 5);

        assertThat(stats.isQueueSizeTracked()).isTrue();
        assertThat(stats.getCurrentQueueSize()).isEqualTo(5);
    }

    @Test
    void givenNoQueueSizeSupplier_whenToLogString_thenOmitsQueueSizeButKeepsCounters() {
        MessagesStats stats = statsFactory.createMessagesStats("producer");
        stats.incrementTotal(10);
        stats.incrementSuccessful(7);
        stats.incrementFailed(3);

        String line = stats.toLogString();

        assertThat(line).doesNotContain("queueSize");
        assertThat(line).contains("totalMsgs = [10]");
        assertThat(line).contains("successfulMsgs = [7]");
        assertThat(line).contains("failedMsgs = [3]");
    }

    @Test
    void givenQueueSizeSupplierWired_whenToLogString_thenIncludesQueueSize() {
        MessagesStats stats = statsFactory.createMessagesStats("sqlQueue.Events");
        stats.updateQueueSize(() -> 5);

        String line = stats.toLogString();

        assertThat(line).contains("queueSize = [5]");
        assertThat(line).contains("totalMsgs = [0]");
    }
}
