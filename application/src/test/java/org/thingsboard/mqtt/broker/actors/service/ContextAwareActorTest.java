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

import org.junit.Test;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.msg.MsgType;
import org.thingsboard.mqtt.broker.actors.msg.TbActorMsg;
import org.thingsboard.mqtt.broker.actors.shared.TimedMsg;
import org.thingsboard.mqtt.broker.common.stats.ResettableTimer;
import org.thingsboard.mqtt.broker.service.stats.ActorStats;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ContextAwareActorTest {

    @Test
    public void givenSubMillisecondWork_whenProcess_thenReportsProcessingTimeInNanoseconds() {
        CapturingActorStats stats = new CapturingActorStats();
        ContextAwareActor actor = newActor(stats);

        TbActorMsg msg = mock(TbActorMsg.class);
        when(msg.getMsgType()).thenReturn(MsgType.INCOMING_PUBLISH_MSG);

        actor.process(msg);

        // A ~2 ms operation is ~2_000_000 ns. A value this large can only be nanoseconds.
        assertTrue("expected nanosecond-scale processing time but got " + stats.processingNanos.get(),
                stats.processingNanos.get() > 100_000L);
        assertEquals("non-TimedMsg must not record queue time", 0, stats.queueCalls.get());
    }

    @Test
    public void givenTimedMsg_whenProcess_thenRecordsQueueTime() {
        CapturingActorStats stats = new CapturingActorStats();
        ContextAwareActor actor = newActor(stats);

        TimedTbActorMsg msg = new TimedTbActorMsg();

        actor.process(msg);

        assertEquals("TimedMsg must record queue time exactly once", 1, stats.queueCalls.get());
    }

    private ContextAwareActor newActor(ActorStats stats) {
        ActorSystemContext systemContext = mock(ActorSystemContext.class);
        return new ContextAwareActor(systemContext, stats) {
            @Override
            protected boolean doProcess(TbActorMsg msg) {
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return true;
            }
        };
    }

    private static final class TimedTbActorMsg implements TbActorMsg, TimedMsg {
        private final long created = System.nanoTime();
        @Override public long getMsgCreatedTimeNanos() { return created; }
        @Override public MsgType getMsgType() { return MsgType.INCOMING_PUBLISH_MSG; }
    }

    private static final class CapturingActorStats implements ActorStats {
        final AtomicLong processingNanos = new AtomicLong(-1);
        final AtomicInteger queueCalls = new AtomicInteger(0);

        @Override
        public void logMsgProcessingTime(MsgType msgType, long startTime) {
            processingNanos.set(System.nanoTime() - startTime);
        }

        @Override
        public void logMsgQueueTime(TimedMsg msg) {
            queueCalls.incrementAndGet();
        }

        @Override public Map<String, ResettableTimer> getTimers() { return Map.of(); }
        @Override public int getMsgCount() { return 0; }
        @Override public double getQueueTimeAvg() { return 0; }
        @Override public double getQueueTimeMax() { return 0; }
        @Override public void reset() { }
    }
}
