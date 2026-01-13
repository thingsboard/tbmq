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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.actors.client.messages.SubscriptionChangedEventMsg;
import org.thingsboard.mqtt.broker.common.data.subscription.ClientTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class SubscriptionChangesManagerImplTest {

    ClientSubscriptionService clientSubscriptionService;
    SubscriptionChangesManager subscriptionChangesManager;

    @Before
    public void setUp() {
        clientSubscriptionService = mock(ClientSubscriptionServiceImpl.class);
        subscriptionChangesManager = spy(new SubscriptionChangesManagerImpl(clientSubscriptionService));
    }

    @Test
    public void givenCurrentTopicSubscriptions_whenProcessSubscriptionChangedEvent_thenUpdateSubscriptions() {
        Set<TopicSubscription> currentTopicSubscriptions = Set.of(
                getTopicSubs("topic1", 0),
                getTopicSubs("topic2", 1, "s1", new SubscriptionOptions(), -1),
                getTopicSubs("topic3", 2, null, new SubscriptionOptions(), 1));
        doReturn(currentTopicSubscriptions).when(clientSubscriptionService).getClientSubscriptions("clientId");

        SubscriptionChangedEventMsg msg = new SubscriptionChangedEventMsg(Set.of(
                getTopicSubs("topic2", 2, null, new SubscriptionOptions(), 2),
                getTopicSubs("topic3", 2, "s2", new SubscriptionOptions(true, false, SubscriptionOptions.RetainHandlingPolicy.DONT_SEND_AT_SUBSCRIBE), 3),
                getTopicSubs("topic4", 1)));
        subscriptionChangesManager.processSubscriptionChangedEvent("clientId", msg);

        verify(clientSubscriptionService, never()).clearSubscriptionsInternally(any());

        ArgumentCaptor<Collection<String>> unsubscribeTopicsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(clientSubscriptionService, times(1)).unsubscribeInternally(any(), unsubscribeTopicsCaptor.capture());
        Collection<String> unsubscribeTopics = unsubscribeTopicsCaptor.getValue();
        assertEquals(Set.of("topic1"), unsubscribeTopics);

        ArgumentCaptor<Collection<TopicSubscription>> subscribeTopicsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(clientSubscriptionService, times(1)).subscribeInternally(any(), subscribeTopicsCaptor.capture());
        List<TopicSubscription> subscribeTopics = new ArrayList<>(subscribeTopicsCaptor.getValue());
        assertEquals("topic2", subscribeTopics.get(0).getTopicFilter());
        assertEquals(2, subscribeTopics.get(0).getQos());
        assertNull(subscribeTopics.get(0).getShareName());
        assertEquals(2, subscribeTopics.get(0).getSubscriptionId());

        assertEquals("topic3", subscribeTopics.get(1).getTopicFilter());
        assertEquals(2, subscribeTopics.get(1).getQos());
        assertEquals("s2", subscribeTopics.get(1).getShareName());
        assertTrue(subscribeTopics.get(1).getOptions().isNoLocal());
        assertEquals(3, subscribeTopics.get(1).getSubscriptionId());

        assertEquals("topic4", subscribeTopics.get(2).getTopicFilter());
        assertEquals(1, subscribeTopics.get(2).getQos());
        assertEquals(-1, subscribeTopics.get(2).getSubscriptionId());
    }

    @Test
    public void givenCurrentTopicSubscription_whenProcessSubscriptionChangedEvent_thenUpdateSubscription() {
        Set<TopicSubscription> currentTopicSubscriptions = Set.of(getTopicSubs("topic1", 0));
        doReturn(currentTopicSubscriptions).when(clientSubscriptionService).getClientSubscriptions("clientId");

        SubscriptionChangedEventMsg msg = new SubscriptionChangedEventMsg(Set.of(getTopicSubs("topic1", 1)));
        subscriptionChangesManager.processSubscriptionChangedEvent("clientId", msg);

        verify(clientSubscriptionService, never()).clearSubscriptionsInternally(any());
        verify(clientSubscriptionService, never()).unsubscribeInternally(any(), any());
        verify(clientSubscriptionService, times(1)).subscribeInternally(any(), any());
    }

    @Test
    public void givenCurrentTopicSubscriptions_whenProcessEmptySubscriptionChangedEvent_thenClearSubscriptions() {
        Set<TopicSubscription> currentTopicSubscriptions = Set.of(getTopicSubs("topic1", 0));
        doReturn(currentTopicSubscriptions).when(clientSubscriptionService).getClientSubscriptions("clientId");

        SubscriptionChangedEventMsg msg = new SubscriptionChangedEventMsg(Set.of());
        subscriptionChangesManager.processSubscriptionChangedEvent("clientId", msg);

        verify(clientSubscriptionService, times(1)).clearSubscriptionsInternally("clientId");
        verify(clientSubscriptionService, never()).unsubscribeInternally(any(), any());
        verify(clientSubscriptionService, never()).subscribeInternally(any(), any());
    }

    private TopicSubscription getTopicSubs(String topic, int qos) {
        return new ClientTopicSubscription(topic, qos);
    }

    private TopicSubscription getTopicSubs(String topic, int qos, String shareName, SubscriptionOptions options, int subscriptionId) {
        return new ClientTopicSubscription(topic, qos, shareName, options, subscriptionId);
    }
}
