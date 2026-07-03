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
package org.thingsboard.mqtt.broker.service.integration;

import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.integration.ClientLifecycleEventType;
import org.thingsboard.mqtt.broker.common.data.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.gen.queue.RetainHandling;
import org.thingsboard.mqtt.broker.gen.queue.TopicSubscriptionProto;
import org.thingsboard.mqtt.broker.gen.integration.ClientLifecycleEventMsgProto;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.integration.IntegrationEventMsgQueuePublisher;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;
import org.thingsboard.mqtt.broker.service.stats.DroppedLifecycleEventStats;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class IntegrationLifecycleEventPublisherImplTest {

    @Mock
    private IntegrationLifecycleEventTypeCache lifecycleEventTypeCache;
    @Mock
    private IntegrationEventMsgQueuePublisher integrationEventMsgQueuePublisher;
    @Mock
    private StatsManager statsManager;
    @Mock
    private DroppedLifecycleEventStats droppedLifecycleEventStats;
    @Mock
    private ServiceInfoProvider serviceInfoProvider;
    @Mock
    private SessionInfo sessionInfo;
    @Mock
    private ClientInfo clientInfo;
    @Mock
    private TopicSubscription topicSubscription;
    @Mock
    private ClientSessionCtx ctx;

    private IntegrationLifecycleEventPublisherImpl publisher;

    @Before
    public void setUp() {
        when(statsManager.getDroppedLifecycleEventStats()).thenReturn(droppedLifecycleEventStats);
        publisher = new IntegrationLifecycleEventPublisherImpl(lifecycleEventTypeCache, integrationEventMsgQueuePublisher, statsManager, serviceInfoProvider);
        publisher.init();
    }

    private void stubCtxSession() {
        when(ctx.getSessionInfo()).thenReturn(sessionInfo);
        when(ctx.getUsername()).thenReturn("demo");
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);
        when(sessionInfo.getClientInfo()).thenReturn(clientInfo);
        when(clientInfo.getClientId()).thenReturn("client-1");
        when(clientInfo.getClientIpAdr()).thenReturn(new byte[]{127, 0, 0, 1});
        when(sessionInfo.getSessionId()).thenReturn(UUID.randomUUID());
        when(sessionInfo.getServiceId()).thenReturn("tbmq-node-1");
        when(sessionInfo.isCleanStart()).thenReturn(true);
        when(sessionInfo.getKeepAlive()).thenReturn(60);
        when(sessionInfo.safeGetSessionExpiryInterval()).thenReturn(0);
    }

    @Test
    public void givenSubscriber_whenPublishConnected_thenSetsUsernameProtocolAndExpiry() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_CONNECTED)).thenReturn(Set.of("int-1"));
        stubCtxSession();
        when(ctx.getClientCertCn()).thenReturn("device-42");

        publisher.publishConnected(ctx);

        ArgumentCaptor<TbProtoQueueMsg<ClientLifecycleEventMsgProto>> captor = ArgumentCaptor.forClass(TbProtoQueueMsg.class);
        verify(integrationEventMsgQueuePublisher).sendEventMsg(eq("int-1"), captor.capture(), any());
        ClientLifecycleEventMsgProto proto = captor.getValue().getValue();
        org.junit.Assert.assertEquals("demo", proto.getUsername());
        org.junit.Assert.assertEquals("device-42", proto.getClientCertCn());
        org.junit.Assert.assertEquals(5, proto.getProtocolVersion());
        org.junit.Assert.assertEquals(0L, proto.getSessionExpiryInterval());
        org.junit.Assert.assertTrue(proto.getCleanStart());
    }

    @Test
    public void givenNoSubscribers_whenPublishConnected_thenEarlyReturnAndNeverPublishes() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_CONNECTED)).thenReturn(Set.of());

        publisher.publishConnected(ctx);

        verifyNoInteractions(integrationEventMsgQueuePublisher);
        verify(droppedLifecycleEventStats, never()).increment();
    }

    @Test
    public void givenSubscriber_whenPublishConnected_thenSendsEventMsgDirectly() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_CONNECTED)).thenReturn(Set.of("ie-1"));
        stubCtxSession();

        publisher.publishConnected(ctx);

        verify(integrationEventMsgQueuePublisher).sendEventMsg(eq("ie-1"), any(TbProtoQueueMsg.class), any(PublishMsgCallback.class));
    }

    @Test
    public void givenSendCallbackFails_whenPublishConnected_thenNoKeyAndIncrementsDroppedMetric() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_CONNECTED)).thenReturn(Set.of("ie-1"));
        stubCtxSession();

        publisher.publishConnected(ctx);

        ArgumentCaptor<TbProtoQueueMsg<ClientLifecycleEventMsgProto>> msgCaptor = ArgumentCaptor.forClass(TbProtoQueueMsg.class);
        ArgumentCaptor<PublishMsgCallback> cbCaptor = ArgumentCaptor.forClass(PublishMsgCallback.class);
        verify(integrationEventMsgQueuePublisher).sendEventMsg(eq("ie-1"), msgCaptor.capture(), cbCaptor.capture());

        // no record key: the event stream is single-partition with delete retention, so the key is unused
        org.junit.Assert.assertNull(msgCaptor.getValue().getKey());

        // an asynchronous Kafka send failure must be counted via the callback, not silently swallowed
        cbCaptor.getValue().onFailure(new RuntimeException("kafka send failed"));
        verify(droppedLifecycleEventStats).increment();
    }

    @Test
    public void givenPublisherThrows_whenPublishConnected_thenSwallowsAndIncrementsDroppedMetric() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_CONNECTED)).thenReturn(Set.of("ie-1"));
        stubCtxSession();
        doThrow(new RuntimeException("kafka down"))
                .when(integrationEventMsgQueuePublisher).sendEventMsg(anyString(), any(), any());

        // must not throw
        publisher.publishConnected(ctx);

        verify(droppedLifecycleEventStats).increment();
    }

    @Test
    public void givenMultipleIntegrations_whenEverySendThrows_thenCountsOneDropPerIntegration() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_CONNECTED))
                .thenReturn(Set.of("ie-1", "ie-2", "ie-3"));
        stubCtxSession();
        doThrow(new RuntimeException("kafka down"))
                .when(integrationEventMsgQueuePublisher).sendEventMsg(anyString(), any(), any());

        // must not throw
        publisher.publishConnected(ctx);

        // a synchronous send failure must be attributed per-integration (mirroring the async callback) and must
        // not abort the fan-out to the remaining integrations — not collapsed to a single drop for the whole batch
        verify(integrationEventMsgQueuePublisher, times(3)).sendEventMsg(anyString(), any(), any());
        verify(droppedLifecycleEventStats, times(3)).increment();
    }

    @Test
    public void givenCacheThrows_whenPublishDisconnected_thenSwallowsAndIncrementsDroppedMetric() {
        doThrow(new RuntimeException("cache boom"))
                .when(lifecycleEventTypeCache).getIntegrationIds(ClientLifecycleEventType.CLIENT_DISCONNECTED);

        publisher.publishDisconnected(ctx, DisconnectReasonType.ON_DISCONNECT_MSG);

        verify(droppedLifecycleEventStats).increment();
    }

    @Test
    public void givenSubscriber_whenPublishDisconnected_thenSetsMqttStandardReasonCode() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_DISCONNECTED)).thenReturn(Set.of("int-1"));
        stubCtxSession();

        publisher.publishDisconnected(ctx, DisconnectReasonType.ON_CLUSTER_CONFLICTING_SESSIONS);

        ArgumentCaptor<TbProtoQueueMsg<ClientLifecycleEventMsgProto>> captor = ArgumentCaptor.forClass(TbProtoQueueMsg.class);
        verify(integrationEventMsgQueuePublisher).sendEventMsg(eq("int-1"), captor.capture(), any());
        ClientLifecycleEventMsgProto p = captor.getValue().getValue();
        org.junit.Assert.assertEquals("CLIENT_DISCONNECTED", p.getEventType());
        // MQTT-standard reason-code name (Netty MqttReasonCodes.Disconnect), not the internal DisconnectReasonType name
        org.junit.Assert.assertEquals("SESSION_TAKEN_OVER", p.getDisconnectReason());
    }

    @Test
    public void givenSubscriber_whenPublishSubscribed_thenProtoCarriesFullSubscriptionDetails() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_SUBSCRIBED)).thenReturn(Set.of("int-1"));
        stubCtxSession();
        when(topicSubscription.getTopicFilter()).thenReturn("foo/bar");
        when(topicSubscription.getQos()).thenReturn(2);
        when(topicSubscription.getShareName()).thenReturn("g1");
        when(topicSubscription.getSubscriptionId()).thenReturn(7);
        when(topicSubscription.getOptions()).thenReturn(
                new SubscriptionOptions(true, true, SubscriptionOptions.RetainHandlingPolicy.DONT_SEND_AT_SUBSCRIBE));

        publisher.publishSubscribed(ctx, List.of(topicSubscription));

        ArgumentCaptor<TbProtoQueueMsg<ClientLifecycleEventMsgProto>> captor = ArgumentCaptor.forClass(TbProtoQueueMsg.class);
        verify(integrationEventMsgQueuePublisher).sendEventMsg(eq("int-1"), captor.capture(), any());
        TopicSubscriptionProto sub = captor.getValue().getValue().getSubscriptions(0);
        org.junit.Assert.assertEquals("foo/bar", sub.getTopic());
        org.junit.Assert.assertEquals(2, sub.getQos());
        org.junit.Assert.assertEquals("g1", sub.getShareName());
        org.junit.Assert.assertEquals(7, sub.getSubscriptionId());
        org.junit.Assert.assertTrue(sub.getOptions().getNoLocal());
        org.junit.Assert.assertTrue(sub.getOptions().getRetainAsPublish());
        org.junit.Assert.assertEquals(RetainHandling.DONT_SEND, sub.getOptions().getRetainHandling());
    }

    @Test
    public void givenSubscriber_whenPublishSubscribed_thenSendsEventMsgPerIntegration() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_SUBSCRIBED)).thenReturn(Set.of("ie-1"));
        stubCtxSession();
        when(topicSubscription.getTopicFilter()).thenReturn("test/topic");

        publisher.publishSubscribed(ctx, List.of(topicSubscription));

        verify(integrationEventMsgQueuePublisher).sendEventMsg(eq("ie-1"), any(TbProtoQueueMsg.class), any(PublishMsgCallback.class));
        verify(droppedLifecycleEventStats, never()).increment();
    }

    @Test
    public void givenPublisherThrows_whenPublishSubscribed_thenSwallowsAndIncrementsDroppedMetric() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_SUBSCRIBED)).thenReturn(Set.of("ie-1"));
        stubCtxSession();
        when(topicSubscription.getTopicFilter()).thenReturn("test/topic");
        doThrow(new RuntimeException("send failure"))
                .when(integrationEventMsgQueuePublisher).sendEventMsg(anyString(), any(), any());

        publisher.publishSubscribed(ctx, List.of(topicSubscription));

        verify(droppedLifecycleEventStats).increment();
    }

    @Test
    public void givenSubscriber_whenPublishUnsubscribed_thenSendsEventMsgPerIntegration() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_UNSUBSCRIBED)).thenReturn(Set.of("ie-1"));
        stubCtxSession();
        when(topicSubscription.getTopicFilter()).thenReturn("test/topic");

        publisher.publishUnsubscribed(ctx, List.of(topicSubscription));

        verify(integrationEventMsgQueuePublisher).sendEventMsg(eq("ie-1"), any(TbProtoQueueMsg.class), any(PublishMsgCallback.class));
        verify(droppedLifecycleEventStats, never()).increment();
    }

    @Test
    public void givenSharedSubscription_whenPublishUnsubscribed_thenProtoCarriesTopicFilterAndShareName() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_UNSUBSCRIBED)).thenReturn(Set.of("int-1"));
        stubCtxSession();
        when(topicSubscription.getTopicFilter()).thenReturn("foo/bar");
        when(topicSubscription.getShareName()).thenReturn("g1");

        publisher.publishUnsubscribed(ctx, List.of(topicSubscription));

        ArgumentCaptor<TbProtoQueueMsg<ClientLifecycleEventMsgProto>> captor = ArgumentCaptor.forClass(TbProtoQueueMsg.class);
        verify(integrationEventMsgQueuePublisher).sendEventMsg(eq("int-1"), captor.capture(), any());
        TopicSubscriptionProto sub = captor.getValue().getValue().getSubscriptions(0);
        org.junit.Assert.assertEquals("foo/bar", sub.getTopic());
        org.junit.Assert.assertTrue(sub.hasShareName());
        org.junit.Assert.assertEquals("g1", sub.getShareName());
        // an UNSUBSCRIBE carries only identity — no qos/options/subscriptionId on the wire
        org.junit.Assert.assertFalse(sub.hasSubscriptionId());
    }

    @Test
    public void givenPublisherThrows_whenPublishUnsubscribed_thenSwallowsAndIncrementsDroppedMetric() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_UNSUBSCRIBED)).thenReturn(Set.of("ie-1"));
        stubCtxSession();
        when(topicSubscription.getTopicFilter()).thenReturn("test/topic");
        doThrow(new RuntimeException("send failure"))
                .when(integrationEventMsgQueuePublisher).sendEventMsg(anyString(), any(), any());

        publisher.publishUnsubscribed(ctx, List.of(topicSubscription));

        verify(droppedLifecycleEventStats).increment();
    }

    @Test
    public void givenSubscriber_whenPublishAuthorizationDenied_thenBuildsDenyEvent() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_AUTHORIZATION_FAILED)).thenReturn(Set.of("int-1"));
        stubCtxSession();

        publisher.publishAuthorizationDenied(ctx, AuthorizationAction.PUBLISH, "zxc/demo/topic");

        ArgumentCaptor<TbProtoQueueMsg<ClientLifecycleEventMsgProto>> captor = ArgumentCaptor.forClass(TbProtoQueueMsg.class);
        verify(integrationEventMsgQueuePublisher).sendEventMsg(eq("int-1"), captor.capture(), any());
        ClientLifecycleEventMsgProto p = captor.getValue().getValue();
        org.junit.Assert.assertEquals("CLIENT_AUTHORIZATION_FAILED", p.getEventType());
        org.junit.Assert.assertEquals("publish", p.getAction());
        org.junit.Assert.assertEquals("zxc/demo/topic", p.getTopic());
        org.junit.Assert.assertEquals("demo", p.getUsername());
    }

    @Test
    public void givenSubscriber_whenPublishAuthenticatedFailure_thenBuildsFailureEvent() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_AUTHENTICATION_FAILED)).thenReturn(Set.of("int-1"));
        when(serviceInfoProvider.getServiceId()).thenReturn("tbmq-node-1");
        when(ctx.getSessionId()).thenReturn(UUID.randomUUID());
        when(ctx.getAddressBytes()).thenReturn(new byte[]{127, 0, 0, 1});
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);
        when(ctx.getUsername()).thenReturn("demo");
        publisher.publishAuthenticationFailed(ctx, "client-1", "Invalid credentials");

        ArgumentCaptor<TbProtoQueueMsg<ClientLifecycleEventMsgProto>> captor = ArgumentCaptor.forClass(TbProtoQueueMsg.class);
        verify(integrationEventMsgQueuePublisher).sendEventMsg(eq("int-1"), captor.capture(), any());
        ClientLifecycleEventMsgProto p = captor.getValue().getValue();
        org.junit.Assert.assertEquals("CLIENT_AUTHENTICATION_FAILED", p.getEventType());
        org.junit.Assert.assertEquals("client-1", p.getClientId());
        org.junit.Assert.assertEquals("demo", p.getUsername());
        org.junit.Assert.assertEquals("Invalid credentials", p.getReason());
    }

    @Test
    public void givenSubscriber_whenPublishConnectionFailed_thenBuildsFailureEvent() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_CONNECTION_FAILED)).thenReturn(Set.of("int-1"));
        stubCtxSession();

        publisher.publishConnectionFailed(ctx, sessionInfo, "QUOTA_EXCEEDED");

        ArgumentCaptor<TbProtoQueueMsg<ClientLifecycleEventMsgProto>> captor = ArgumentCaptor.forClass(TbProtoQueueMsg.class);
        verify(integrationEventMsgQueuePublisher).sendEventMsg(eq("int-1"), captor.capture(), any());
        ClientLifecycleEventMsgProto p = captor.getValue().getValue();
        org.junit.Assert.assertEquals("CLIENT_CONNECTION_FAILED", p.getEventType());
        org.junit.Assert.assertEquals("QUOTA_EXCEEDED", p.getReason());
        org.junit.Assert.assertEquals("client-1", p.getClientId());
        org.junit.Assert.assertEquals("demo", p.getUsername());
    }

    @Test
    public void givenPreConnectionValidationFailure_whenPublishConnectionFailed_thenBuildsFromPassedSessionInfo() {
        // Pre-connection refusals happen before ctx.setSessionInfo, so the session is passed explicitly.
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_CONNECTION_FAILED)).thenReturn(Set.of("int-1"));
        when(ctx.getUsername()).thenReturn("demo");
        when(sessionInfo.getClientInfo()).thenReturn(clientInfo);
        when(clientInfo.getClientId()).thenReturn("client-1");
        when(clientInfo.getClientIpAdr()).thenReturn(new byte[]{127, 0, 0, 1});
        when(sessionInfo.getSessionId()).thenReturn(UUID.randomUUID());
        when(sessionInfo.getServiceId()).thenReturn("tbmq-node-1");

        publisher.publishConnectionFailed(ctx, sessionInfo, "CONNECTION_REFUSED_CLIENT_IDENTIFIER_NOT_VALID");

        ArgumentCaptor<TbProtoQueueMsg<ClientLifecycleEventMsgProto>> captor = ArgumentCaptor.forClass(TbProtoQueueMsg.class);
        verify(integrationEventMsgQueuePublisher).sendEventMsg(eq("int-1"), captor.capture(), any());
        ClientLifecycleEventMsgProto p = captor.getValue().getValue();
        org.junit.Assert.assertEquals("CLIENT_CONNECTION_FAILED", p.getEventType());
        org.junit.Assert.assertEquals("CONNECTION_REFUSED_CLIENT_IDENTIFIER_NOT_VALID", p.getReason());
        org.junit.Assert.assertEquals("client-1", p.getClientId());
        org.junit.Assert.assertEquals("demo", p.getUsername());
    }
}
