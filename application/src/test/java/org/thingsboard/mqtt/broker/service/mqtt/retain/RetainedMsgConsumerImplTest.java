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
package org.thingsboard.mqtt.broker.service.mqtt.retain;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.gen.queue.RetainedMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.DefaultTbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.RetainedMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.stats.RetainedMsgConsumerStats;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import java.util.List;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RetainedMsgConsumerImplTest {

    private RetainedMsgConsumerStats stats;
    private RetainedMsgChangesCallback callback;
    private RetainedMsgConsumerImpl consumer;

    @Before
    public void setUp() {
        StatsManager statsManager = mock(StatsManager.class);
        stats = mock(RetainedMsgConsumerStats.class);
        when(statsManager.getRetainedMsgConsumerStats()).thenReturn(stats);
        callback = mock(RetainedMsgChangesCallback.class);
        consumer = new RetainedMsgConsumerImpl(
                mock(RetainedMsgQueueFactory.class),
                mock(ServiceInfoProvider.class),
                mock(RetainedMsgPersistenceService.class),
                mock(TbQueueAdmin.class),
                statsManager);
    }

    @Test
    public void givenFullyProcessedPack_whenProcessPack_thenAllCountersAdvanceTogether() {
        // Two empty (tombstone) retained-msg records take the cleared path — the point of this test is that total
        // and the new/cleared split are both recorded for a successfully processed pack.
        List<TbProtoQueueMsg<RetainedMsgProto>> messages = List.of(clearedMsg(), clearedMsg());

        consumer.processPack(messages, callback);

        verify(stats).logTotal(2);
        verify(stats).log(0, 2);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void givenMidPackFailure_whenProcessPack_thenNoCounterAdvances() {
        // A record that throws mid-pack (here while reading its key) must abort before any counter is recorded, so
        // the uncommitted pack is not counted and total stays consistent with new+cleared.
        TbProtoQueueMsg<RetainedMsgProto> boom = mock(TbProtoQueueMsg.class);
        when(boom.getKey()).thenThrow(new RuntimeException("boom"));
        List<TbProtoQueueMsg<RetainedMsgProto>> messages = List.of(boom);

        assertThrows(RuntimeException.class, () -> consumer.processPack(messages, callback));

        verify(stats, never()).logTotal(anyInt());
        verify(stats, never()).log(anyInt(), anyInt());
    }

    private static TbProtoQueueMsg<RetainedMsgProto> clearedMsg() {
        // Default RetainedMsgProto has an empty payload + qos 0, which the consumer treats as a cleared (tombstone) msg.
        DefaultTbQueueMsgHeaders headers = new DefaultTbQueueMsgHeaders();
        headers.put(BrokerConstants.SERVICE_ID_HEADER, "svc".getBytes());
        return new TbProtoQueueMsg<>("some/topic", RetainedMsgProto.newBuilder().build(), headers);
    }
}
