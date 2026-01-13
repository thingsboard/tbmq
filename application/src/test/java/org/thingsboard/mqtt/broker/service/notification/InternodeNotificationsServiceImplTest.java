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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionStatsCleanupProto;
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
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionStatsCleanupProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
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
                authorizationRoutingService
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
    public void testDestroy_ShouldStopProducer() {
        service.destroy();
        verify(producer).stop();
    }
}

