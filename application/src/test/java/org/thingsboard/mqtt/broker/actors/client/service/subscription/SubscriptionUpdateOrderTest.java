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
package org.thingsboard.mqtt.broker.actors.client.service.subscription;

import org.junit.Test;
import org.thingsboard.mqtt.broker.common.data.subscription.ClientTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class SubscriptionUpdateOrderTest {
    private static final Set<TopicSubscription> ONE = Set.of(new ClientTopicSubscription("one", 1));
    private static final Set<TopicSubscription> TWO = Set.of(new ClientTopicSubscription("two", 1));

    @Test
    public void olderRemoteClearCannotEraseAcknowledgedLocalSubscription() {
        SubscriptionUpdateOrder order = new SubscriptionUpdateOrder();
        var write = order.begin("device", Set.of());
        order.complete(write, ONE, 11);
        assertEquals(ONE, order.resolve("device", Set.of(), 10).orElseThrow());
        assertEquals(1, order.pendingClients());
        assertEquals(ONE, order.resolve("device", ONE, 11).orElseThrow());
        assertEquals(0, order.pendingClients());
    }

    @Test
    public void oldSnapshotIsDeferredUntilLocalOffsetIsKnown() {
        SubscriptionUpdateOrder order = new SubscriptionUpdateOrder();
        var write = order.begin("device", Set.of());
        assertTrue(order.resolve("device", Set.of(), 10).isEmpty());
        order.complete(write, ONE, 11);
        assertEquals(ONE, order.resolve("device", null, -1).orElseThrow());
        order.resolve("device", ONE, 11);
        assertEquals(0, order.pendingClients());
    }

    @Test
    public void newerRemoteSnapshotWinsEvenWhenLocalAckArrivesLast() {
        SubscriptionUpdateOrder order = new SubscriptionUpdateOrder();
        var write = order.begin("device", Set.of());
        assertTrue(order.resolve("device", TWO, 12).isEmpty());
        order.complete(write, ONE, 11);
        assertEquals(TWO, order.resolve("device", null, -1).orElseThrow());
        assertEquals(0, order.pendingClients());
    }

    @Test
    public void ownEchoBeforeProducerCallbackDoesNotReleaseFenceEarly() {
        SubscriptionUpdateOrder order = new SubscriptionUpdateOrder();
        var write = order.begin("device", Set.of());
        assertTrue(order.resolve("device", ONE, 11).isEmpty());
        assertEquals(1, order.pendingClients());
        order.complete(write, ONE, 11);
        assertEquals(ONE, order.resolve("device", null, -1).orElseThrow());
        assertEquals(0, order.pendingClients());
    }

    @Test
    public void overlappingLocalWritesWaitForAllResultsAndChooseKafkaOrder() {
        SubscriptionUpdateOrder order = new SubscriptionUpdateOrder();
        var first = order.begin("device", Set.of());
        var second = order.begin("device", ONE);
        order.complete(second, TWO, 12);
        assertTrue(order.resolve("device", TWO, 12).isEmpty());
        order.complete(first, ONE, 11);
        assertEquals(TWO, order.resolve("device", null, -1).orElseThrow());
        assertEquals(0, order.pendingClients());
    }

    @Test
    public void failedWriteRestoresLastDurableSnapshotAndReleasesFence() {
        SubscriptionUpdateOrder order = new SubscriptionUpdateOrder();
        var write = order.begin("device", ONE);
        order.complete(write, TWO, -1);
        assertEquals(ONE, order.resolve("device", null, -1).orElseThrow());
        assertEquals(0, order.pendingClients());
    }

    @Test
    public void failedWriteDoesNotDiscardNewerRemoteSnapshot() {
        SubscriptionUpdateOrder order = new SubscriptionUpdateOrder();
        var write = order.begin("device", ONE);
        assertTrue(order.resolve("device", TWO, 11).isEmpty());
        order.complete(write, Set.of(), -1);
        assertEquals(TWO, order.resolve("device", null, -1).orElseThrow());
        assertEquals(0, order.pendingClients());
    }

    @Test
    public void localUnsubscribeCannotBeUndoneByOlderRemoteSubscribe() {
        SubscriptionUpdateOrder order = new SubscriptionUpdateOrder();
        var write = order.begin("device", ONE);
        order.complete(write, Set.of(), 11);
        assertEquals(Set.of(), order.resolve("device", ONE, 10).orElseThrow());
        order.resolve("device", Set.of(), 11);
        assertEquals(0, order.pendingClients());
    }

    @Test
    public void localSnapshotIsImmutableAndIdleClientsLeaveNoLedger() {
        SubscriptionUpdateOrder order = new SubscriptionUpdateOrder();
        for (int i = 0; i < 10000; i++) {
            String id = "device-" + i;
            Set<TopicSubscription> mutable = new HashSet<>(ONE);
            var write = order.begin(id, Set.of());
            order.complete(write, mutable, 100);
            mutable.clear();
            assertEquals(ONE, order.resolve(id, null, -1).orElseThrow());
            order.resolve(id, ONE, 100);
        }
        assertEquals(0, order.pendingClients());
    }

    @Test
    public void remoteOnlyClientsNeedNoFenceAndCompletionSignalsAreHarmless() {
        SubscriptionUpdateOrder order = new SubscriptionUpdateOrder();
        assertEquals(ONE, order.resolve("device", ONE, 10).orElseThrow());
        assertTrue(order.resolve("device", null, -1).isEmpty());
        assertEquals(0, order.pendingClients());
    }
}
