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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.mqtt.broker.actors.msg.MsgType;
import org.thingsboard.mqtt.broker.common.stats.DefaultStatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsConstantNames;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MsgTypeActorProcessingMetricServiceTest {

    SimpleMeterRegistry meterRegistry;
    MsgTypeActorProcessingMetricService service;

    @Before
    public void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        StatsFactory statsFactory = new DefaultStatsFactory(meterRegistry);
        service = new MsgTypeActorProcessingMetricService(statsFactory);
    }

    @Test
    public void givenSubMillisNanoTime_whenLogMsgProcessingTime_thenTimerRecordsInNanoseconds() {
        // 1.5 ms expressed in nanoseconds — the unit ContextAwareActor supplies via StopWatch.getNanoTime().
        // If the timer records the value as MILLISECONDS, the recorded total is 1_500_000 ms, not 1.5 ms.
        service.logMsgProcessingTime(MsgType.INCOMING_PUBLISH_MSG, 1_500_000L);

        Timer timer = meterRegistry.find("actors.processing")
                .tag(StatsConstantNames.MSG_TYPE, MsgType.INCOMING_PUBLISH_MSG.toString())
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
        assertEquals(1.5, timer.totalTime(TimeUnit.MILLISECONDS), 0.001);
    }
}
