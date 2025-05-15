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


import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.gen.queue.MqttAuthProviderProto;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.InternodeNotificationsQueueFactory;
import org.thingsboard.mqtt.broker.service.auth.providers.MqttAuthProviderManager;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
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
    private MqttAuthProviderManager mqttAuthProviderManager;

    @Mock
    private TbQueueConsumer<TbProtoQueueMsg<InternodeNotificationProto>> consumer;

    private InternodeNotificationsConsumerImpl notificationsConsumer;

    @Before
    public void setUp() {
        notificationsConsumer = new InternodeNotificationsConsumerImpl(
                queueFactory, helper, serviceInfoProvider, mqttAuthProviderManager
        );

        ReflectionTestUtils.setField(notificationsConsumer, "pollDuration", 1L);

        // Basic mock wiring
        when(serviceInfoProvider.getServiceId()).thenReturn("nodeA");
        when(helper.getServiceTopic("nodeA")).thenReturn("topicA");
        when(queueFactory.createConsumer("topicA", "nodeA")).thenReturn(consumer);
    }

    @Test
    public void testStartConsuming_AndProcessesMessageWithAuthProvider() throws InterruptedException {
        // Prepare a latch to wait for async execution
        CountDownLatch latch = new CountDownLatch(1);

        InternodeNotificationProto proto = InternodeNotificationProto.newBuilder()
                .setMqttAuthProviderProto(MqttAuthProviderProto.getDefaultInstance())
                .build();

        TbProtoQueueMsg<InternodeNotificationProto> msg = new TbProtoQueueMsg<>("key1", proto);

        when(consumer.poll(anyLong()))
                .thenAnswer(invocation -> {
                    latch.countDown();
                    return List.of(msg);
                }).thenReturn(List.of()); // to avoid continuous processing

        notificationsConsumer.startConsuming();

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

        verify(mqttAuthProviderManager).handleProviderNotification(proto.getMqttAuthProviderProto());

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
