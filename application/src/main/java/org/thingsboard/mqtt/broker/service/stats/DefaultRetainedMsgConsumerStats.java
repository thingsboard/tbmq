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
package org.thingsboard.mqtt.broker.service.stats;

import org.thingsboard.mqtt.broker.common.stats.StatsCounter;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;

import java.util.List;

import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.CLEARED_RETAINED_MSGS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.NEW_RETAINED_MSGS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.TOTAL_RETAINED_MSGS;

public class DefaultRetainedMsgConsumerStats implements RetainedMsgConsumerStats {

    private final List<StatsCounter> counters;

    private final StatsCounter totalRetainedMsgCounter;
    private final StatsCounter newRetainedMsgCounter;
    private final StatsCounter clearedRetainedMsgCounter;

    public DefaultRetainedMsgConsumerStats(StatsFactory statsFactory) {
        String statsKey = StatsType.RETAINED_MSG_CONSUMER.getPrintName();
        this.totalRetainedMsgCounter = statsFactory.createStatsCounter(statsKey, TOTAL_RETAINED_MSGS);
        this.newRetainedMsgCounter = statsFactory.createStatsCounter(statsKey, NEW_RETAINED_MSGS);
        this.clearedRetainedMsgCounter = statsFactory.createStatsCounter(statsKey, CLEARED_RETAINED_MSGS);

        counters = List.of(totalRetainedMsgCounter, newRetainedMsgCounter, clearedRetainedMsgCounter);
    }

    @Override
    public void logTotal(int totalRetainedMsgs) {
        totalRetainedMsgCounter.add(totalRetainedMsgs);
    }

    @Override
    public void log(int newRetainedMsgCount, int clearedRetainedMsgCount) {
        newRetainedMsgCounter.add(newRetainedMsgCount);
        clearedRetainedMsgCounter.add(clearedRetainedMsgCount);
    }

    @Override
    public List<StatsCounter> getStatsCounters() {
        return counters;
    }

    @Override
    public void reset() {
        counters.forEach(StatsCounter::clear);
    }
}
