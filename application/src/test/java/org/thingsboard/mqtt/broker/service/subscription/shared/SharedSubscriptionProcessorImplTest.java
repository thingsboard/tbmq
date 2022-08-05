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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory.getClientInfo;
import static org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory.getConnectionInfo;

class SharedSubscriptionProcessorImplTest {

    SharedSubscriptionProcessorImpl subscriptionProcessor;

    @BeforeEach
    void setUp() {
        subscriptionProcessor = new SharedSubscriptionProcessorImpl();
    }

    @Test
    void testProcessRoundRobin() {
        UUID sessionId1 = UUID.randomUUID();
        UUID sessionId2 = UUID.randomUUID();
        SessionInfo expectedSessionInfo1 = getSessionInfo(sessionId1, "clientId1");
        SessionInfo expectedSessionInfo2 = getSessionInfo(sessionId2, "clientId2");

        List<Subscription> subscriptions = getSubscriptions(expectedSessionInfo1, expectedSessionInfo2);

        SharedSubscription sharedSubscription = getSharedSubscription(subscriptions);

        Subscription subscription1 = subscriptionProcessor.processRoundRobin(sharedSubscription);
        Subscription subscription2 = subscriptionProcessor.processRoundRobin(sharedSubscription);

        assertEquals(new HashSet<>(Set.of(subscription1, subscription2)), new HashSet<>(subscriptions));

        Subscription subscription3 = subscriptionProcessor.processRoundRobin(sharedSubscription);
        Subscription subscription4 = subscriptionProcessor.processRoundRobin(sharedSubscription);

        assertEquals(subscription1, subscription3);
        assertEquals(subscription2, subscription4);
    }

    @Test
    void testObjectsEquals() {
        assertEquals(newTopicAndGroup(), newTopicAndGroup());

        UUID sessionId = UUID.randomUUID();
        SessionInfo expectedSessionInfo1 = getSessionInfo(sessionId, "clientId");
        SessionInfo expectedSessionInfo2 = getSessionInfo(sessionId, "clientId");

        assertEquals(expectedSessionInfo1, expectedSessionInfo2);

        List<Subscription> subscriptions1 = getSubscriptions(expectedSessionInfo1, expectedSessionInfo2);
        List<Subscription> subscriptions2 = getSubscriptions(expectedSessionInfo1, expectedSessionInfo2);

        SharedSubscription sharedSubscription1 = getSharedSubscription(subscriptions1);
        SharedSubscription sharedSubscription2 = getSharedSubscription(subscriptions2);

        assertEquals(sharedSubscription1, sharedSubscription2);
    }

    private List<Subscription> getSubscriptions(SessionInfo expectedSessionInfo1, SessionInfo expectedSessionInfo2) {
        return List.of(
                new Subscription(1, expectedSessionInfo1),
                new Subscription(2, expectedSessionInfo2)
        );
    }

    private SessionInfo getSessionInfo(UUID sessionId, String clientId) {
        return ClientSessionInfoFactory.getSessionInfo(
                sessionId,
                false,
                "SERVICE_ID",
                getClientInfo(clientId),
                getConnectionInfo(1000, 1000));
    }

    private SharedSubscriptionProcessorImpl.SharedTopicAndGroup newTopicAndGroup() {
        return new SharedSubscriptionProcessorImpl.SharedTopicAndGroup("topic", "group");
    }

    private SharedSubscription getSharedSubscription(List<Subscription> subscriptions) {
        return new SharedSubscription("topic", "group", subscriptions);
    }
}