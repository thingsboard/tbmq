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
package org.thingsboard.mqtt.broker.actors.client.service.subscription;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.subscription.ClientTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionPersistenceService;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionCacheService;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionProcessor;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class ClientSubscriptionServiceImplTest {

    SubscriptionPersistenceService subscriptionPersistenceService;
    SubscriptionService subscriptionService;
    SharedSubscriptionProcessor sharedSubscriptionProcessor;
    SharedSubscriptionCacheService sharedSubscriptionCacheService;
    StatsManager statsManager;
    ClientSubscriptionServiceImpl clientSubscriptionService;

    @Before
    public void setUp() {
        subscriptionPersistenceService = mock(SubscriptionPersistenceService.class);
        subscriptionService = mock(SubscriptionService.class);
        sharedSubscriptionProcessor = mock(SharedSubscriptionProcessor.class);
        sharedSubscriptionCacheService = mock(SharedSubscriptionCacheService.class);
        statsManager = mock(StatsManager.class);
        clientSubscriptionService = spy(new ClientSubscriptionServiceImpl(
                subscriptionPersistenceService,
                subscriptionService,
                sharedSubscriptionProcessor,
                sharedSubscriptionCacheService,
                statsManager));

        clientSubscriptionService.init(getClientTopicSubscriptions());
    }

    @Test
    public void givenClientTopicSubscriptions_whenInit_thenOk() {
        verify(subscriptionService, times(2)).subscribe(any(), any());
    }

    @Test
    public void givenClientTopicSubscriptionsAndSharedSubscriptions_whenGetClientSharedSubscriptions_thenReturnExpectedResult() {
        String clientId = "sharedClientId";
        getAndVerifyClientSubscriptionsForClient(clientId, 0);

        clientSubscriptionService.subscribeInternally(clientId, Set.of(getSharedTopicSubscription("topic11")));
        getAndVerifyClientSubscriptionsForClient(clientId, 1);

        clientSubscriptionService.subscribeInternally(clientId, Set.of(getSharedTopicSubscription("topic12")));
        getAndVerifyClientSubscriptionsForClient(clientId, 2);

        clientSubscriptionService.subscribeInternally(clientId, Set.of(getTopicSubscription("topic123")));
        getAndVerifyClientSubscriptionsForClient(clientId, 3);

        Set<TopicSharedSubscription> clientSharedSubscriptions = clientSubscriptionService.getClientSharedSubscriptions(clientId);

        assertEquals(2, clientSharedSubscriptions.size());
    }

    @Test
    public void givenClientSharedSubscriptions_whenGetClientSharedSubscriptions_thenReturnExpectedResult() {
        String clientId = "sharedClientId";
        getAndVerifyClientSubscriptionsForClient(clientId, 0);

        clientSubscriptionService.subscribeInternally(clientId, Set.of(getSharedTopicSubscription("topic11", 2)));
        getAndVerifyClientSubscriptionsForClient(clientId, 1);

        clientSubscriptionService.subscribeInternally(clientId, Set.of(getSharedTopicSubscription("topic11", 1)));
        getAndVerifyClientSubscriptionsForClient(clientId, 1);

        clientSubscriptionService.subscribeInternally(clientId, Set.of(getSharedTopicSubscription("topic12", 2)));
        getAndVerifyClientSubscriptionsForClient(clientId, 2);

        Set<TopicSharedSubscription> clientSharedSubscriptions = clientSubscriptionService.getClientSharedSubscriptions(clientId);

        assertEquals(2, clientSharedSubscriptions.size());
    }

    @Test
    public void givenClientTopicSubscriptionsAndNoSharedSubscriptions_whenGetClientSharedSubscriptions_thenReturnEmptySet() {
        String clientId = "sharedClientId";
        getAndVerifyClientSubscriptionsForClient(clientId, 0);

        clientSubscriptionService.subscribeInternally(clientId, Set.of(getTopicSubscription("topic123")));
        getAndVerifyClientSubscriptionsForClient(clientId, 1);

        Set<TopicSharedSubscription> clientSharedSubscriptions = clientSubscriptionService.getClientSharedSubscriptions(clientId);

        assertEquals(0, clientSharedSubscriptions.size());
    }

    @Test
    public void givenClientTopicSubscriptions_whenSubscribeAndUnsubscribe_thenOk() {
        String clientId = "clientId1";
        getAndVerifyClientSubscriptionsForClient(clientId, 1);

        clientSubscriptionService.subscribeInternally(clientId, Set.of(getTopicSubscription("topic11")));
        getAndVerifyClientSubscriptionsForClient(clientId, 2);

        clientSubscriptionService.unsubscribeInternally(clientId, Set.of("topic11"));
        Set<TopicSubscription> clientSubscriptions = getAndVerifyClientSubscriptionsForClient(clientId, 1);

        assertTrue(clientSubscriptions.contains(getTopicSubscription("topic1")));
    }

    @Test
    public void givenClientTopicSubscriptions_whenSubscribeAndUnsubscribeWithPersist_thenOk() {
        clientSubscriptionService.subscribeAndPersist("clientId1", Set.of(getTopicSubscription("topic11")));
        clientSubscriptionService.unsubscribeAndPersist("clientId1", Set.of("topic11"));

        Set<TopicSubscription> clientSubscriptions = getAndVerifyClientSubscriptionsForClient("clientId1", 1);
        assertTrue(clientSubscriptions.contains(getTopicSubscription("topic1")));
        verify(subscriptionPersistenceService, times(2)).persistClientSubscriptionsAsync(any(), any(), any());
    }

    @Test
    public void givenClientTopicSubscriptions_whenSubscribeAndPersist_thenOk() {
        clientSubscriptionService.subscribeAndPersist("clientId1", Set.of(getTopicSubscription("topic11")));

        Set<TopicSubscription> clientSubscriptions = getAndVerifyClientSubscriptionsForClient("clientId1", 2);

        verify(subscriptionPersistenceService, times(1)).persistClientSubscriptionsAsync(
                eq("clientId1"), eq(clientSubscriptions), any());
    }

    @Test
    public void givenClientTopicSubscriptions_whenSubscribeInternallyAndUnsubscribeAndPersist_thenOk() {
        clientSubscriptionService.subscribeInternally("clientId1", Set.of(getTopicSubscription("topic11")));
        clientSubscriptionService.unsubscribeAndPersist("clientId1", Set.of("topic1"));

        Set<TopicSubscription> clientSubscriptions = getAndVerifyClientSubscriptionsForClient("clientId1", 1);

        verify(subscriptionPersistenceService, times(1)).persistClientSubscriptionsAsync(
                eq("clientId1"), eq(clientSubscriptions), any());
    }

    @Test
    public void givenClientTopicSubscriptions_whenClearSubscriptionsAndPersist_thenOk() {
        clientSubscriptionService.clearSubscriptionsAndPersist("clientId1", null);

        getAndVerifyClientSubscriptionsForClient("clientId1", 0);
        verify(subscriptionPersistenceService, times(1)).persistClientSubscriptionsAsync(any(), eq(Collections.emptySet()), any());
    }

    @Test
    public void givenClientTopicSubscriptions_whenSubscribeAndGetClientSubscriptionsCount_thenReturnExpectedSum() {
        clientSubscriptionService.subscribeAndPersist(
                "clientId3",
                Sets.newHashSet(
                        getTopicSubscription("topic123"),
                        getTopicSubscription("topic321")));

        clientSubscriptionService.subscribeAndPersist(
                "clientId4",
                Sets.newHashSet(
                        getTopicSubscription("topic123"),
                        getTopicSubscription("topic321"),
                        getTopicSubscription("topic12345")));

        int clientSubscriptionsCount = clientSubscriptionService.getClientSubscriptionsCount();
        assertThat(clientSubscriptionsCount).isEqualTo(7);
    }

    private Set<TopicSubscription> getAndVerifyClientSubscriptionsForClient(String clientId, int expected) {
        Set<TopicSubscription> clientSubscriptions = clientSubscriptionService.getClientSubscriptions(clientId);
        assertEquals(expected, clientSubscriptions.size());
        return clientSubscriptions;
    }

    private Map<String, Set<TopicSubscription>> getClientTopicSubscriptions() {
        return Map.of(
                "clientId1", Sets.newHashSet(getTopicSubscription("topic1")),
                "clientId2", Sets.newHashSet(getTopicSubscription("topic2"))
        );
    }

    private TopicSubscription getTopicSubscription(String topic) {
        return new ClientTopicSubscription(topic, 1);
    }

    private TopicSubscription getSharedTopicSubscription(String topic) {
        return new ClientTopicSubscription(topic, 1, "sharedGroup");
    }

    private TopicSubscription getSharedTopicSubscription(String topic, int qos) {
        return new ClientTopicSubscription(topic, qos, "sharedGroup");
    }
}
