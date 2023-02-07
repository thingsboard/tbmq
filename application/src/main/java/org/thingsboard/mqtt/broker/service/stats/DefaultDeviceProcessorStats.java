/**
 * Copyright © 2016-2023 The Thingsboard Authors
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

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.stats.StatsCounter;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;

import java.util.List;

import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.CONSUMER_ID_TAG;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.FAILED_ITERATIONS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.FAILED_MSGS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.SUCCESSFUL_ITERATIONS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.SUCCESSFUL_MSGS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.TMP_FAILED;

@Slf4j
public class DefaultDeviceProcessorStats implements DeviceProcessorStats {
    private final String consumerId;

    private final List<StatsCounter> counters;

    private final StatsCounter successMsgCounter;
    private final StatsCounter tmpFailedMsgCounter;
    private final StatsCounter failedMsgCounter;

    private final StatsCounter successIterationsCounter;
    private final StatsCounter failedIterationsCounter;

    public DefaultDeviceProcessorStats(String consumerId, StatsFactory statsFactory) {
        this.consumerId = consumerId;
        String statsKey = StatsType.DEVICE_PROCESSOR.getPrintName();
        this.successMsgCounter = statsFactory.createStatsCounter(statsKey, SUCCESSFUL_MSGS, CONSUMER_ID_TAG, consumerId);
        this.failedMsgCounter = statsFactory.createStatsCounter(statsKey, FAILED_MSGS, CONSUMER_ID_TAG, consumerId);
        this.tmpFailedMsgCounter = statsFactory.createStatsCounter(statsKey, TMP_FAILED, CONSUMER_ID_TAG, consumerId);
        this.successIterationsCounter = statsFactory.createStatsCounter(statsKey, SUCCESSFUL_ITERATIONS, CONSUMER_ID_TAG, consumerId);
        this.failedIterationsCounter = statsFactory.createStatsCounter(statsKey, FAILED_ITERATIONS, CONSUMER_ID_TAG, consumerId);

        counters = List.of(successMsgCounter, failedMsgCounter, tmpFailedMsgCounter, successIterationsCounter, failedIterationsCounter);
    }

    @Override
    public String getConsumerId() {
        return consumerId;
    }

    @Override
    public void log(int msgsCount, boolean successful, boolean finalIterationForPack) {
        if (finalIterationForPack) {
            if (!successful) {
                failedMsgCounter.add(msgsCount);
                failedIterationsCounter.increment();
            } else {
                successMsgCounter.add(msgsCount);
                successIterationsCounter.increment();
            }
        } else {
            failedIterationsCounter.increment();
            tmpFailedMsgCounter.add(msgsCount);
        }
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
