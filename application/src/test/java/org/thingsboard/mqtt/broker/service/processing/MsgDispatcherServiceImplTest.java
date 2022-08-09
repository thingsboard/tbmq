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

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
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
        when(clientSession.getSessionInfo()).thenReturn(null);

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

        Map<SubscriptionType, List<Subscription>> subscriptionsByType = msgDispatcherService.convertToSubscriptionsByType(clientSubscriptionWithTopicFilters);
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

    private Subscription newSubscription(int mqttQoSValue) {
        return newSubscription(mqttQoSValue, null);
    }

    private Subscription newSubscription(int mqttQoSValue, String shareName) {
        return new Subscription(TOPIC, mqttQoSValue, null, shareName);
    }

    private ValueWithTopicFilter<ClientSubscription> newValueWithTopicFilter(String clientId, int qos, String shareName) {
        return new ValueWithTopicFilter<>(newClientSubscription(clientId, qos, shareName), TOPIC);
    }

    private ClientSubscription newClientSubscription(String clientId, int qos, String shareName) {
        return new ClientSubscription(clientId, qos, shareName);
    }
}