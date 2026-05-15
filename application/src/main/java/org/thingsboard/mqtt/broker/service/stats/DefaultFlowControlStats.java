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
package org.thingsboard.mqtt.broker.service.stats;

import org.thingsboard.mqtt.broker.common.stats.StatsCounter;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;

import java.util.concurrent.atomic.AtomicInteger;

public class DefaultFlowControlStats implements FlowControlStats {

    private static final String STATS_KEY = StatsType.FLOW_CONTROL.getPrintName();

    private final StatsCounter dropOverflowCounter;
    private final StatsCounter dropTtlCounter;
    private final StatsCounter unknownAckCounter;

    // Broker-wide aggregate gauges (no clientId tag — cardinality concern at 100M+ connections).
    private final AtomicInteger inflightGauge;
    private final AtomicInteger delayedGauge;

    public DefaultFlowControlStats(StatsFactory statsFactory) {
        this.dropOverflowCounter = statsFactory.createStatsCounter(STATS_KEY, "dropsOverflow");
        this.dropTtlCounter = statsFactory.createStatsCounter(STATS_KEY, "dropsTtl");
        this.unknownAckCounter = statsFactory.createStatsCounter(STATS_KEY, "unknownAck");
        this.inflightGauge = statsFactory.createGauge(STATS_KEY + ".inflightCount", new AtomicInteger(0));
        this.delayedGauge = statsFactory.createGauge(STATS_KEY + ".delayedQueueSize", new AtomicInteger(0));
    }

    @Override
    public void incDropOverflow() {
        dropOverflowCounter.increment();
    }

    @Override
    public void incDropTtl() {
        dropTtlCounter.increment();
    }

    @Override
    public void incDropTtl(int n) {
        dropTtlCounter.add(n);
    }

    @Override
    public void incUnknownAck() {
        unknownAckCounter.increment();
    }

    @Override
    public void incInflight() {
        inflightGauge.incrementAndGet();
    }

    @Override
    public void decInflight() {
        inflightGauge.decrementAndGet();
    }

    @Override
    public void decInflight(int n) {
        inflightGauge.addAndGet(-n);
    }

    @Override
    public void incDelayed() {
        delayedGauge.incrementAndGet();
    }

    @Override
    public void decDelayed() {
        delayedGauge.decrementAndGet();
    }

    @Override
    public void decDelayed(int n) {
        delayedGauge.addAndGet(-n);
    }

    public int getDropOverflow() {
        return dropOverflowCounter.get();
    }

    public int getDropTtl() {
        return dropTtlCounter.get();
    }

    public int getUnknownAck() {
        return unknownAckCounter.get();
    }

    public int getInflightCount() {
        return inflightGauge.get();
    }

    public int getDelayedQueueSize() {
        return delayedGauge.get();
    }
}
