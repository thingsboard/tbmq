package org.thingsboard.mqtt.broker.actors.client.service.subscription;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionPersistenceService;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Slf4j
class ClientSubscriptionServiceImplTest {

    SubscriptionPersistenceService subscriptionPersistenceService;
    SubscriptionService subscriptionService;
    StatsManager statsManager;
    ClientSubscriptionServiceImpl clientSubscriptionService;

    @BeforeEach
    void setUp() {
        subscriptionPersistenceService = mock(SubscriptionPersistenceService.class);
        subscriptionService = mock(SubscriptionService.class);
        statsManager = mock(StatsManager.class);
        clientSubscriptionService = spy(new ClientSubscriptionServiceImpl(
                subscriptionPersistenceService,
                subscriptionService,
                statsManager));

        clientSubscriptionService.init(getClientTopicSubscriptions());
    }

    @Test
    public void givenClientTopicSubscriptions_whenInit_thenOk() {
        verify(subscriptionService, times(2)).subscribe(any(), any());
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
    public void givenClientTopicSubscriptions_whenClearSubscriptionsAndPersist_thenOk() {
        clientSubscriptionService.clearSubscriptionsAndPersist("clientId1", null);

        getAndVerifyClientSubscriptionsForClient("clientId1", 0);
        verify(subscriptionPersistenceService, times(1)).persistClientSubscriptionsAsync(any(), eq(Collections.emptySet()), any());
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
        return new TopicSubscription(topic, 1);
    }
}