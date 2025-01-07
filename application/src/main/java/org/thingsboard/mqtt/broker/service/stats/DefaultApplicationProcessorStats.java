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

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.stats.ResettableTimer;
import org.thingsboard.mqtt.broker.common.stats.StatsCounter;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationPackProcessingResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.CLIENT_ID_TAG;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.FAILED_ITERATIONS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.SUCCESSFUL_ITERATIONS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.SUCCESSFUL_PUBLISH_MSGS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.SUCCESSFUL_PUBREL_MSGS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.TIMEOUT_PUBLISH_MSGS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.TIMEOUT_PUBREL_MSGS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.TMP_TIMEOUT_PUBLISH;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.TMP_TIMEOUT_PUBREL;

@Slf4j
public class DefaultApplicationProcessorStats implements ApplicationProcessorStats {
    private static final String PACKET_TYPE_TAG = "packetType";
    private volatile boolean active = true;

    private final String clientId;

    private final Map<String, ResettableTimer> latencyTimers;
    private final List<StatsCounter> counters;

    private final StatsCounter successPublishMsgCounter;
    private final StatsCounter successPubRelMsgCounter;

    private final StatsCounter tmpTimeoutPublishMsgCounter;
    private final StatsCounter tmpTimeoutPubRelMsgCounter;

    private final StatsCounter timeoutPublishMsgCounter;
    private final StatsCounter timeoutPubRelMsgCounter;

    private final StatsCounter successIterationsCounter;
    private final StatsCounter failedIterationsCounter;

    private final ResettableTimer pubAckLatencyTimer;
    private final ResettableTimer pubRecLatencyTimer;
    private final ResettableTimer pubCompLatencyTimer;

    public DefaultApplicationProcessorStats(String clientId, StatsFactory statsFactory) {
        this.clientId = clientId;
        String statsKey = StatsType.APP_PROCESSOR.getPrintName();
        this.successPublishMsgCounter = statsFactory.createStatsCounter(statsKey, SUCCESSFUL_PUBLISH_MSGS, CLIENT_ID_TAG, clientId);
        this.successPubRelMsgCounter = statsFactory.createStatsCounter(statsKey, SUCCESSFUL_PUBREL_MSGS, CLIENT_ID_TAG, clientId);
        this.tmpTimeoutPublishMsgCounter = statsFactory.createStatsCounter(statsKey, TMP_TIMEOUT_PUBLISH, CLIENT_ID_TAG, clientId);
        this.tmpTimeoutPubRelMsgCounter = statsFactory.createStatsCounter(statsKey, TMP_TIMEOUT_PUBREL, CLIENT_ID_TAG, clientId);
        this.timeoutPublishMsgCounter = statsFactory.createStatsCounter(statsKey, TIMEOUT_PUBLISH_MSGS, CLIENT_ID_TAG, clientId);
        this.timeoutPubRelMsgCounter = statsFactory.createStatsCounter(statsKey, TIMEOUT_PUBREL_MSGS, CLIENT_ID_TAG, clientId);
        this.successIterationsCounter = statsFactory.createStatsCounter(statsKey, SUCCESSFUL_ITERATIONS, CLIENT_ID_TAG, clientId);
        this.failedIterationsCounter = statsFactory.createStatsCounter(statsKey, FAILED_ITERATIONS, CLIENT_ID_TAG, clientId);

        counters = List.of(successPublishMsgCounter, successPubRelMsgCounter, tmpTimeoutPublishMsgCounter, tmpTimeoutPubRelMsgCounter,
                timeoutPublishMsgCounter, timeoutPubRelMsgCounter, successIterationsCounter, failedIterationsCounter);

        this.pubAckLatencyTimer = new ResettableTimer(statsFactory.createTimer(statsKey + ".latency", PACKET_TYPE_TAG, "puback"), true);
        this.pubRecLatencyTimer = new ResettableTimer(statsFactory.createTimer(statsKey + ".latency", PACKET_TYPE_TAG, "pubrec"), true);
        this.pubCompLatencyTimer = new ResettableTimer(statsFactory.createTimer(statsKey + ".latency", PACKET_TYPE_TAG, "pubcomp"), true);
        latencyTimers = Map.of(
                "PUBACK", pubAckLatencyTimer,
                "PUBREC", pubRecLatencyTimer,
                "PUBCOMP", pubCompLatencyTimer
        );
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    @Override
    public void log(int totalPublishMsgsCount, int totalPubRelMsgsCount, ApplicationPackProcessingResult result, boolean finalIterationForPack) {
        int pendingPublish = result.getPublishPendingMap().size();
        int pendingPubRel = result.getPubRelPendingMap().size();
        successPublishMsgCounter.add(totalPublishMsgsCount - pendingPublish);
        successPubRelMsgCounter.add(totalPubRelMsgsCount - pendingPubRel);
        if (finalIterationForPack) {
            if (pendingPublish > 0 || pendingPubRel > 0) {
                timeoutPublishMsgCounter.add(pendingPublish);
                timeoutPubRelMsgCounter.add(pendingPubRel);
                failedIterationsCounter.increment();
            } else {
                successIterationsCounter.increment();
            }
        } else {
            failedIterationsCounter.increment();
            tmpTimeoutPublishMsgCounter.add(pendingPublish);
            tmpTimeoutPubRelMsgCounter.add(pendingPubRel);
        }
    }

    @Override
    public void logPubAckLatency(long startTime, TimeUnit unit) {
        long amount = getAmount(startTime);
        pubAckLatencyTimer.logTime(amount, unit);
    }

    @Override
    public void logPubRecLatency(long startTime, TimeUnit unit) {
        long amount = getAmount(startTime);
        pubRecLatencyTimer.logTime(amount, unit);
    }

    @Override
    public void logPubCompLatency(long startTime, TimeUnit unit) {
        long amount = getAmount(startTime);
        pubCompLatencyTimer.logTime(amount, unit);
    }

    private long getAmount(long startTime) {
        return System.nanoTime() - startTime;
    }

    @Override
    public List<StatsCounter> getStatsCounters() {
        return counters;
    }

    @Override
    public Map<String, ResettableTimer> getLatencyTimers() {
        return latencyTimers;
    }

    @Override
    public void reset() {
        counters.forEach(StatsCounter::clear);
        latencyTimers.forEach((s, timer) -> timer.reset());
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void disable() {
        this.active = false;
    }
}
