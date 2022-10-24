/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;

import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                getTopic("topic1", 0),
                getTopic("topic2", 1),
                getTopic("topic3", 2));
        doReturn(currentTopicSubscriptions).when(clientSubscriptionService).getClientSubscriptions("clientId");

        SubscriptionChangedEventMsg msg = new SubscriptionChangedEventMsg(Set.of(
                getTopic("topic2", 2),
                getTopic("topic3", 2),
                getTopic("topic4", 1)));
        subscriptionChangesManager.processSubscriptionChangedEvent("clientId", msg);

        verify(clientSubscriptionService, never()).clearSubscriptionsInternally(any());

        ArgumentCaptor<Collection<String>> unsubscribeTopicsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(clientSubscriptionService, times(1)).unsubscribeInternally(any(), unsubscribeTopicsCaptor.capture());
        Collection<String> unsubscribeTopics = unsubscribeTopicsCaptor.getValue();
        assertEquals(Set.of("topic1", "topic2"), unsubscribeTopics);

        ArgumentCaptor<Collection<TopicSubscription>> subscribeTopicsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(clientSubscriptionService, times(1)).subscribeInternally(any(), subscribeTopicsCaptor.capture());
        Collection<TopicSubscription> subscribeTopics = subscribeTopicsCaptor.getValue();
        assertEquals(
                Set.of(
                        new TopicSubscription("topic2", 2),
                        new TopicSubscription("topic4", 1)),
                subscribeTopics);
    }

    @Test
    public void givenCurrentTopicSubscriptions_whenProcessEmptySubscriptionChangedEvent_thenClearSubscriptions() {
        Set<TopicSubscription> currentTopicSubscriptions = Set.of(getTopic("topic1", 0));
        doReturn(currentTopicSubscriptions).when(clientSubscriptionService).getClientSubscriptions("clientId");

        SubscriptionChangedEventMsg msg = new SubscriptionChangedEventMsg(Set.of());
        subscriptionChangesManager.processSubscriptionChangedEvent("clientId", msg);

        verify(clientSubscriptionService, times(1)).clearSubscriptionsInternally("clientId");
        verify(clientSubscriptionService, never()).unsubscribeInternally(any(), any());
        verify(clientSubscriptionService, never()).subscribeInternally(any(), any());
    }

    private TopicSubscription getTopic(String topic, int qos) {
        return new TopicSubscription(topic, qos);
    }
}