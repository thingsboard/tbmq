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

import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.ACCEPTED_SUBSCRIPTIONS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.IGNORED_SUBSCRIPTIONS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.TOTAL_SUBSCRIPTIONS;

public class DefaultClientSubscriptionConsumerStats implements ClientSubscriptionConsumerStats {
    private final List<StatsCounter> counters;

    private final StatsCounter totalSubscriptionCounter;
    private final StatsCounter acceptedSubscriptionCounter;
    private final StatsCounter ignoredSubscriptionCounter;

    public DefaultClientSubscriptionConsumerStats(StatsFactory statsFactory) {
        String statsKey = StatsType.CLIENT_SUBSCRIPTIONS_CONSUMER.getPrintName();
        this.totalSubscriptionCounter = statsFactory.createStatsCounter(statsKey, TOTAL_SUBSCRIPTIONS);
        this.acceptedSubscriptionCounter = statsFactory.createStatsCounter(statsKey, ACCEPTED_SUBSCRIPTIONS);
        this.ignoredSubscriptionCounter = statsFactory.createStatsCounter(statsKey, IGNORED_SUBSCRIPTIONS);

        counters = List.of(totalSubscriptionCounter, acceptedSubscriptionCounter, ignoredSubscriptionCounter);
    }

    @Override
    public void logTotal(int totalSubscriptions) {
        totalSubscriptionCounter.add(totalSubscriptions);
    }

    @Override
    public void log(int acceptedSubscriptions, int ignoredSubscriptions) {
        acceptedSubscriptionCounter.add(acceptedSubscriptions);
        ignoredSubscriptionCounter.add(ignoredSubscriptions);
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
