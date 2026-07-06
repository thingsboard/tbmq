/**
 * Copyright © 2016-2026 The Thingsboard Authors
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

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttUnsubscribeMsg;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.UnsubscribeCallback;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.subscription.ClientTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.service.integration.IntegrationLifecycleEventPublisher;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationPersistenceProcessor;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MqttUnsubscribeHandlerTest {

    MqttMessageGenerator mqttMessageGenerator;
    ClientSubscriptionService clientSubscriptionService;
    ApplicationPersistenceProcessor applicationPersistenceProcessor;
    IntegrationLifecycleEventPublisher integrationLifecycleEventPublisher;
    MqttUnsubscribeHandler mqttUnsubscribeHandler;

    ClientSessionCtx ctx;
    SessionInfo sessionInfo;

    @Before
    public void setUp() {
        mqttMessageGenerator = mock(MqttMessageGenerator.class);
        clientSubscriptionService = mock(ClientSubscriptionService.class);
        applicationPersistenceProcessor = mock(ApplicationPersistenceProcessor.class);
        integrationLifecycleEventPublisher = mock(IntegrationLifecycleEventPublisher.class);
        mqttUnsubscribeHandler = spy(new MqttUnsubscribeHandler(mqttMessageGenerator, clientSubscriptionService, applicationPersistenceProcessor, integrationLifecycleEventPublisher));

        ctx = mock(ClientSessionCtx.class);
        sessionInfo = mock(SessionInfo.class);
        when(ctx.getClientId()).thenReturn("client-1");
        when(ctx.getChannel()).thenReturn(mock(ChannelHandlerContext.class));
        when(ctx.getSessionInfo()).thenReturn(sessionInfo);
        when(sessionInfo.isPersistentAppClient()).thenReturn(false);
    }

    private UnsubscribeCallback process(MqttUnsubscribeMsg msg) {
        mqttUnsubscribeHandler.process(ctx, msg);
        ArgumentCaptor<UnsubscribeCallback> callbackCaptor = ArgumentCaptor.forClass(UnsubscribeCallback.class);
        verify(clientSubscriptionService).unsubscribeAndPersistReportingRemoved(eq("client-1"), eq(msg.getTopics()), callbackCaptor.capture());
        return callbackCaptor.getValue();
    }

    @Test
    public void givenMqtt5AndRemovedFilter_whenProcess_thenSuccessCodeAndEvent() {
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);

        UnsubscribeCallback callback = process(new MqttUnsubscribeMsg(UUID.randomUUID(), 1, List.of("topic")));
        TopicSubscription removed = new ClientTopicSubscription("topic", 1);
        callback.onSuccess(List.of(removed));

        verify(mqttMessageGenerator).createUnSubAckMessage(eq(1), eq(List.of(MqttReasonCodes.UnsubAck.SUCCESS)));
        verify(integrationLifecycleEventPublisher).publishUnsubscribed(ctx, List.of(removed));
    }

    @Test
    public void givenMqtt5AndNeverSubscribedFilter_whenProcess_thenNoSubscriptionExistedCodeAndNoEvent() {
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);

        UnsubscribeCallback callback = process(new MqttUnsubscribeMsg(UUID.randomUUID(), 1, List.of("never/subscribed")));
        callback.onSuccess(List.of());

        verify(mqttMessageGenerator).createUnSubAckMessage(eq(1), eq(List.of(MqttReasonCodes.UnsubAck.NO_SUBSCRIPTION_EXISTED)));
        verify(integrationLifecycleEventPublisher, never()).publishUnsubscribed(any(), any());
    }

    @Test
    public void givenMqtt5AndMixedFilters_whenProcess_thenPerFilterCodesInRequestOrder() {
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);

        UnsubscribeCallback callback = process(new MqttUnsubscribeMsg(UUID.randomUUID(), 1, List.of("a/b", "never/subscribed")));
        callback.onSuccess(List.of(new ClientTopicSubscription("a/b", 1)));

        // Codes must line up positionally with the requested topics: removed -> SUCCESS, never subscribed -> NO_SUBSCRIPTION_EXISTED
        verify(mqttMessageGenerator).createUnSubAckMessage(eq(1),
                eq(List.of(MqttReasonCodes.UnsubAck.SUCCESS, MqttReasonCodes.UnsubAck.NO_SUBSCRIPTION_EXISTED)));
        verify(integrationLifecycleEventPublisher).publishUnsubscribed(ctx, List.of(new ClientTopicSubscription("a/b", 1)));
    }

    @Test
    public void givenMqtt3_whenProcess_thenSingleNullCodeRegardlessOfRemoval() {
        // MQTT 3.1.1 (version not stubbed) -> no reason codes on the wire
        UnsubscribeCallback callback = process(new MqttUnsubscribeMsg(UUID.randomUUID(), 1, List.of("topic")));
        callback.onSuccess(List.of());

        verify(mqttMessageGenerator).createUnSubAckMessage(eq(1), eq(Collections.singletonList(null)));
    }

    @Test
    public void givenMqtt3AppClient_whenProcessSharedUnsubscribe_thenStopsSharedProcessingAndAcks() {
        when(sessionInfo.isPersistentAppClient()).thenReturn(true);

        UnsubscribeCallback callback = process(new MqttUnsubscribeMsg(UUID.randomUUID(), 1, List.of("$share/group/topic")));

        // Application shared-subscription processing is stopped synchronously, independent of the persist callback
        verify(applicationPersistenceProcessor).stopProcessingSharedSubscriptions(any(), eq(Set.of(new TopicSharedSubscription("topic", "group"))));

        callback.onSuccess(List.of());
        verify(mqttMessageGenerator).createUnSubAckMessage(eq(1), eq(Collections.singletonList(null)));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void givenSharedSubscriptionRemoved_whenProcess_thenSuccessCodeAndEmitsSubscriptionWithShareName() {
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);

        UnsubscribeCallback callback = process(new MqttUnsubscribeMsg(UUID.randomUUID(), 1, List.of("$share/group/topic")));
        callback.onSuccess(List.of(new ClientTopicSubscription("topic", 1, "group")));

        // The shared request resolves to its bare filter for the code: removed -> SUCCESS
        verify(mqttMessageGenerator).createUnSubAckMessage(eq(1), eq(List.of(MqttReasonCodes.UnsubAck.SUCCESS)));

        // The removed shared subscription must carry its shareName so $share/group/topic can be reconstructed downstream
        ArgumentCaptor<List<TopicSubscription>> captor = ArgumentCaptor.forClass(List.class);
        verify(integrationLifecycleEventPublisher).publishUnsubscribed(eq(ctx), captor.capture());
        List<TopicSubscription> removed = captor.getValue();
        assertEquals(1, removed.size());
        assertEquals("topic", removed.get(0).getTopicFilter());
        assertEquals("group", removed.get(0).getShareName());
    }

    @Test
    public void givenNothingRemoved_whenProcess_thenNoEventPublished() {
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);

        UnsubscribeCallback callback = process(new MqttUnsubscribeMsg(UUID.randomUUID(), 1, List.of("a/b")));
        callback.onSuccess(List.of());

        verify(integrationLifecycleEventPublisher, never()).publishUnsubscribed(any(), any());
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
