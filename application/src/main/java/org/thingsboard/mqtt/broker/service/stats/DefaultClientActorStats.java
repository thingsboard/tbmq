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

import org.thingsboard.mqtt.broker.actors.msg.TbActorMsg;
import org.thingsboard.mqtt.broker.actors.shared.TimedMsg;
import org.thingsboard.mqtt.broker.common.stats.ResettableTimer;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class DefaultClientActorStats implements ClientActorStats {
    private static final String MSG_TYPE_TAG = "msgType";
    private final String statsKey = StatsType.CLIENT_ACTOR.getPrintName();

    private final ConcurrentMap<String, ResettableTimer> timers = new ConcurrentHashMap<>();
    private final ResettableTimer queueTimer;

    private final StatsFactory statsFactory;

    public DefaultClientActorStats(StatsFactory statsFactory) {
        this.statsFactory = statsFactory;
        this.queueTimer = new ResettableTimer(statsFactory.createTimer(statsKey + ".msgInQueueTime"), true);
    }

    @Override
    public void logMsgProcessingTime(String msgType, long amount, TimeUnit unit) {
        timers.computeIfAbsent(msgType, s -> new ResettableTimer(statsFactory.createTimer(statsKey + ".processing.time", MSG_TYPE_TAG, msgType)))
                .logTime(amount, unit);
    }

    @Override
    public void logMsgQueueTime(TbActorMsg msg, TimeUnit unit) {
        long amount = System.nanoTime() - ((TimedMsg) msg).getMsgCreatedTimeNanos();
        queueTimer.logTime(amount, unit);
    }

    @Override
    public Map<String, ResettableTimer> getTimers() {
        return timers;
    }

    @Override
    public int getMsgCount() {
        return queueTimer.getCount();
    }

    @Override
    public double getQueueTimeAvg() {
        return queueTimer.getAvg();
    }

    @Override
    public double getQueueTimeMax() {
        return queueTimer.getMax();
    }

    @Override
    public void reset() {
        timers.forEach((msgType, timer) -> timer.reset());
        queueTimer.reset();
    }
}
