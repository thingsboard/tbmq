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
package org.thingsboard.mqtt.broker.service.subscription;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.gen.queue.ClientSubscriptionsProto;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientSubscriptionsQueueFactory;
import org.thingsboard.mqtt.broker.service.stats.ClientSubscriptionConsumerStats;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import java.util.List;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ClientSubscriptionConsumerImplTest {

    private ClientSubscriptionConsumerStats stats;
    private ClientSubscriptionChangesCallback callback;
    private ClientSubscriptionConsumerImpl consumer;

    @Before
    public void setUp() {
        StatsManager statsManager = mock(StatsManager.class);
        stats = mock(ClientSubscriptionConsumerStats.class);
        when(statsManager.getClientSubscriptionConsumerStats()).thenReturn(stats);
        callback = mock(ClientSubscriptionChangesCallback.class);
        consumer = new ClientSubscriptionConsumerImpl(
                mock(ClientSubscriptionsQueueFactory.class),
                mock(ServiceInfoProvider.class),
                mock(SubscriptionPersistenceService.class),
                mock(TbQueueAdmin.class),
                statsManager);
    }

    @Test
    public void givenFullyProcessedPack_whenProcessPack_thenAllCountersAdvanceTogether() {
        // Two dummy-prefixed records take the ignore fast path (no proto/callback needed) — the point of this test
        // is that total and the accepted/ignored split are both recorded for a successfully processed pack.
        List<TbProtoQueueMsg<ClientSubscriptionsProto>> messages = List.of(dummyMsg(), dummyMsg());

        consumer.processPack(messages, callback);

        verify(stats).logTotal(2);
        verify(stats).log(0, 2);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void givenMidPackFailure_whenProcessPack_thenNoCounterAdvances() {
        // A record that throws mid-pack (here while reading its key) must abort before any counter is recorded, so
        // the pack is neither committed nor counted — total stays consistent with accepted+ignored and a later
        // rebalance re-delivery does not double-count.
        TbProtoQueueMsg<ClientSubscriptionsProto> boom = mock(TbProtoQueueMsg.class);
        when(boom.getKey()).thenThrow(new RuntimeException("boom"));
        List<TbProtoQueueMsg<ClientSubscriptionsProto>> messages = List.of(boom);

        assertThrows(RuntimeException.class, () -> consumer.processPack(messages, callback));

        verify(stats, never()).logTotal(anyInt());
        verify(stats, never()).log(anyInt(), anyInt());
    }

    @SuppressWarnings("unchecked")
    private static TbProtoQueueMsg<ClientSubscriptionsProto> dummyMsg() {
        TbProtoQueueMsg<ClientSubscriptionsProto> msg = mock(TbProtoQueueMsg.class);
        when(msg.getKey()).thenReturn(BrokerConstants.SYSTEM_DUMMY_CLIENT_ID_PREFIX + "x");
        return msg;
    }
}
