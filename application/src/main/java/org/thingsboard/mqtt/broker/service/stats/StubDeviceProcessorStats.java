/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.processing.DevicePackProcessingResult;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class StubDeviceProcessorStats implements DeviceProcessorStats {
    public static final StubDeviceProcessorStats STUB_DEVICE_PROCESSOR_STATS = new StubDeviceProcessorStats();

    private StubDeviceProcessorStats(){}

    @Override
    public String getConsumerId() {
        return "STUB_CONSUMER_ID";
    }

    @Override
    public void log(int msgsCount, DevicePackProcessingResult result, boolean finalIterationForPack) {
    }

    @Override
    public void logClientIdPackProcessingTime(long amount, TimeUnit unit) {
    }

    @Override
    public void logClientIdPacksProcessingTime(int packSize, long amount, TimeUnit unit) {
    }

    @Override
    public List<StatsCounter> getStatsCounters() {
        return Collections.emptyList();
    }

    @Override
    public double getAvgClientIdMsgPackProcessingTime() {
        return 0;
    }

    @Override
    public double getAvgPackProcessingTime() {
        return 0;
    }

    @Override
    public double getAvgPackSize() {
        return 0;
    }

    @Override
    public void reset() {
    }
}
