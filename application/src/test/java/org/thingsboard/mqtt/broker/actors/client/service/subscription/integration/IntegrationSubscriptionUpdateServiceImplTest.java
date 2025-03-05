/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.actors.client.service.subscription.integration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.common.data.subscription.IntegrationTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class IntegrationSubscriptionUpdateServiceImplTest {

    ClientSubscriptionService clientSubscriptionService;
    IntegrationSubscriptionUpdateService integrationSubscriptionUpdateService;

    @Before
    public void setUp() {
        clientSubscriptionService = mock(ClientSubscriptionService.class);
        integrationSubscriptionUpdateService = spy(new IntegrationSubscriptionUpdateServiceImpl(clientSubscriptionService));
    }

    @Test
    public void givenCurrentTopicSubscriptions_whenProcessSubscriptionsUpdate_thenUpdateSubscriptions() {
        Set<TopicSubscription> currentTopicSubscriptions = Set.of(
                getTopicSubs("topic1"),
                getTopicSubs("topic2"),
                getTopicSubs("topic3"));
        doReturn(currentTopicSubscriptions).when(clientSubscriptionService).getClientSubscriptions("integrationId");

        Set<TopicSubscription> subscriptions = Set.of(
                getTopicSubs("topic2"),
                getTopicSubs("topic3"),
                getTopicSubs("topic4"));
        integrationSubscriptionUpdateService.processSubscriptionsUpdate("integrationId", subscriptions);

        verify(clientSubscriptionService, never()).clearSubscriptionsAndPersist(any());

        ArgumentCaptor<Collection<String>> unsubscribeTopicsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(clientSubscriptionService, times(1)).unsubscribeAndPersist(any(), unsubscribeTopicsCaptor.capture());
        Collection<String> unsubscribeTopics = unsubscribeTopicsCaptor.getValue();
        assertEquals(Set.of("topic1"), unsubscribeTopics);

        ArgumentCaptor<Collection<TopicSubscription>> subscribeTopicsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(clientSubscriptionService, times(1)).subscribeAndPersist(any(), subscribeTopicsCaptor.capture());

        List<String> subscribeTopics = subscribeTopicsCaptor.getValue().stream().map(TopicSubscription::getTopicFilter).toList();
        assertTrue(subscribeTopics.containsAll(List.of("topic2", "topic3", "topic4")));
    }

    @Test
    public void givenCurrentTopicSubscription_whenProcessSubscriptionsUpdate_thenUpdateSubscription() {
        Set<TopicSubscription> currentTopicSubscriptions = Set.of(getTopicSubs("topic1"));
        doReturn(currentTopicSubscriptions).when(clientSubscriptionService).getClientSubscriptions("integrationId");

        Set<TopicSubscription> subscriptions = Set.of(getTopicSubs("topic1"));
        integrationSubscriptionUpdateService.processSubscriptionsUpdate("integrationId", subscriptions);

        verify(clientSubscriptionService, never()).clearSubscriptionsAndPersist(any());
        verify(clientSubscriptionService, never()).unsubscribeAndPersist(any(), any());
        verify(clientSubscriptionService, times(1)).subscribeAndPersist(any(), any());
    }

    @Test
    public void givenCurrentTopicSubscriptions_whenProcessSubscriptionsUpdate_thenClearSubscriptions() {
        Set<TopicSubscription> currentTopicSubscriptions = Set.of(getTopicSubs("topic1"));
        doReturn(currentTopicSubscriptions).when(clientSubscriptionService).getClientSubscriptions("integrationId");

        integrationSubscriptionUpdateService.processSubscriptionsUpdate("integrationId", Set.of());

        verify(clientSubscriptionService, times(1)).clearSubscriptionsAndPersist("integrationId");
        verify(clientSubscriptionService, never()).unsubscribeAndPersist(any(), any());
        verify(clientSubscriptionService, never()).subscribeAndPersist(any(), any());
    }

    private TopicSubscription getTopicSubs(String topic) {
        return new IntegrationTopicSubscription(topic);
    }

}
