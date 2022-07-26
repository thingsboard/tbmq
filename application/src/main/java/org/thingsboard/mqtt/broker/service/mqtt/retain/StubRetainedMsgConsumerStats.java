/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.retain;

import org.thingsboard.mqtt.broker.common.stats.StatsCounter;

import java.util.Collections;
import java.util.List;

public class StubRetainedMsgConsumerStats implements RetainedMsgConsumerStats {

    public static StubRetainedMsgConsumerStats STUB_RETAINED_MSG_CONSUMER_STATS = new StubRetainedMsgConsumerStats();

    private StubRetainedMsgConsumerStats() {
    }

    @Override
    public void logTotal(int totalRetainedMsgs) {

    }

    @Override
    public void log(int newRetainedMsgCount, int clearedRetainedMsgCount) {

    }

    @Override
    public List<StatsCounter> getStatsCounters() {
        return Collections.emptyList();
    }

    @Override
    public void reset() {

    }
}
