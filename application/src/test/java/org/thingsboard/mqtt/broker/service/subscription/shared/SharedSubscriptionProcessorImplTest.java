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
package org.thingsboard.mqtt.broker.service.subscription.shared;

import com.google.common.collect.Iterables;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ClientSession;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory.getClientInfo;
import static org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory.getConnectionInfo;

@RunWith(MockitoJUnitRunner.class)
public class SharedSubscriptionProcessorImplTest {

    SharedSubscriptionProcessorImpl subscriptionProcessor;

    @Before
    public void setUp() {
        subscriptionProcessor = new SharedSubscriptionProcessorImpl();
    }

    @Test
    public void testProcessRoundRobin() {
        UUID sessionId1 = UUID.randomUUID();
        UUID sessionId2 = UUID.randomUUID();
        SessionInfo expectedSessionInfo1 = getSessionInfo(sessionId1, "clientId1");
        SessionInfo expectedSessionInfo2 = getSessionInfo(sessionId2, "clientId2");

        List<Subscription> subscriptions = getSubscriptions(expectedSessionInfo1, expectedSessionInfo2);

        SharedSubscription sharedSubscription = getSharedSubscription(subscriptions);

        Subscription subscription1 = subscriptionProcessor.processRoundRobin(sharedSubscription);
        Subscription subscription2 = subscriptionProcessor.processRoundRobin(sharedSubscription);

        Assert.assertEquals(new HashSet<>(Set.of(subscription1, subscription2)), new HashSet<>(subscriptions));

        Subscription subscription3 = subscriptionProcessor.processRoundRobin(sharedSubscription);
        Subscription subscription4 = subscriptionProcessor.processRoundRobin(sharedSubscription);

        Assert.assertEquals(subscription1, subscription3);
        Assert.assertEquals(subscription2, subscription4);
    }

    @Test
    public void testObjectsEquals() {
        Assert.assertEquals(newTopicAndGroup(), newTopicAndGroup());

        UUID sessionId = UUID.randomUUID();
        SessionInfo expectedSessionInfo1 = getSessionInfo(sessionId, "clientId");
        SessionInfo expectedSessionInfo2 = getSessionInfo(sessionId, "clientId");

        Assert.assertEquals(expectedSessionInfo1, expectedSessionInfo2);

        List<Subscription> subscriptions1 = getSubscriptions(expectedSessionInfo1, expectedSessionInfo2);
        List<Subscription> subscriptions2 = getSubscriptions(expectedSessionInfo1, expectedSessionInfo2);

        SharedSubscription sharedSubscription1 = getSharedSubscription(subscriptions1);
        SharedSubscription sharedSubscription2 = getSharedSubscription(subscriptions2);

        Assert.assertEquals(sharedSubscription1, sharedSubscription2);
    }

    @Test
    public void testGetOneSubscription() {
        UUID sessionId1 = UUID.randomUUID();
        UUID sessionId2 = UUID.randomUUID();
        UUID sessionId3 = UUID.randomUUID();

        SessionInfo expectedSessionInfo1 = getSessionInfo(sessionId1, "clientId1");
        SessionInfo expectedSessionInfo2 = getSessionInfo(sessionId2, "clientId2");
        SessionInfo expectedSessionInfo3 = getSessionInfo(sessionId3, "clientId3");

        ClientSession clientSession1 = new ClientSession(false, expectedSessionInfo1);
        ClientSession clientSession2 = new ClientSession(false, expectedSessionInfo2);
        ClientSession clientSession3 = new ClientSession(true, expectedSessionInfo3);

        List<Subscription> subscriptions = getSubscriptions(clientSession1, clientSession2, clientSession3);

        Iterator<Subscription> iterator = Iterables.cycle(subscriptions).iterator();
        Subscription subscription = subscriptionProcessor.getOneSubscription(iterator);
        Assert.assertEquals(sessionId3, subscription.getClientSession().getSessionInfo().getSessionId());
    }

    private List<Subscription> getSubscriptions(ClientSession clientSession1, ClientSession clientSession2, ClientSession clientSession3) {
        return List.of(
                new Subscription("topic1", 1, clientSession1),
                new Subscription("topic2", 2, clientSession2),
                new Subscription("topic3", 0, clientSession3)
        );
    }

    private List<Subscription> getSubscriptions(SessionInfo expectedSessionInfo1, SessionInfo expectedSessionInfo2) {
        return List.of(
                new Subscription("topic1", 1, new ClientSession(true, expectedSessionInfo1)),
                new Subscription("topic2", 2, new ClientSession(true, expectedSessionInfo2))
        );
    }

    private SessionInfo getSessionInfo(UUID sessionId, String clientId) {
        return ClientSessionInfoFactory.getSessionInfo(
                sessionId,
                true,
                "SERVICE_ID",
                getClientInfo(clientId),
                getConnectionInfo(1000, 1000),
                0);
    }

    private TopicSharedSubscription newTopicAndGroup() {
        return new TopicSharedSubscription("topic", "group");
    }

    private SharedSubscription getSharedSubscription(List<Subscription> subscriptions) {
        return new SharedSubscription(new TopicSharedSubscription("topic", "group"), subscriptions);
    }
}