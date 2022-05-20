package org.thingsboard.mqtt.broker.actors.client.service.subscription;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscription;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionTrie;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Slf4j
class SubscriptionServiceImplTest {

    SubscriptionTrie<ClientSubscription> subscriptionTrie;
    StatsManager statsManager;
    SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
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