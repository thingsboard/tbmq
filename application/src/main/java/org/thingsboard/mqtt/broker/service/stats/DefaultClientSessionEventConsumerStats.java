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

import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.stats.ResettableTimer;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class DefaultClientSessionEventConsumerStats implements ClientSessionEventConsumerStats {

    private static final String CONSUMER_ID_TAG = "consumerId";

    private final String consumerId;
    private final ResettableTimer packProcessingTimer;
    private final AtomicLong totalPackSize = new AtomicLong();

    public DefaultClientSessionEventConsumerStats(String consumerId, StatsFactory statsFactory) {
        this.consumerId = consumerId;
        String statsKey = StatsType.CLIENT_SESSION_EVENT_CONSUMER.getPrintName();
        Timer timer = statsFactory.createTimer(statsKey + ".pack.processing.time", CONSUMER_ID_TAG, consumerId);
        this.packProcessingTimer = new ResettableTimer(timer);
    }

    @Override
    public String getConsumerId() {
        return consumerId;
    }

    @Override
    public void logPackProcessingTime(int packSize, long amount, TimeUnit unit) {
        packProcessingTimer.logTime(amount, unit);
        totalPackSize.addAndGet(packSize);
    }

    @Override
    public double getAvgPackProcessingTime() {
        return packProcessingTimer.getAvg();
    }

    @Override
    public double getAvgPackSize() {
        return Math.ceil((double) totalPackSize.get() / packProcessingTimer.getCount());
    }

    @Override
    public void reset() {
        packProcessingTimer.reset();
        totalPackSize.getAndSet(0);
    }
}
