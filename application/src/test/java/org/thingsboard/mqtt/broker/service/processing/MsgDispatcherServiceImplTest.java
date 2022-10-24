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
package org.thingsboard.mqtt.broker.service.processing;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionService;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.processing.downlink.DownLinkProxy;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscription;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionType;
import org.thingsboard.mqtt.broker.service.subscription.ValueWithTopicFilter;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscription;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionProcessingStrategyFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = MsgDispatcherServiceImpl.class)
public class MsgDispatcherServiceImplTest {

    static final String TOPIC = "topic";

    @MockBean
    SubscriptionService subscriptionService;
    @MockBean
    StatsManager statsManager;
    @MockBean
    MsgPersistenceManager msgPersistenceManager;
    @MockBean
    ClientSessionCache clientSessionCache;
    @MockBean
    DownLinkProxy downLinkProxy;
    @MockBean
    ClientLogger clientLogger;
    @MockBean
    PublishMsgQueuePublisher publishMsgQueuePublisher;
    @MockBean
    SharedSubscriptionProcessingStrategyFactory sharedSubscriptionProcessingStrategyFactory;
    @SpyBean
    MsgDispatcherServiceImpl msgDispatcherService;

    ClientSession clientSession;

    @Before
    public void setUp() {
        clientSession = mock(ClientSession.class);
    }

    @Test
    public void testConvertClientSubscriptionsToSubscriptionsByType() {
        when(clientSessionCache.getClientSession(any())).thenReturn(clientSession);

        List<ValueWithTopicFilter<ClientSubscription>> clientSubscriptionWithTopicFilters =
                List.of(
                        newValueWithTopicFilter("client1", 0, "group1"),
                        newValueWithTopicFilter("client2", 1, "group1"),
                        newValueWithTopicFilter("client3", 1, "group2"),
                        newValueWithTopicFilter("client3", 2, "group2"),
                        newValueWithTopicFilter("client4", 1, "group2"),
                        newValueWithTopicFilter("client5", 1, null),
                        newValueWithTopicFilter("client6", 2, null)
                );

        Map<SubscriptionType, List<Subscription>> subscriptionsByType = msgDispatcherService.collectToSubscriptionsByType(clientSubscriptionWithTopicFilters);
        assertEquals(2, subscriptionsByType.size());

        List<Subscription> commonSubscriptions = subscriptionsByType.get(SubscriptionType.COMMON);
        assertEquals(2, commonSubscriptions.size());

        List<Subscription> sharedSubscriptions = subscriptionsByType.get(SubscriptionType.SHARED);
        assertEquals(4, sharedSubscriptions.size());

        Assert.assertTrue(commonSubscriptions.containsAll(List.of(
                newSubscription(1),
                newSubscription(2)
        )));

        Assert.assertTrue(sharedSubscriptions.containsAll(List.of(
                newSubscription(0, "group1"),
                newSubscription(1, "group1"),
                newSubscription(2, "group2"),
                newSubscription(1, "group2")
        )));
    }

    @Test
    public void testConvertToSharedSubscriptionList() {
        List<Subscription> subscriptions = List.of(
                newSubscription(0, "group1"),
                newSubscription(1, "group1"),
                newSubscription(2, "group2"),
                newSubscription(0, "group2"),
                newSubscription(1, "group3"),
                newSubscription(2, "group3")
        );
        List<SharedSubscription> sharedSubscriptionList = msgDispatcherService.toSharedSubscriptionList(subscriptions);
        assertEquals(3, sharedSubscriptionList.size());
        sharedSubscriptionList.forEach(sharedSubscription -> assertEquals(2, sharedSubscription.getSubscriptions().size()));
    }

    @Test
    public void testFilterHighestQosClientSubscriptions5() {
        List<ValueWithTopicFilter<ClientSubscription>> before = List.of(
                newValueWithTopicFilter("clientId1", 0, "g1", "+/test/+"),
                newValueWithTopicFilter("clientId2", 1, "g1", "#"),
                newValueWithTopicFilter("clientId3", 2, null, "topic/+/+"),
                newValueWithTopicFilter("clientId1", 2, null, "+/+/res")
        );
        assertEquals(4, before.size());

        Collection<ValueWithTopicFilter<ClientSubscription>> result = msgDispatcherService.filterHighestQosClientSubscriptions(before);
        assertEquals(4, result.size());
    }

    @Test
    public void testFilterHighestQosClientSubscriptions4() {
        List<ValueWithTopicFilter<ClientSubscription>> before = List.of(
                newValueWithTopicFilter("clientId1", 0, "g1", "+/test/+"),
                newValueWithTopicFilter("clientId2", 0, "g2", "#"),
                newValueWithTopicFilter("clientId3", 2, "g2", "topic/+/+"),
                newValueWithTopicFilter("clientId1", 2, "g1", "+/+/res"),
                newValueWithTopicFilter("clientId2", 1, "g3", "topic/test/+"),
                newValueWithTopicFilter("clientId3", 0, "g3", "topic/+/res")
        );
        assertEquals(6, before.size());

        Collection<ValueWithTopicFilter<ClientSubscription>> result = msgDispatcherService.filterHighestQosClientSubscriptions(before);
        assertEquals(3, result.size());

        assertTrue(result.containsAll(
                List.of(
                        newValueWithTopicFilter("clientId3", 2, "g2", "topic/+/+"),
                        newValueWithTopicFilter("clientId1", 2, "g1", "+/+/res"),
                        newValueWithTopicFilter("clientId2", 1, "g3", "topic/test/+")
                )
        ));
    }

