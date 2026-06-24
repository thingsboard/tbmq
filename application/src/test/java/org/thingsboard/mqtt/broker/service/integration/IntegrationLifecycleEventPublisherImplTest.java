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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.integration.ClientLifecycleEventType;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.integration.IntegrationEventMsgQueuePublisher;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;
import org.thingsboard.mqtt.broker.service.stats.DroppedLifecycleEventStats;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
    private SessionInfo sessionInfo;
    @Mock
    private ClientInfo clientInfo;
    @Mock
    private TopicSubscription topicSubscription;

    private IntegrationLifecycleEventPublisherImpl publisher;

    @Before
    public void setUp() {
        when(statsManager.getDroppedLifecycleEventStats()).thenReturn(droppedLifecycleEventStats);
        publisher = new IntegrationLifecycleEventPublisherImpl(lifecycleEventTypeCache, integrationEventMsgQueuePublisher, statsManager);
        publisher.init();
    }

    private void stubSession() {
        when(sessionInfo.getClientInfo()).thenReturn(clientInfo);
        when(clientInfo.getClientId()).thenReturn("client-1");
        when(clientInfo.getClientIpAdr()).thenReturn(new byte[]{127, 0, 0, 1});
        when(sessionInfo.getSessionId()).thenReturn(UUID.randomUUID());
        when(sessionInfo.getServiceId()).thenReturn("tbmq-node-1");
    }

    @Test
    public void givenNoSubscribers_whenPublishConnected_thenEarlyReturnAndNeverPublishes() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_CONNECTED)).thenReturn(Set.of());

        publisher.publishConnected(sessionInfo);

        verifyNoInteractions(integrationEventMsgQueuePublisher);
        verify(droppedLifecycleEventStats, never()).increment();
    }

    @Test
    public void givenSubscriber_whenPublishConnected_thenSendsEventMsgDirectly() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_CONNECTED)).thenReturn(Set.of("ie-1"));
        stubSession();

        publisher.publishConnected(sessionInfo);

        verify(integrationEventMsgQueuePublisher).sendEventMsg(eq("ie-1"), any(TbProtoQueueMsg.class), eq(PublishMsgCallback.EMPTY));
    }

    @Test
    public void givenPublisherThrows_whenPublishConnected_thenSwallowsAndIncrementsDroppedMetric() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_CONNECTED)).thenReturn(Set.of("ie-1"));
        stubSession();
        doThrow(new RuntimeException("kafka down"))
                .when(integrationEventMsgQueuePublisher).sendEventMsg(anyString(), any(), any());

        // must not throw
        publisher.publishConnected(sessionInfo);

        verify(droppedLifecycleEventStats).increment();
    }

    @Test
    public void givenCacheThrows_whenPublishDisconnected_thenSwallowsAndIncrementsDroppedMetric() {
        doThrow(new RuntimeException("cache boom"))
                .when(lifecycleEventTypeCache).getIntegrationIds(ClientLifecycleEventType.CLIENT_DISCONNECTED);

        publisher.publishDisconnected(sessionInfo, DisconnectReasonType.ON_DISCONNECT_MSG);

        verify(droppedLifecycleEventStats).increment();
    }

    @Test
    public void givenSubscriber_whenPublishSubscribed_thenSendsEventMsgPerIntegration() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_SUBSCRIBED)).thenReturn(Set.of("ie-1"));
        stubSession();
        when(topicSubscription.getTopicFilter()).thenReturn("test/topic");

        publisher.publishSubscribed(sessionInfo, List.of(topicSubscription));

        verify(integrationEventMsgQueuePublisher).sendEventMsg(eq("ie-1"), any(TbProtoQueueMsg.class), eq(PublishMsgCallback.EMPTY));
        verify(droppedLifecycleEventStats, never()).increment();
    }

    @Test
    public void givenPublisherThrows_whenPublishSubscribed_thenSwallowsAndIncrementsDroppedMetric() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_SUBSCRIBED)).thenReturn(Set.of("ie-1"));
        stubSession();
        when(topicSubscription.getTopicFilter()).thenReturn("test/topic");
        doThrow(new RuntimeException("send failure"))
                .when(integrationEventMsgQueuePublisher).sendEventMsg(anyString(), any(), any());

        publisher.publishSubscribed(sessionInfo, List.of(topicSubscription));

        verify(droppedLifecycleEventStats).increment();
    }

    @Test
    public void givenSubscriber_whenPublishUnsubscribed_thenSendsEventMsgPerIntegration() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_UNSUBSCRIBED)).thenReturn(Set.of("ie-1"));
        stubSession();

        publisher.publishUnsubscribed(sessionInfo, List.of("test/topic"));

        verify(integrationEventMsgQueuePublisher).sendEventMsg(eq("ie-1"), any(TbProtoQueueMsg.class), eq(PublishMsgCallback.EMPTY));
        verify(droppedLifecycleEventStats, never()).increment();
    }

    @Test
    public void givenPublisherThrows_whenPublishUnsubscribed_thenSwallowsAndIncrementsDroppedMetric() {
        when(lifecycleEventTypeCache.getIntegrationIds(ClientLifecycleEventType.CLIENT_UNSUBSCRIBED)).thenReturn(Set.of("ie-1"));
        stubSession();
        doThrow(new RuntimeException("send failure"))
                .when(integrationEventMsgQueuePublisher).sendEventMsg(anyString(), any(), any());

        publisher.publishUnsubscribed(sessionInfo, List.of("test/topic"));

        verify(droppedLifecycleEventStats).increment();
    }
}
