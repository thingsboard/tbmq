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
package org.thingsboard.mqtt.broker.standalone.pipeline;

import io.micrometer.core.instrument.Timer;
import org.thingsboard.mqtt.broker.common.stats.StatsCounter;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;

import java.util.concurrent.TimeUnit;

import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.FAILED_MSGS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.SUCCESSFUL_MSGS;
import static org.thingsboard.mqtt.broker.common.stats.StatsConstantNames.TOTAL_MSGS;

/**
 * What one worker of the dispatch stage reports as {@code incomingPublishMsg.consumed}: the same series, with the
 * same tags, as the consumer of the cluster edition, minus the ones that describe polling a queue in packs. It is
 * not registered with the stats manager, so nothing is logged periodically and nothing is reset.
 */
public class StandaloneDispatchStats {

    private static final String CONSUMER_ID_TAG = "consumerId";

    private final StatsCounter totalMsgCounter;
    private final StatsCounter successMsgCounter;
    private final StatsCounter failedMsgCounter;
    private final Timer msgProcessingTimer;

    public StandaloneDispatchStats(String consumerId, StatsFactory statsFactory) {
        String statsKey = StatsType.PUBLISH_MSG_CONSUMER.getPrintName();
        this.totalMsgCounter = statsFactory.createStatsCounter(statsKey, TOTAL_MSGS, CONSUMER_ID_TAG, consumerId);
        this.successMsgCounter = statsFactory.createStatsCounter(statsKey, SUCCESSFUL_MSGS, CONSUMER_ID_TAG, consumerId);
        this.failedMsgCounter = statsFactory.createStatsCounter(statsKey, FAILED_MSGS, CONSUMER_ID_TAG, consumerId);
        this.msgProcessingTimer = statsFactory.createTimer(statsKey + ".processing.time", CONSUMER_ID_TAG, consumerId);
    }

    /**
     * A publish was handed to the dispatcher.
     */
    public void onDispatched() {
        totalMsgCounter.increment();
    }

    /**
     * The callback of a dispatched publish resolved successfully.
     */
    public void onSuccess() {
        successMsgCounter.increment();
    }

    /**
     * The callback of a dispatched publish failed, or the dispatcher threw.
     */
    public void onFailure() {
        failedMsgCounter.increment();
    }

    public void logMsgProcessingTime(long amount, TimeUnit unit) {
        msgProcessingTimer.record(amount, unit);
    }

}
