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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.integration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.gen.integration.ClientLifecycleEventMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.integration.IntegrationMsgQueueProvider;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;
import org.thingsboard.mqtt.broker.service.util.IntegrationHelperService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class IntegrationEventMsgQueuePublisherImplTest {

    @Mock
    private ClientLogger clientLogger;
    @Mock
    private IntegrationMsgQueueProvider msgQueueProvider;
    @Mock
    private IntegrationHelperService integrationHelperService;
    @Mock
    private TbQueueProducer<TbProtoQueueMsg<ClientLifecycleEventMsgProto>> producer;

    private IntegrationEventMsgQueuePublisherImpl publisher;

    private static final String INTEGRATION_ID = "ie-1";
    private static final String EVENT_TOPIC = "tbmq.ie.event.ie_1";

    @Before
    public void setUp() {
        when(msgQueueProvider.getIeEventMsgProducer()).thenReturn(producer);
        publisher = new IntegrationEventMsgQueuePublisherImpl(clientLogger, msgQueueProvider, integrationHelperService);
        publisher.init();
    }

    @Test
    public void givenEventMsg_whenSendEventMsg_thenForwardsToEventTopicOnPartitionZero() {
        when(integrationHelperService.getIntegrationEventTopic(INTEGRATION_ID)).thenReturn(EVENT_TOPIC);
        TbProtoQueueMsg<ClientLifecycleEventMsgProto> queueMsg =
                new TbProtoQueueMsg<>(UUID.randomUUID(), ClientLifecycleEventMsgProto.newBuilder().build());

        publisher.sendEventMsg(INTEGRATION_ID, queueMsg, PublishMsgCallback.EMPTY);

        verify(producer).send(eq(EVENT_TOPIC), eq(0), eq(queueMsg), any(TbQueueCallback.class));
    }

}
