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
package org.thingsboard.mqtt.broker.service.subscription.shared;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SharedSubscriptionCacheServiceImplTest {

    private static final String CLIENT_ID_1 = "clientId1";
    private static final String CLIENT_ID_2 = "clientId2";

    ClientSessionCache clientSessionCache;
    SharedSubscriptionCacheServiceImpl sharedSubscriptionCache;

    ClientSessionInfo clientSessionInfo1;
    ClientSessionInfo clientSessionInfo2;

    @Before
    public void setUp() {
        clientSessionCache = mock(ClientSessionCache.class);
        sharedSubscriptionCache = spy(new SharedSubscriptionCacheServiceImpl(clientSessionCache));

        clientSessionInfo1 = mock(ClientSessionInfo.class);
        clientSessionInfo2 = mock(ClientSessionInfo.class);

        when(clientSessionCache.getClientSessionInfo(CLIENT_ID_1)).thenReturn(clientSessionInfo1);
        when(clientSessionCache.getClientSessionInfo(CLIENT_ID_2)).thenReturn(clientSessionInfo2);
    }

    @After
    public void destroy() {
        sharedSubscriptionCache.getSharedSubscriptionsMap().clear();
    }

    @Test
    public void testPutDifferentClients() {
        when(clientSessionInfo1.getType()).thenReturn(ClientType.APPLICATION);
        when(clientSessionInfo2.getType()).thenReturn(ClientType.APPLICATION);

        sharedSubscriptionCache.put(CLIENT_ID_1, List.of(
                new TopicSubscription("/test/topic/1", 1, "g1"),
                new TopicSubscription("/test/topic/2", 1, "g2"),
                new TopicSubscription("/test/topic/3", 1)
        ));

        assertEquals(2, sharedSubscriptionCache.getSharedSubscriptionsMap().size());

        SharedSubscriptions sharedSubscriptions = sharedSubscriptionCache.getSharedSubscriptionsMap()
                .get(new TopicSharedSubscription("/test/topic/1", "g1"));
        assertTrue(sharedSubscriptions.getDeviceSubscriptions().isEmpty());
        assertEquals(1, sharedSubscriptions.getApplicationSubscriptions().size());

        sharedSubscriptions = sharedSubscriptionCache.getSharedSubscriptionsMap()
                .get(new TopicSharedSubscription("/test/topic/2", "g2"));
        assertTrue(sharedSubscriptions.getDeviceSubscriptions().isEmpty());
        assertEquals(1, sharedSubscriptions.getApplicationSubscriptions().size());

        sharedSubscriptionCache.put(CLIENT_ID_2, List.of(
                new TopicSubscription("/test/topic/1", 2, "g1"),
                new TopicSubscription("/test/topic/2", 0, "g2"),
                new TopicSubscription("/test/topic/3", 1)
        ));

        assertEquals(2, sharedSubscriptionCache.getSharedSubscriptionsMap().size());

        sharedSubscriptions = sharedSubscriptionCache.getSharedSubscriptionsMap()
                .get(new TopicSharedSubscription("/test/topic/2", "g2"));
        assertTrue(sharedSubscriptions.getDeviceSubscriptions().isEmpty());
        assertEquals(2, sharedSubscriptions.getApplicationSubscriptions().size());
    }

    @Test
    public void testPutSameClientAndSubscription() {
        when(clientSessionInfo1.getType()).thenReturn(ClientType.DEVICE);
        when(clientSessionInfo1.getClientId()).thenReturn(CLIENT_ID_1);

        sharedSubscriptionCache.put(CLIENT_ID_1, List.of(
                new TopicSubscription("/test/topic/1", 1, "g1")
        ));

        assertEquals(1, sharedSubscriptionCache.getSharedSubscriptionsMap().size());

        SharedSubscriptions sharedSubscriptions = sharedSubscriptionCache.getSharedSubscriptionsMap()
                .get(new TopicSharedSubscription("/test/topic/1", "g1"));
        assertTrue(sharedSubscriptions.getApplicationSubscriptions().isEmpty());
        assertEquals(1, sharedSubscriptions.getDeviceSubscriptions().size());

        sharedSubscriptionCache.put(CLIENT_ID_1, List.of(
                new TopicSubscription("/test/topic/1", 2, "g1")
        ));

        assertEquals(1, sharedSubscriptionCache.getSharedSubscriptionsMap().size());

        sharedSubscriptions = sharedSubscriptionCache.getSharedSubscriptionsMap()
                .get(new TopicSharedSubscription("/test/topic/1", "g1"));
        assertTrue(sharedSubscriptions.getApplicationSubscriptions().isEmpty());
        assertEquals(1, sharedSubscriptions.getDeviceSubscriptions().size());
    }

    @Test
    public void remove() {
        when(clientSessionInfo1.getType()).thenReturn(ClientType.DEVICE);
        when(clientSessionInfo1.getClientId()).thenReturn(CLIENT_ID_1);

        when(clientSessionInfo2.getType()).thenReturn(ClientType.APPLICATION);
        when(clientSessionInfo2.getClientId()).thenReturn(CLIENT_ID_2);

        sharedSubscriptionCache.put(CLIENT_ID_1, List.of(
                new TopicSubscription("/test/topic/1", 2, "g1"),
                new TopicSubscription("#", 0, "g2"),
                new TopicSubscription("/test/topic/+", 1, "g3")
        ));
        sharedSubscriptionCache.put(CLIENT_ID_2, List.of(
                new TopicSubscription("/test/topic/1", 1, "g1"),
                new TopicSubscription("/test/topic/+", 2, "g3")
        ));

        assertEquals(3, sharedSubscriptionCache.getSharedSubscriptionsMap().size());
        SharedSubscriptions sharedSubscriptions = sharedSubscriptionCache.getSharedSubscriptionsMap()
                .get(new TopicSharedSubscription("/test/topic/1", "g1"));
        assertEquals(1, sharedSubscriptions.getApplicationSubscriptions().size());
        assertEquals(1, sharedSubscriptions.getDeviceSubscriptions().size());
        sharedSubscriptions = sharedSubscriptionCache.getSharedSubscriptionsMap()
                .get(new TopicSharedSubscription("/test/topic/+", "g2"));
        assertNull(sharedSubscriptions);

        sharedSubscriptionCache.remove(CLIENT_ID_1, new TopicSubscription("#", 0, "g2"));
        assertEquals(2, sharedSubscriptionCache.getSharedSubscriptionsMap().size());

        when(clientSessionCache.getClientSessionInfo(CLIENT_ID_2)).thenReturn(null);
        sharedSubscriptionCache.remove(CLIENT_ID_2, new TopicSubscription("/test/topic/1", 0, "g1"));

        assertEquals(2, sharedSubscriptionCache.getSharedSubscriptionsMap().size());
        sharedSubscriptions = sharedSubscriptionCache.getSharedSubscriptionsMap()
                .get(new TopicSharedSubscription("/test/topic/1", "g1"));
        assertEquals(1, sharedSubscriptions.getDeviceSubscriptions().size());
        sharedSubscriptions = sharedSubscriptionCache.getSharedSubscriptionsMap()
                .get(new TopicSharedSubscription("/test/topic/+", "g3"));
        assertEquals(1, sharedSubscriptions.getDeviceSubscriptions().size());
    }

    @Test
    public void testGetNothing() {
        assertNull(sharedSubscriptionCache.get(null));
        assertNull(sharedSubscriptionCache.get(Set.of()));
    }

    @Test
    public void testGet() {
        when(clientSessionInfo1.getType()).thenReturn(ClientType.DEVICE);
        when(clientSessionInfo1.getClientId()).thenReturn(CLIENT_ID_1);

        when(clientSessionInfo2.getType()).thenReturn(ClientType.DEVICE);
        when(clientSessionInfo2.getClientId()).thenReturn(CLIENT_ID_2);

        sharedSubscriptionCache.put(CLIENT_ID_1, List.of(
                new TopicSubscription("/test/topic/1", 2, "g1"),
                new TopicSubscription("#", 0, "g2"),
                new TopicSubscription("/test/topic/+", 1, "g3")
        ));

        sharedSubscriptionCache.put(CLIENT_ID_2, List.of(
                new TopicSubscription("/test/topic/1", 1, "g1")
        ));

        SharedSubscriptions sharedSubscriptions = sharedSubscriptionCache.get(Set.of(
                new TopicSharedSubscription("/test/topic/1", "g1"),
                new TopicSharedSubscription("#", "g2"),
                new TopicSharedSubscription("/test/topic/+", "g3")
        ));

        assertEquals(2, sharedSubscriptions.getDeviceSubscriptions().size());
        for (Subscription subscription : sharedSubscriptions.getDeviceSubscriptions()) {
            assertEquals("/test/topic/1", subscription.getTopicFilter());
            if (subscription.getClientSessionInfo().getClientId().equals(CLIENT_ID_1)) {
                assertEquals(2, subscription.getQos());
            } else {
                assertEquals(1, subscription.getQos());
            }
        }
    }
}