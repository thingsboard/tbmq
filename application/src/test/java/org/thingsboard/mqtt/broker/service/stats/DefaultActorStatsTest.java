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

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.mqtt.broker.actors.msg.MsgType;
import org.thingsboard.mqtt.broker.actors.msg.TbActorMsg;
import org.thingsboard.mqtt.broker.actors.shared.TimedMsg;
import org.thingsboard.mqtt.broker.common.stats.DefaultStatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsConstantNames;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.common.stats.StatsType;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DefaultActorStatsTest {

    private SimpleMeterRegistry meterRegistry;
    private StatsFactory statsFactory;

    @Before
    public void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        statsFactory = new DefaultStatsFactory(meterRegistry);
    }

    @Test
    public void givenClientActorType_whenLogProcessingTime_thenTimerNamedClientActor() {
        ActorStats stats = new DefaultActorStats(statsFactory, StatsType.CLIENT_ACTOR);
        stats.logMsgProcessingTime(MsgType.INCOMING_PUBLISH_MSG, System.nanoTime() - 1_500_000L, TimeUnit.NANOSECONDS);

        Timer timer = meterRegistry.find("clientActor.processing.time")
                .tag(StatsConstantNames.MSG_TYPE, MsgType.INCOMING_PUBLISH_MSG.toString()).timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    public void givenDeviceActorType_whenLogProcessingTime_thenTimerNamedDeviceActor() {
        ActorStats stats = new DefaultActorStats(statsFactory, StatsType.PERSISTED_DEVICE_ACTOR);
        stats.logMsgProcessingTime(MsgType.INCOMING_PUBLISH_MSG, System.nanoTime(), TimeUnit.NANOSECONDS);

        Timer timer = meterRegistry.find("deviceActor.processing.time")
                .tag(StatsConstantNames.MSG_TYPE, MsgType.INCOMING_PUBLISH_MSG.toString()).timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    public void givenTimedMsg_whenLogQueueTime_thenQueueTimerRecords() {
        ActorStats stats = new DefaultActorStats(statsFactory, StatsType.PERSISTED_DEVICE_ACTOR);
        stats.logMsgQueueTime(new TestTimedMsg(System.nanoTime() - 2_000_000L), TimeUnit.NANOSECONDS);

        Timer timer = meterRegistry.find("deviceActor.msgInQueueTime").timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
        assertEquals(1, stats.getMsgCount());
    }

    @Test
    public void deviceMessagesAreTimedMsg() {
        assertTrue(TimedMsg.class.isAssignableFrom(
                org.thingsboard.mqtt.broker.actors.device.messages.IncomingPublishMsg.class));
        assertTrue(TimedMsg.class.isAssignableFrom(
                org.thingsboard.mqtt.broker.actors.device.messages.DeviceConnectedEventMsg.class));
        assertTrue(TimedMsg.class.isAssignableFrom(
                org.thingsboard.mqtt.broker.actors.device.messages.StopDeviceActorCommandMsg.class));
    }

    // Generic TbActorMsg + TimedMsg stand-in for the queue-timer unit test.
    private static final class TestTimedMsg implements TbActorMsg, TimedMsg {
        private final long createdTimeNanos;

        private TestTimedMsg(long createdTimeNanos) {
            this.createdTimeNanos = createdTimeNanos;
        }

        @Override
        public long getMsgCreatedTimeNanos() {
            return createdTimeNanos;
        }

        @Override
        public MsgType getMsgType() {
            return MsgType.INCOMING_PUBLISH_MSG;
        }
    }
}
