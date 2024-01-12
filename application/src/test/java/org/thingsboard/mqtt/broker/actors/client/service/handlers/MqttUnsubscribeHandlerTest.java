/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.actors.client.service.handlers;

import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttUnsubscribeMsg;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationPersistenceProcessor;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MqttUnsubscribeHandlerTest {

    MqttMessageGenerator mqttMessageGenerator;
    ClientSubscriptionService clientSubscriptionService;
    ApplicationPersistenceProcessor applicationPersistenceProcessor;
    MqttUnsubscribeHandler mqttUnsubscribeHandler;

    ClientSessionCtx ctx;

    @Before
    public void setUp() {
        mqttMessageGenerator = mock(MqttMessageGenerator.class);
        clientSubscriptionService = mock(ClientSubscriptionService.class);
        applicationPersistenceProcessor = mock(ApplicationPersistenceProcessor.class);
        mqttUnsubscribeHandler = spy(new MqttUnsubscribeHandler(mqttMessageGenerator, clientSubscriptionService, applicationPersistenceProcessor));

        ctx = mock(ClientSessionCtx.class);
    }

    @Test
    public void testProcess_MQTT5() {
        SessionInfo sessionInfo = mock(SessionInfo.class);
        when(ctx.getSessionInfo()).thenReturn(sessionInfo);
        ClientInfo clientInfo = mock(ClientInfo.class);
        when(sessionInfo.getClientInfo()).thenReturn(clientInfo);

        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);

        mqttUnsubscribeHandler.process(ctx, new MqttUnsubscribeMsg(UUID.randomUUID(), 1, List.of("topic")));

        verify(mqttMessageGenerator, times(1)).createUnSubAckMessage(eq(1), eq(List.of(MqttReasonCodes.UnsubAck.SUCCESS)));
        verify(clientSubscriptionService, times(1)).unsubscribeAndPersist(any(), any(), any());
    }

    @Test
    public void testProcess_MQTT3() {
        SessionInfo sessionInfo = mock(SessionInfo.class);
        when(ctx.getSessionInfo()).thenReturn(sessionInfo);
        ClientInfo clientInfo = mock(ClientInfo.class);
        when(sessionInfo.getClientInfo()).thenReturn(clientInfo);

        mqttUnsubscribeHandler.process(ctx, new MqttUnsubscribeMsg(UUID.randomUUID(), 1, List.of("topic")));

        verify(mqttMessageGenerator, times(1)).createUnSubAckMessage(eq(1), eq(getList()));
        verify(clientSubscriptionService, times(1)).unsubscribeAndPersist(any(), any(), any());
    }

    private List<MqttReasonCodes.UnsubAck> getList() {
        List<MqttReasonCodes.UnsubAck> value = new ArrayList<>();
        value.add(null);
        return value;
    }

    @Test
    public void testCollectUniqueSharedSubscriptions() {
        List<String> topics = List.of(
                "test/topic",
                "my/home/+/bedroom",
                "home/#",
                "$share/g1/test/my/#",
                "$share/g1/test/my/topic",
                "$share/g2/test/my/topic");

        Set<TopicSharedSubscription> sharedSubscriptions = mqttUnsubscribeHandler.collectUniqueSharedSubscriptions(topics);

        assertEquals(3, sharedSubscriptions.size());
        assertEquals(Set.of(
                new TopicSharedSubscription("test/my/#", "g1"),
                new TopicSharedSubscription("test/my/topic", "g1"),
                new TopicSharedSubscription("test/my/topic", "g2")
        ), sharedSubscriptions);
    }
}