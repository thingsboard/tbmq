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
package org.thingsboard.mqtt.broker.actors.service;

import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.msg.MsgType;
import org.thingsboard.mqtt.broker.common.stats.StatsConstantNames;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Service
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "actors.system.processing-metrics", value = "enabled", havingValue = "true")
public class MsgTypeActorProcessingMetricService implements ActorProcessingMetricService {

    private static final String ACTOR_MSG_PROCESSING_STATS_NAME = "actors.processing";

    private final ConcurrentMap<MsgType, Timer> timers = new ConcurrentHashMap<>();
    private final StatsFactory statsFactory;

    @Override
    public void logMsgProcessingTime(MsgType msgType, long time) {
        Timer timer = timers.computeIfAbsent(msgType, t -> statsFactory.createTimer(ACTOR_MSG_PROCESSING_STATS_NAME,
                StatsConstantNames.MSG_TYPE, msgType.toString()));
        timer.record(time, TimeUnit.MILLISECONDS);
    }
}
