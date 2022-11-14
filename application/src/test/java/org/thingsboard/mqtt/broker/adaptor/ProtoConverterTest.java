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
package org.thingsboard.mqtt.broker.adaptor;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.ConnectionInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@RunWith(MockitoJUnitRunner.class)
public class ProtoConverterTest {

    @Test
    public void givenSessionInfo_whenCheckForPersistence_thenOk() {
        SessionInfo sessionInfo = newSessionInfo(true, 5);
        Assert.assertTrue(sessionInfo.isPersistent());
        sessionInfo = newSessionInfo(false, 5);
        Assert.assertTrue(sessionInfo.isPersistent());
        sessionInfo = newSessionInfo(true, 0);
        Assert.assertFalse(sessionInfo.isPersistent());
        sessionInfo = newSessionInfo(false, 0);
        Assert.assertTrue(sessionInfo.isPersistent());
        sessionInfo = newSessionInfo(true, null);
        Assert.assertFalse(sessionInfo.isPersistent());
        sessionInfo = newSessionInfo(false, null);
        Assert.assertTrue(sessionInfo.isPersistent());

        sessionInfo = newSessionInfo(true, 0);
        Assert.assertTrue(sessionInfo.isCleanSession());
        sessionInfo = newSessionInfo(true, null);
        Assert.assertTrue(sessionInfo.isCleanSession());
        sessionInfo = newSessionInfo(true, 5);
        Assert.assertFalse(sessionInfo.isCleanSession());

        sessionInfo = newSessionInfo(false, 0);
        Assert.assertTrue(sessionInfo.isNotCleanSession());
        sessionInfo = newSessionInfo(false, null);
        Assert.assertTrue(sessionInfo.isNotCleanSession());
        sessionInfo = newSessionInfo(false, 5);
        Assert.assertFalse(sessionInfo.isNotCleanSession());
    }

    @Test
    public void givenSessionInfo_whenConvertToProtoAndBackWithSessionExpiryIntervalNull_thenOk() {
        SessionInfo sessionInfoConverted = convertSessionInfo(null);
        Assert.assertNull(sessionInfoConverted.getSessionExpiryInterval());
    }

    @Test
    public void givenSessionInfo_whenConvertToProtoAndBackWithSessionExpiryIntervalNotNull_thenOk() {
        SessionInfo sessionInfoConverted = convertSessionInfo(5);
        Assert.assertNotNull(sessionInfoConverted.getSessionExpiryInterval());
    }

    private static SessionInfo convertSessionInfo(Integer sessionExpiryInterval) {
        SessionInfo sessionInfo = newSessionInfo(true, sessionExpiryInterval);

        QueueProtos.SessionInfoProto sessionInfoProto = ProtoConverter.convertToSessionInfoProto(sessionInfo);
        SessionInfo sessionInfoConverted = ProtoConverter.convertToSessionInfo(sessionInfoProto);

        Assert.assertEquals(sessionInfo, sessionInfoConverted);
        return sessionInfoConverted;
    }

    private static SessionInfo newSessionInfo(boolean cleanStart, Integer sessionExpiryInterval) {
        return SessionInfo.builder()
                .serviceId("serviceId")
                .sessionId(UUID.randomUUID())
                .cleanStart(cleanStart)
                .sessionExpiryInterval(sessionExpiryInterval)
                .clientInfo(ClientInfo.builder()
                        .clientId("clientId")
                        .type(ClientType.DEVICE)
                        .build())
                .connectionInfo(ConnectionInfo.builder()
                        .connectedAt(10)
                        .disconnectedAt(20)
                        .keepAlive(100)
                        .build())
                .build();
    }

    @Test
    public void givenTopicSubscriptions_whenConvertToProtoAndBack_thenOk() {
        Set<TopicSubscription> input = Set.of(
                new TopicSubscription("topic1", 0, "name1"),
                new TopicSubscription("topic2", 1)
        );

        QueueProtos.ClientSubscriptionsProto clientSubscriptionsProto = ProtoConverter.convertToClientSubscriptionsProto(input);

        QueueProtos.TopicSubscriptionProto topicSubscriptionProto = clientSubscriptionsProto.getSubscriptionsList()
                .stream()
                .filter(tsp -> tsp.getShareName().isEmpty())
                .findFirst()
                .orElse(null);
        assertNotNull(topicSubscriptionProto);

        Set<TopicSubscription> output = ProtoConverter.convertToClientSubscriptions(clientSubscriptionsProto);

        TopicSubscription topicSubscription = output
                .stream()
                .filter(ts -> ts.getShareName() == null)
                .findFirst()
                .orElse(null);
        assertNotNull(topicSubscription);

        assertEquals(input, output);
    }
}