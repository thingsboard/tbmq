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

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ContextAwareActorTest {

    @Test
    public void givenSubMillisecondWork_whenProcess_thenReportsProcessingTimeInNanoseconds() {
        AtomicLong captured = new AtomicLong(-1);
        ActorProcessingMetricService capturingService = (msgType, time) -> captured.set(time);

        ActorSystemContext systemContext = mock(ActorSystemContext.class);
        when(systemContext.getActorProcessingMetricService()).thenReturn(capturingService);

        ContextAwareActor actor = new ContextAwareActor(systemContext) {
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

        TbActorMsg msg = mock(TbActorMsg.class);
        when(msg.getMsgType()).thenReturn(MsgType.INCOMING_PUBLISH_MSG);

        actor.process(msg);

        // A ~2 ms operation is ~2_000_000 ns but only ~2 ms. A value this large can only be nanoseconds,
        // proving the actor reports StopWatch.getNanoTime() rather than the millisecond getTime().
        assertTrue("expected nanosecond-scale processing time but got " + captured.get(), captured.get() > 100_000L);
    }
}
