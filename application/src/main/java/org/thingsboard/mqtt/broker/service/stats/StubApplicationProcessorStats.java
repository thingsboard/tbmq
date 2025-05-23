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

import org.thingsboard.mqtt.broker.common.stats.ResettableTimer;
import org.thingsboard.mqtt.broker.common.stats.StatsCounter;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationPackProcessingResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class StubApplicationProcessorStats implements ApplicationProcessorStats {
    public static StubApplicationProcessorStats STUB_APPLICATION_PROCESSOR_STATS = new StubApplicationProcessorStats();

    private StubApplicationProcessorStats(){}

    @Override
    public String getClientId() {
        return "STUB_CLIENT_ID";
    }

    @Override
    public void log(int totalPublishMsgsCount, int totalPubRelMsgsCount, ApplicationPackProcessingResult packProcessingResult, boolean finalIterationForPack) {
    }

    @Override
    public void logPubAckLatency(long startTime, TimeUnit unit) {

    }

    @Override
    public void logPubRecLatency(long startTime, TimeUnit unit) {

    }

    @Override
    public void logPubCompLatency(long startTime, TimeUnit unit) {

    }


    @Override
    public List<StatsCounter> getStatsCounters() {
        return Collections.emptyList();
    }

    @Override
    public Map<String, ResettableTimer> getLatencyTimers() {
        return Collections.emptyMap();
    }

    @Override
    public void reset() {

    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void disable() {

    }
}
