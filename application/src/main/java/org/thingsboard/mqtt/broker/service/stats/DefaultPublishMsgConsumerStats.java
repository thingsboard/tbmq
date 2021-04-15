/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.service.processing.PackProcessingResult;

import java.util.ArrayList;
import java.util.List;

import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.FAILED_ITERATIONS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.FAILED_MSGS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.SUCCESSFUL_ITERATIONS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.SUCCESSFUL_MSGS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.TIMEOUT_MSGS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.TMP_FAILED;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.TMP_TIMEOUT;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.TOTAL_MSGS;

@Slf4j
public class DefaultPublishMsgConsumerStats implements PublishMsgConsumerStats {
    private static final String CONSUMER_ID_TAG = "consumerId";

    private final String consumerId;

    private final List<StatsCounter> counters = new ArrayList<>();

    private final StatsCounter totalMsgCounter;
    private final StatsCounter successMsgCounter;
    private final StatsCounter tmpTimeoutMsgCounter;
    private final StatsCounter tmpFailedMsgCounter;

    private final StatsCounter timeoutMsgCounter;
    private final StatsCounter failedMsgCounter;

    private final StatsCounter successIterationsCounter;
    private final StatsCounter failedIterationsCounter;

    public DefaultPublishMsgConsumerStats(String consumerId, StatsFactory statsFactory) {
        this.consumerId = consumerId;
        String statsKey = StatsType.PUBLISH_MSG_CONSUMER.getPrintName();
        this.totalMsgCounter = statsFactory.createStatsCounter(statsKey, TOTAL_MSGS, CONSUMER_ID_TAG, consumerId);
        this.successMsgCounter = statsFactory.createStatsCounter(statsKey, SUCCESSFUL_MSGS, CONSUMER_ID_TAG, consumerId);
        this.timeoutMsgCounter = statsFactory.createStatsCounter(statsKey, TIMEOUT_MSGS, CONSUMER_ID_TAG, consumerId);
        this.failedMsgCounter = statsFactory.createStatsCounter(statsKey, FAILED_MSGS, CONSUMER_ID_TAG, consumerId);
        this.tmpTimeoutMsgCounter = statsFactory.createStatsCounter(statsKey, TMP_TIMEOUT, CONSUMER_ID_TAG, consumerId);
        this.tmpFailedMsgCounter = statsFactory.createStatsCounter(statsKey, TMP_FAILED, CONSUMER_ID_TAG, consumerId);
        this.successIterationsCounter = statsFactory.createStatsCounter(statsKey, SUCCESSFUL_ITERATIONS, CONSUMER_ID_TAG, consumerId);
        this.failedIterationsCounter = statsFactory.createStatsCounter(statsKey, FAILED_ITERATIONS, CONSUMER_ID_TAG, consumerId);

        counters.add(totalMsgCounter);
        counters.add(successMsgCounter);
        counters.add(timeoutMsgCounter);
        counters.add(failedMsgCounter);
        counters.add(tmpTimeoutMsgCounter);
        counters.add(tmpFailedMsgCounter);
        counters.add(successIterationsCounter);
        counters.add(failedIterationsCounter);
    }

    @Override
    public String getConsumerId() {
        return consumerId;
    }

    @Override
    public void log(int totalMessagesCount, PackProcessingResult result, boolean finalIterationForPack) {
        int pending = result.getPendingMap().size();
        int failed = result.getFailedMap().size();
        int success = totalMessagesCount - (pending + failed);
        totalMsgCounter.add(totalMessagesCount);
        successMsgCounter.add(success);
        if (finalIterationForPack) {
            if (pending > 0 || failed > 0) {
                timeoutMsgCounter.add(pending);
                failedMsgCounter.add(failed);
                failedIterationsCounter.increment();
            } else {
                successIterationsCounter.increment();
            }
        } else {
            failedIterationsCounter.increment();
            tmpTimeoutMsgCounter.add(pending);
            tmpFailedMsgCounter.add(failed);
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
