/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthProviderProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.InternodeNotificationsQueueFactory;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttAuthProviderManager;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private MqttAuthProviderManager mqttAuthProviderManager;

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
                mqttAuthProviderManager
        );
        service.init();
    }

    @Test
    public void testBroadcast_ToAnotherNode() {
        InternodeNotificationProto proto = InternodeNotificationProto.getDefaultInstance();

        when(helper.getServiceIds()).thenReturn(List.of("nodeA", "nodeB"));
        when(helper.getServiceTopic("nodeB")).thenReturn("topicB");

        service.broadcast(proto);

        verify(producer).send(
                eq("topicB"),
                isNull(),
                argThat(msg -> "nodeB".equals(msg.getKey()) && msg.getValue().equals(proto)),
                any(TbQueueCallback.class)
        );
    }

    @Test
    public void testBroadcast_ToSelf_WithAuthProvider() {
        InternodeNotificationProto proto = InternodeNotificationProto.newBuilder()
                .setMqttAuthProviderProto(MqttAuthProviderProto.getDefaultInstance())
                .build();

        when(helper.getServiceIds()).thenReturn(List.of("nodeA"));

        service.broadcast(proto);

        verify(mqttAuthProviderManager).handleProviderNotification(proto.getMqttAuthProviderProto());
        verify(producer, never()).send(any(), any(), any(), any());
    }

    @Test
    public void testBroadcast_ToSelf_WithoutAuthProvider() {
        InternodeNotificationProto proto = InternodeNotificationProto.getDefaultInstance();

        when(helper.getServiceIds()).thenReturn(Collections.singletonList("nodeA"));

        service.broadcast(proto);

        verify(mqttAuthProviderManager, never()).handleProviderNotification(any());
    }

    @Test
    public void testDestroy_ShouldStopProducer() {
        service.destroy();
        verify(producer).stop();
    }
}

