/**
 * Copyright © 2016-2024 The Thingsboard Authors
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

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscription;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionTrie;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class SubscriptionServiceImplTest {

    SubscriptionTrie<ClientSubscription> subscriptionTrie;
    StatsManager statsManager;
    SubscriptionService subscriptionService;

    @Before
    public void setUp() {
        subscriptionTrie = mock(SubscriptionTrie.class);
        statsManager = mock(StatsManager.class);
        subscriptionService = spy(new SubscriptionServiceImpl(
                subscriptionTrie,
                statsManager));
    }

    @Test
    public void givenClientTopicSubscriptions_whenSubscribe_thenOk() {
        subscriptionService.subscribe("clientId", Set.of(
                new TopicSubscription("topic1", 1),
                new TopicSubscription("topic2", 2)
        ));
        verify(subscriptionTrie, times(2)).put(any(), any());
    }

    @Test
    public void givenClientTopics_whenUnsubscribe_thenOk() {
        subscriptionService.unsubscribe("clientId", Set.of("topic1", "topic2"));
        verify(subscriptionTrie, times(2)).delete(any(), any());
    }
}