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
package org.thingsboard.mqtt.broker.integration.api.stats;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.stats.StatsCounter;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.integration.api.data.IntegrationPackProcessingResult;

import java.util.List;
import java.util.UUID;

import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.FAILED_ITERATIONS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.FAILED_MSGS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.INTEGRATION_ID_TAG;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.SUCCESSFUL_ITERATIONS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.SUCCESSFUL_MSGS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.TIMEOUT_MSGS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.TMP_FAILED;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.TMP_TIMEOUT;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.TOTAL_MSGS;

@Slf4j
public class IntegrationProcessorStatsImpl implements IntegrationProcessorStats {

    private volatile boolean active = true;

    @Getter
    private final UUID integrationUuid;

    private final List<StatsCounter> counters;

    private final StatsCounter totalMsgCounter;
    private final StatsCounter successMsgCounter;
    private final StatsCounter tmpTimeoutMsgCounter;
    private final StatsCounter tmpFailedMsgCounter;
    private final StatsCounter timeoutMsgCounter;
    private final StatsCounter failedMsgCounter;

    private final StatsCounter successIterationsCounter;
    private final StatsCounter failedIterationsCounter;

    public IntegrationProcessorStatsImpl(UUID integrationUuid, StatsFactory statsFactory) {
        this.integrationUuid = integrationUuid;
        String integrationId = integrationUuid.toString();
        String statsKey = StatsType.INTEGRATION_PROCESSOR.getPrintName();
        this.totalMsgCounter = statsFactory.createStatsCounter(statsKey, TOTAL_MSGS, INTEGRATION_ID_TAG, integrationId);
        this.successMsgCounter = statsFactory.createStatsCounter(statsKey, SUCCESSFUL_MSGS, INTEGRATION_ID_TAG, integrationId);
        this.tmpTimeoutMsgCounter = statsFactory.createStatsCounter(statsKey, TMP_TIMEOUT, INTEGRATION_ID_TAG, integrationId);
        this.tmpFailedMsgCounter = statsFactory.createStatsCounter(statsKey, TMP_FAILED, INTEGRATION_ID_TAG, integrationId);
        this.timeoutMsgCounter = statsFactory.createStatsCounter(statsKey, TIMEOUT_MSGS, INTEGRATION_ID_TAG, integrationId);
        this.failedMsgCounter = statsFactory.createStatsCounter(statsKey, FAILED_MSGS, INTEGRATION_ID_TAG, integrationId);
        this.successIterationsCounter = statsFactory.createStatsCounter(statsKey, SUCCESSFUL_ITERATIONS, INTEGRATION_ID_TAG, integrationId);
        this.failedIterationsCounter = statsFactory.createStatsCounter(statsKey, FAILED_ITERATIONS, INTEGRATION_ID_TAG, integrationId);

        counters = List.of(totalMsgCounter, successMsgCounter, tmpTimeoutMsgCounter, tmpFailedMsgCounter, timeoutMsgCounter,
                failedMsgCounter, successIterationsCounter, failedIterationsCounter);
    }

    @Override
    public void log(int totalMsgCount, IntegrationPackProcessingResult result, boolean finalIterationForPack) {
        int pending = result.getPendingMap().size();
        int failed = result.getFailedMap().size();
        int success = totalMsgCount - (pending + failed);
        totalMsgCounter.add(totalMsgCount);
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

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void disable() {
        this.active = false;
    }
}
