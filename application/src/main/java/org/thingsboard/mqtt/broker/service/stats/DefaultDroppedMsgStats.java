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
package org.thingsboard.mqtt.broker.service.stats;

import org.thingsboard.mqtt.broker.common.stats.DefaultCounter;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;

public class DefaultDroppedMsgStats implements DroppedMsgStats {

    private final DefaultCounter droppedMsgsCounter;

    public DefaultDroppedMsgStats(StatsFactory statsFactory) {
        this.droppedMsgsCounter = statsFactory.createDefaultCounter(StatsType.DROPPED_MSGS.getPrintName());
    }

    @Override
    public void increment() {
        droppedMsgsCounter.increment();
    }

    @Override
    public void increment(int count) {
        droppedMsgsCounter.add(count);
    }

    @Override
    public int getCount() {
        return droppedMsgsCounter.get();
    }

    @Override
    public void reset() {
        droppedMsgsCounter.clear();
    }
}
