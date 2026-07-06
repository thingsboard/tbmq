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

import java.util.List;

import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.ACCEPTED_RECORDS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.IGNORED_RECORDS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.TOTAL_RECORDS;

public class DefaultClientSubscriptionConsumerStats implements ClientSubscriptionConsumerStats {
    private final List<StatsCounter> counters;

    private final StatsCounter totalRecordCounter;
    private final StatsCounter acceptedRecordCounter;
    private final StatsCounter ignoredRecordCounter;

    public DefaultClientSubscriptionConsumerStats(StatsFactory statsFactory) {
        String statsKey = StatsType.CLIENT_SUBSCRIPTIONS_CONSUMER.getPrintName();
        this.totalRecordCounter = statsFactory.createStatsCounter(statsKey, TOTAL_RECORDS);
        this.acceptedRecordCounter = statsFactory.createStatsCounter(statsKey, ACCEPTED_RECORDS);
        this.ignoredRecordCounter = statsFactory.createStatsCounter(statsKey, IGNORED_RECORDS);

        counters = List.of(totalRecordCounter, acceptedRecordCounter, ignoredRecordCounter);
    }

    @Override
    public void logTotal(int totalRecords) {
        totalRecordCounter.add(totalRecords);
    }

    @Override
    public void log(int acceptedRecords, int ignoredRecords) {
        acceptedRecordCounter.add(acceptedRecords);
        ignoredRecordCounter.add(ignoredRecords);
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
