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
package org.thingsboard.mqtt.broker.service.notification;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;
import org.thingsboard.mqtt.broker.common.data.integration.ClientLifecycleEventType;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionStatsCleanupProto;
import org.thingsboard.mqtt.broker.gen.queue.IntegrationLifecycleConfigProto;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthProviderProto;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthSettingsProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.InternodeNotificationsQueueFactory;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRoutingService;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttAuthProviderNotificationManager;
import org.thingsboard.mqtt.broker.service.integration.IntegrationLifecycleEventTypeCache;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionStatsCleanupProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InternodeNotificationsServiceImplTest {

    @Mock
    private InternodeNotificationsQueueFactory queueFactory;

    @Mock
    private ServiceInfoProvider serviceInfoProvider;

    @Mock
    private InternodeNotificationsHelper helper;

    @Mock
    private MqttAuthProviderNotificationManager mqttClientAuthProviderManager;

    @Mock
    private ClientSessionStatsCleanupProcessor clientSessionStatsCleanupProcessor;

    @Mock
    private AuthorizationRoutingService authorizationRoutingService;

    @Mock
    private IntegrationLifecycleEventTypeCache integrationLifecycleEventTypeCache;

    @Mock
    private TbQueueProducer<TbProtoQueueMsg<InternodeNotificationProto>> producer;

    private InternodeNotificationsServiceImpl service;

    @Before
    public void setUp() {
        when(serviceInfoProvider.getServiceId()).thenReturn("nodeA");
        when(queueFactory.createProducer("nodeA")).thenReturn(producer);

        service = new InternodeNotificationsServiceImpl(
                queueFactory,
                serviceInfoProvider,
                helper,
                mqttClientAuthProviderManager,
                clientSessionStatsCleanupProcessor,
                authorizationRoutingService,
                integrationLifecycleEventTypeCache
        );
        service.init();
    }

    @Test
    public void testBroadcast_ToAnotherNodes() {
        InternodeNotificationProto proto = InternodeNotificationProto.getDefaultInstance();

        when(helper.getServiceIds()).thenReturn(List.of("nodeA", "nodeB", "nodeC"));
        when(helper.getServiceTopic("nodeB")).thenReturn("topicB");
        when(helper.getServiceTopic("nodeC")).thenReturn("topicC");

        service.broadcast(proto);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<TbProtoQueueMsg<InternodeNotificationProto>> msgCaptor = ArgumentCaptor.forClass(TbProtoQueueMsg.class);

        // Capture interactions twice: once for nodeB, once for nodeC
        verify(producer, times(2)).send(
                topicCaptor.capture(),
                isNull(),
                msgCaptor.capture(),
                any(TbQueueCallback.class)
        );

        List<String> topics = topicCaptor.getAllValues();
        List<TbProtoQueueMsg<InternodeNotificationProto>> messages = msgCaptor.getAllValues();

        assertThat(topics).containsExactlyInAnyOrder("topicB", "topicC");

        assertThat(messages)
                .hasSize(2)
                .allSatisfy(msg -> {
                    assertThat(msg.getValue()).isEqualTo(proto);
                    assertThat(List.of("nodeB", "nodeC")).contains(msg.getKey());
                });
    }

    @Test
    public void testBroadcast_ToSelfAndOthers_WithAuthSettings() {
        InternodeNotificationProto proto = InternodeNotificationProto.newBuilder()
                .setMqttAuthSettingsProto(MqttAuthSettingsProto.getDefaultInstance())
                .build();

        when(helper.getServiceIds()).thenReturn(List.of("nodeA", "nodeB", "nodeC"));
        when(helper.getServiceTopic("nodeB")).thenReturn("topicB");
        when(helper.getServiceTopic("nodeC")).thenReturn("topicC");

        service.broadcast(proto);

        // Verify that local handler is invoked
        verify(authorizationRoutingService).onMqttAuthSettingsUpdate(proto.getMqttAuthSettingsProto());

        // Verify that messages are sent to other nodes
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<TbProtoQueueMsg<InternodeNotificationProto>> msgCaptor = ArgumentCaptor.forClass(TbProtoQueueMsg.class);

        verify(producer, times(2)).send(
                topicCaptor.capture(),
                isNull(),
                msgCaptor.capture(),
                any(TbQueueCallback.class)
        );

        List<String> topics = topicCaptor.getAllValues();
        List<TbProtoQueueMsg<InternodeNotificationProto>> messages = msgCaptor.getAllValues();

        assertThat(topics).containsExactlyInAnyOrder("topicB", "topicC");

        assertThat(messages)
                .hasSize(2)
                .allSatisfy(msg -> {
                    assertThat(msg.getValue()).isEqualTo(proto);
                    assertThat(List.of("nodeB", "nodeC")).contains(msg.getKey());
                });

        verifyNoInteractions(mqttClientAuthProviderManager, clientSessionStatsCleanupProcessor);
    }

    @Test
    public void testBroadcast_ToSelf_WithAuthSettings() {
        InternodeNotificationProto proto = InternodeNotificationProto.newBuilder()
                .setMqttAuthSettingsProto(MqttAuthSettingsProto.getDefaultInstance())
                .build();

        when(helper.getServiceIds()).thenReturn(List.of("nodeA"));

        service.broadcast(proto);

        verify(authorizationRoutingService).onMqttAuthSettingsUpdate(proto.getMqttAuthSettingsProto());
        verifyNoInteractions(mqttClientAuthProviderManager, clientSessionStatsCleanupProcessor, producer);
    }

    @Test
    public void testBroadcast_ToSelf_WithAuthProvider() {
        InternodeNotificationProto proto = InternodeNotificationProto.newBuilder()
                .setMqttAuthProviderProto(MqttAuthProviderProto.getDefaultInstance())
                .build();

        when(helper.getServiceIds()).thenReturn(List.of("nodeA"));

        service.broadcast(proto);

        verify(mqttClientAuthProviderManager).handleProviderNotification(proto.getMqttAuthProviderProto());
        verifyNoInteractions(authorizationRoutingService, clientSessionStatsCleanupProcessor, producer);
    }

    @Test
    public void testBroadcast_ToSelf_WithClientSessionStartCleanupRequest() {
        InternodeNotificationProto proto = InternodeNotificationProto.newBuilder()
                .setClientSessionStatsCleanupProto(ClientSessionStatsCleanupProto.getDefaultInstance())
                .build();

        when(helper.getServiceIds()).thenReturn(List.of("nodeA"));

        service.broadcast(proto);

        verify(clientSessionStatsCleanupProcessor).processClientSessionStatsCleanup(proto.getClientSessionStatsCleanupProto());
        verifyNoInteractions(authorizationRoutingService, mqttClientAuthProviderManager, producer);
    }

    @Test
    public void testBroadcast_ToSelf_WithIntegrationLifecycleConfig() {
        IntegrationLifecycleConfigProto configProto = IntegrationLifecycleConfigProto.newBuilder()
                .setIntegrationId("integration-1")
                .addLifecycleEventTypes(ClientLifecycleEventType.CLIENT_CONNECTED.name())
                .build();
        InternodeNotificationProto proto = InternodeNotificationProto.newBuilder()
                .setIntegrationLifecycleConfigProto(configProto)
                .build();

        when(helper.getServiceIds()).thenReturn(List.of("nodeA"));

        service.broadcast(proto);

        verify(integrationLifecycleEventTypeCache).processIntegrationLifecycleConfig(configProto);
        verifyNoInteractions(authorizationRoutingService, mqttClientAuthProviderManager, clientSessionStatsCleanupProcessor, producer);
    }

    @Test
    public void testBroadcast_ToSelf_WithIntegrationLifecycleConfigDeleted() {
        IntegrationLifecycleConfigProto configProto = IntegrationLifecycleConfigProto.newBuilder()
                .setIntegrationId("integration-1")
                .setDeleted(true)
                .build();
        InternodeNotificationProto proto = InternodeNotificationProto.newBuilder()
                .setIntegrationLifecycleConfigProto(configProto)
                .build();

        when(helper.getServiceIds()).thenReturn(List.of("nodeA"));

        service.broadcast(proto);

        verify(integrationLifecycleEventTypeCache).processIntegrationLifecycleConfig(configProto);
        verifyNoInteractions(authorizationRoutingService, mqttClientAuthProviderManager, clientSessionStatsCleanupProcessor, producer);
    }

    /**
     * The send can throw - createTopicIfNotExists runs first and TbKafkaAdmin.createTopic rethrows - so without
     * isolation one unreachable node silently drops the notification for every node after it in the list.
     */
    @Test
    public void testBroadcast_WhenSendFailsForOneNode_ThenStillSendsToTheRest() {
        InternodeNotificationProto proto = InternodeNotificationProto.getDefaultInstance();

        when(helper.getServiceIds()).thenReturn(List.of("nodeA", "nodeB", "nodeC"));
        when(helper.getServiceTopic("nodeB")).thenReturn("topicB");
        when(helper.getServiceTopic("nodeC")).thenReturn("topicC");
        doThrow(new RuntimeException("kafka admin timeout"))
                .when(producer).send(eq("topicB"), isNull(), any(), any(TbQueueCallback.class));

        service.broadcast(proto);

        verify(producer).send(eq("topicC"), isNull(), any(), any(TbQueueCallback.class));
    }

    /**
     * getTbmqServiceIds maps an unordered Redis hash, so this node's own id can come last - which used to make the
     * in-process update hostage to every remote send ahead of it. DefaultTbIntegrationService.delete relies on it.
     */
    @Test
    public void testBroadcast_WhenSendFailsForANodeListedFirst_ThenStillAppliesLocally() {
        IntegrationLifecycleConfigProto configProto = IntegrationLifecycleConfigProto.newBuilder()
                .setIntegrationId("integration-1")
                .setDeleted(true)
                .build();
        InternodeNotificationProto proto = InternodeNotificationProto.newBuilder()
                .setIntegrationLifecycleConfigProto(configProto)
                .build();

        when(helper.getServiceIds()).thenReturn(List.of("nodeB", "nodeA"));
        when(helper.getServiceTopic("nodeB")).thenReturn("topicB");
        doThrow(new RuntimeException("kafka admin timeout"))
                .when(producer).send(eq("topicB"), isNull(), any(), any(TbQueueCallback.class));

        service.broadcast(proto);

        verify(integrationLifecycleEventTypeCache).processIntegrationLifecycleConfig(configProto);
    }

    /**
     * getServiceIds is a live Redis read, so the local update must not depend on it either. It still propagates -
     * notifying nobody is worth telling the caller about.
     */
    @Test
    public void testBroadcast_WhenTheServiceRegistryReadFails_ThenStillAppliesLocally() {
        IntegrationLifecycleConfigProto configProto = IntegrationLifecycleConfigProto.newBuilder()
                .setIntegrationId("integration-1")
                .setDeleted(true)
                .build();
        InternodeNotificationProto proto = InternodeNotificationProto.newBuilder()
                .setIntegrationLifecycleConfigProto(configProto)
                .build();

        when(helper.getServiceIds()).thenThrow(new RuntimeException("redis is unavailable"));

        assertThatThrownBy(() -> service.broadcast(proto))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("redis is unavailable");

        verify(integrationLifecycleEventTypeCache).processIntegrationLifecycleConfig(configProto);
    }

    /**
     * A stale registry that has lost this node is no reason to skip an in-process update that originated here.
     */
    @Test
    public void testBroadcast_WhenThisNodeIsMissingFromTheRegistry_ThenStillAppliesLocally() {
        IntegrationLifecycleConfigProto configProto = IntegrationLifecycleConfigProto.newBuilder()
                .setIntegrationId("integration-1")
                .setDeleted(true)
                .build();
        InternodeNotificationProto proto = InternodeNotificationProto.newBuilder()
                .setIntegrationLifecycleConfigProto(configProto)
                .build();

        when(helper.getServiceIds()).thenReturn(List.of("nodeB"));
        when(helper.getServiceTopic("nodeB")).thenReturn("topicB");

        service.broadcast(proto);

        verify(integrationLifecycleEventTypeCache).processIntegrationLifecycleConfig(configProto);
        verify(producer).send(eq("topicB"), isNull(), any(), any(TbQueueCallback.class));
    }

    /**
     * Isolating the sends must not make them silent, whichever way the failure arrives.
     */
    @Test
    public void testBroadcast_WhenSendFailsSynchronously_ThenLogsItLikeAnAsynchronousFailure() {
        InternodeNotificationProto proto = InternodeNotificationProto.getDefaultInstance();

        when(helper.getServiceIds()).thenReturn(List.of("nodeB"));
        when(helper.getServiceTopic("nodeB")).thenReturn("topicB");
        doThrow(new RuntimeException("kafka admin timeout"))
                .when(producer).send(eq("topicB"), isNull(), any(), any(TbQueueCallback.class));

        Logger logger = (Logger) LoggerFactory.getLogger(InternodeNotificationsServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.broadcast(proto);

            assertThat(appender.list)
                    .anyMatch(event -> event.getLevel() == Level.WARN
                            && event.getFormattedMessage().contains("Failed to send notification"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    public void testDestroy_ShouldStopProducer() {
        service.destroy();
        verify(producer).stop();
    }
}

