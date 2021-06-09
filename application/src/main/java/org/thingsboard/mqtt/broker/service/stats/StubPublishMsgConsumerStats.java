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

import org.thingsboard.mqtt.broker.common.stats.StatsCounter;
import org.thingsboard.mqtt.broker.service.processing.PackProcessingResult;

import java.util.Collections;
import java.util.List;

public class StubPublishMsgConsumerStats implements PublishMsgConsumerStats {

    public static StubPublishMsgConsumerStats STUB_PUBLISH_MSG_CONSUMER_STATS = new StubPublishMsgConsumerStats();

    private StubPublishMsgConsumerStats(){}

    @Override
    public String getConsumerId() {
        return "STUB_CONSUMER_ID";
    }

    @Override
    public void log(int totalMessagesCount, PackProcessingResult packProcessingResult, boolean finalIterationForPack, long processingTimeMs) {
    }


    @Override
    public List<StatsCounter> getStatsCounters() {
        return Collections.emptyList();
    }

    @Override
    public double getMeanProcessingTime() {
        return 0;
    }

    @Override
    public void reset() {
    }
}