    @Test
    public void testFilterHighestQosClientSubscriptions3() {
        List<ValueWithTopicFilter<ClientSubscription>> before = List.of(
                newValueWithTopicFilter("clientId1", 0, null, "+/test/+"),
                newValueWithTopicFilter("clientId2", 0, null, "#"),
                newValueWithTopicFilter("clientId3", 2, null, "topic/+/+"),
                newValueWithTopicFilter("clientId1", 2, null, "+/+/res"),
                newValueWithTopicFilter("clientId2", 1, null, "topic/test/+"),
                newValueWithTopicFilter("clientId3", 0, null, "topic/+/res")
        );
        assertEquals(6, before.size());

        Collection<ValueWithTopicFilter<ClientSubscription>> result = msgDispatcherService.filterHighestQosClientSubscriptions(before);
        assertEquals(3, result.size());

        assertTrue(result.containsAll(
                List.of(
                        newValueWithTopicFilter("clientId3", 2, null, "topic/+/+"),
                        newValueWithTopicFilter("clientId1", 2, null, "+/+/res"),
                        newValueWithTopicFilter("clientId2", 1, null, "topic/test/+")
                )
        ));
    }

    @Test
    public void testFilterHighestQosClientSubscriptions2() {
        List<ValueWithTopicFilter<ClientSubscription>> before = List.of(
                newValueWithTopicFilter("clientId1", 1, "g1", "+/test/+"),
                newValueWithTopicFilter("clientId2", 1, "g1", "#"),
                newValueWithTopicFilter("clientId3", 1, "g2", "topic/+/+"),
                newValueWithTopicFilter("clientId4", 1, "g2", "+/+/res"),
                newValueWithTopicFilter("clientId5", 1, "g3", "topic/test/+"),
                newValueWithTopicFilter("clientId6", 1, "g3", "topic/+/res")
        );
        assertEquals(6, before.size());

        Collection<ValueWithTopicFilter<ClientSubscription>> result = msgDispatcherService.filterHighestQosClientSubscriptions(before);
        assertEquals(6, result.size());
    }

    @Test
    public void testFilterHighestQosClientSubscriptions1() {
        List<ValueWithTopicFilter<ClientSubscription>> before = List.of(
                newValueWithTopicFilter("clientId1", 1, null, "+/test/+"),
                newValueWithTopicFilter("clientId2", 1, null, "#"),
                newValueWithTopicFilter("clientId3", 1, null, "topic/+/+"),
                newValueWithTopicFilter("clientId4", 1, null, "+/+/res"),
                newValueWithTopicFilter("clientId5", 1, null, "topic/test/+"),
                newValueWithTopicFilter("clientId6", 1, null, "topic/+/res")
        );
        assertEquals(6, before.size());

        Collection<ValueWithTopicFilter<ClientSubscription>> result = msgDispatcherService.filterHighestQosClientSubscriptions(before);
        assertEquals(6, result.size());
    }

    @Test
    public void testFindAnyConnectedSubscription() {
        List<Subscription> subscriptions = List.of(
                new Subscription("topic1", 1, new ClientSession(false, null)),
                new Subscription("topic2", 0, new ClientSession(false, null)),
                new Subscription("topic3", 2, new ClientSession(false, null)),
                new Subscription("topic4", 0, new ClientSession(false, null)),
                new Subscription("topic5", 1, new ClientSession(false, null))
        );
        Subscription subscription = msgDispatcherService.findAnyConnectedSubscription(subscriptions);
        assertNull(subscription);

        subscriptions = List.of(
                new Subscription("topic1", 1, new ClientSession(false, null)),
                new Subscription("topic2", 0, new ClientSession(false, null)),
                new Subscription("topic3", 2, new ClientSession(true, null)),
                new Subscription("topic4", 0, new ClientSession(false, null)),
                new Subscription("topic5", 1, new ClientSession(false, null))
        );
        subscription = msgDispatcherService.findAnyConnectedSubscription(subscriptions);
        assertEquals("topic3", subscription.getTopicFilter());
    }

    private Subscription newSubscription(int mqttQoSValue) {
        return newSubscription(mqttQoSValue, null);
    }

    private Subscription newSubscription(int mqttQoSValue, String shareName) {
        return new Subscription(TOPIC, mqttQoSValue, clientSession, shareName);
    }

    private ValueWithTopicFilter<ClientSubscription> newValueWithTopicFilter(String clientId, int qos, String shareName) {
        return newValueWithTopicFilter(clientId, qos, shareName, TOPIC);
    }

    private ValueWithTopicFilter<ClientSubscription> newValueWithTopicFilter(String clientId, int qos, String shareName, String topic) {
        return new ValueWithTopicFilter<>(newClientSubscription(clientId, qos, shareName), topic);
    }

    private ClientSubscription newClientSubscription(String clientId, int qos, String shareName) {
        return new ClientSubscription(clientId, qos, shareName);
    }
}