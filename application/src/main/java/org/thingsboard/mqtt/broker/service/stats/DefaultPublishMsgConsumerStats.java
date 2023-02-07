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
import org.thingsboard.mqtt.broker.common.stats.ResettableTimer;
import org.thingsboard.mqtt.broker.common.stats.StatsCounter;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.service.processing.PackProcessingResult;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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

    private final List<StatsCounter> counters;

    private final StatsCounter totalMsgCounter;
    private final StatsCounter successMsgCounter;
    private final StatsCounter tmpTimeoutMsgCounter;
    private final StatsCounter tmpFailedMsgCounter;

    private final StatsCounter timeoutMsgCounter;
    private final StatsCounter failedMsgCounter;

    private final StatsCounter successIterationsCounter;
    private final StatsCounter failedIterationsCounter;

    private final ResettableTimer msgProcessingTimer;
    private final ResettableTimer packProcessingTimer;

    private final AtomicLong totalPackSize = new AtomicLong();

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

        counters = List.of(totalMsgCounter, successMsgCounter, timeoutMsgCounter, failedMsgCounter, tmpTimeoutMsgCounter, tmpFailedMsgCounter,
                successIterationsCounter, failedIterationsCounter);

        this.msgProcessingTimer = new ResettableTimer(statsFactory.createTimer(statsKey + ".processing.time", CONSUMER_ID_TAG, consumerId));
        this.packProcessingTimer = new ResettableTimer(statsFactory.createTimer(statsKey + ".pack.processing.time", CONSUMER_ID_TAG, consumerId));
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
    public void logMsgProcessingTime(long amount, TimeUnit unit) {
        msgProcessingTimer.logTime(amount, unit);
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
    public List<StatsCounter> getStatsCounters() {
        return counters;
    }

    @Override
    public double getAvgMsgProcessingTime() {
        return msgProcessingTimer.getAvg();
    }

    @Override
    public void reset() {
        counters.forEach(StatsCounter::clear);
        msgProcessingTimer.reset();
        packProcessingTimer.reset();
        totalPackSize.getAndSet(0);
    }
}
