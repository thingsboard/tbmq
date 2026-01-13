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


import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionStatsCleanupProto;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthProviderProto;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthSettingsProto;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.InternodeNotificationsQueueFactory;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRoutingService;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttAuthProviderNotificationManager;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionStatsCleanupProcessor;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InternodeNotificationsConsumerImplTest {

    @Mock
    private InternodeNotificationsQueueFactory queueFactory;

    @Mock
    private InternodeNotificationsHelperImpl helper;

    @Mock
    private ServiceInfoProvider serviceInfoProvider;

    @Mock
    private MqttAuthProviderNotificationManager mqttClientAuthProviderManager;

    @Mock
    private ClientSessionStatsCleanupProcessor clientSessionStatsCleanupProcessor;

    @Mock
    private AuthorizationRoutingService authorizationRoutingService;

    @Mock
    private TbQueueConsumer<TbProtoQueueMsg<InternodeNotificationProto>> consumer;

    private InternodeNotificationsConsumerImpl notificationsConsumer;

    @Before
    public void setUp() {
        notificationsConsumer = new InternodeNotificationsConsumerImpl(
                queueFactory,
                helper,
                serviceInfoProvider,
                mqttClientAuthProviderManager,
                clientSessionStatsCleanupProcessor,
                authorizationRoutingService);

        ReflectionTestUtils.setField(notificationsConsumer, "pollDuration", 1L);

        when(serviceInfoProvider.getServiceId()).thenReturn("nodeA");
        when(helper.getServiceTopic("nodeA")).thenReturn("topicA");
        when(queueFactory.createConsumer("topicA", "nodeA")).thenReturn(consumer);
    }

    @Test
    public void testStartConsuming_AndProcessesMessageWithAuthSettings() {
        InternodeNotificationProto proto = InternodeNotificationProto.newBuilder()
                .setMqttAuthSettingsProto(MqttAuthSettingsProto.getDefaultInstance())
                .build();

        TbProtoQueueMsg<InternodeNotificationProto> msg = new TbProtoQueueMsg<>("nodeA", proto);

        when(consumer.poll(anyLong()))
                .thenReturn(List.of(msg))
                .thenReturn(List.of()); // to avoid continuous processing

        notificationsConsumer.startConsuming();

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                verify(authorizationRoutingService).onMqttAuthSettingsUpdate(proto.getMqttAuthSettingsProto()));

        verifyNoInteractions(mqttClientAuthProviderManager, clientSessionStatsCleanupProcessor);

        notificationsConsumer.destroy();
        verify(consumer).unsubscribeAndClose();
    }

    @Test
    public void testStartConsuming_AndProcessesMessageWithAuthProvider() {
        InternodeNotificationProto proto = InternodeNotificationProto.newBuilder()
                .setMqttAuthProviderProto(MqttAuthProviderProto.getDefaultInstance())
                .build();

        TbProtoQueueMsg<InternodeNotificationProto> msg = new TbProtoQueueMsg<>("nodeA", proto);

        when(consumer.poll(anyLong()))
                .thenReturn(List.of(msg))
                .thenReturn(List.of()); // Stop further processing

        notificationsConsumer.startConsuming();

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                verify(mqttClientAuthProviderManager).handleProviderNotification(proto.getMqttAuthProviderProto()));

        verifyNoInteractions(authorizationRoutingService, clientSessionStatsCleanupProcessor);

        notificationsConsumer.destroy();
        verify(consumer).unsubscribeAndClose();
    }

    @Test
    public void testStartConsuming_AndProcessesMessageWithClientSessionStatsCleanupRequest() {

        InternodeNotificationProto proto = InternodeNotificationProto.newBuilder()
                .setClientSessionStatsCleanupProto(ClientSessionStatsCleanupProto.getDefaultInstance())
                .build();

        TbProtoQueueMsg<InternodeNotificationProto> msg = new TbProtoQueueMsg<>("nodeA", proto);

        when(consumer.poll(anyLong()))
                .thenReturn(List.of(msg))
                .thenReturn(List.of()); // to avoid continuous processing

        notificationsConsumer.startConsuming();

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                verify(clientSessionStatsCleanupProcessor).processClientSessionStatsCleanup(proto.getClientSessionStatsCleanupProto()));

        verifyNoInteractions(authorizationRoutingService, mqttClientAuthProviderManager);

        notificationsConsumer.destroy();
        verify(consumer).unsubscribeAndClose();
    }

    @Test
    public void testDestroy_ClosesResources() {
        when(consumer.poll(anyLong())).thenReturn(List.of());
        notificationsConsumer.startConsuming();

        Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                verify(consumer, atLeastOnce()).poll(anyLong())
        );

        notificationsConsumer.destroy();
        verify(consumer).unsubscribeAndClose();
    }
}
