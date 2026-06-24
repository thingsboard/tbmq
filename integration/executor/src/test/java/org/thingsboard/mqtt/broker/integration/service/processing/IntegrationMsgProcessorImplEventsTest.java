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
package org.thingsboard.mqtt.broker.integration.service.processing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationLifecycleMsg;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.gen.integration.ClientLifecycleEventMsgProto;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.integration.api.TbPlatformIntegration;
import org.thingsboard.mqtt.broker.integration.api.callback.IntegrationMsgCallback;
import org.thingsboard.mqtt.broker.integration.api.data.IntegrationPackProcessingContext;
import org.thingsboard.mqtt.broker.integration.service.data.IntegrationHolder;
import org.thingsboard.mqtt.broker.integration.service.processing.backpressure.IntegrationAckStrategyFactory;
import org.thingsboard.mqtt.broker.integration.service.processing.backpressure.IntegrationSubmitStrategyFactory;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.integration.IntegrationMsgQueueProvider;
import org.thingsboard.mqtt.broker.service.queue.IntegrationTopicService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Execution(ExecutionMode.SAME_THREAD)
class IntegrationMsgProcessorImplEventsTest {

    private static final String ID = "0198e1a0-1111-2222-3333-444455556666";
    private static final String EVENT_TOPIC = "tbmq.ie.event.x";
    private static final String EVENT_GROUP = "ie-event-consumer-group-x";

    private IntegrationMsgQueueProvider queueProvider;
    private IntegrationTopicService topicService;
    private IntegrationMsgProcessorImpl processor;

    @BeforeEach
    void setUp() {
        queueProvider = mock(IntegrationMsgQueueProvider.class);
        topicService = mock(IntegrationTopicService.class);
        processor = new IntegrationMsgProcessorImpl(
                queueProvider, topicService,
                mock(IntegrationAckStrategyFactory.class),
                mock(IntegrationSubmitStrategyFactory.class),
                Optional.empty());
        processor.init();
    }

    @AfterEach
    void tearDown() {
        processor.destroy();
    }

    @Test
    void givenPolledLifecycleEvent_whenDispatchEvent_thenRoutedToProcessLifecycleEvent() {
        TbPlatformIntegration integration = mock(TbPlatformIntegration.class);
        when(integration.getIntegrationId()).thenReturn(ID);
        IntegrationHolder holder = new IntegrationHolder(integration);

        ClientLifecycleEventMsgProto event = ClientLifecycleEventMsgProto.newBuilder()
                .setEventType("CLIENT_CONNECTED").setClientId("c1").build();
        UUID packetId = new UUID(1L, 0L);
        IntegrationPackProcessingContext<ClientLifecycleEventMsgProto> ctx =
                new IntegrationPackProcessingContext<>(ID, new ConcurrentHashMap<>());

        processor.dispatchEvent(holder, packetId, event, ctx);

        verify(integration).processLifecycleEvent(eq(event), any(IntegrationMsgCallback.class));
    }

    @Test
    void givenOptedInIntegration_whenStart_thenCreatesEventTopicAndConsumer() {
        TbPlatformIntegration integration = mock(TbPlatformIntegration.class);
        when(integration.getIntegrationId()).thenReturn(ID);
        when(integration.getLifecycleMsg()).thenReturn(optedIn(true));
        when(topicService.createTopic(ID)).thenReturn("tbmq.msg.ie.x");
        when(topicService.createEventTopic(ID)).thenReturn(EVENT_TOPIC);
        when(topicService.getConsumerGroup(ID)).thenReturn("ie-msg-consumer-group-x");
        when(topicService.getEventConsumerGroup(ID)).thenReturn(EVENT_GROUP);
        var dataCons = controlledConsumer();
        var eventCons = controlledEventConsumer();
        when(queueProvider.getIeMsgConsumer(anyString(), anyString(), eq(ID))).thenReturn(dataCons);
        when(queueProvider.getIeEventMsgConsumer(eq(EVENT_TOPIC), eq(EVENT_GROUP), eq(ID))).thenReturn(eventCons);

        processor.startProcessingIntegrationMessages(integration);

        verify(topicService, timeout(2000)).createEventTopic(ID);
        verify(queueProvider, timeout(2000)).getIeEventMsgConsumer(EVENT_TOPIC, EVENT_GROUP, ID);
    }

    @Test
    void givenOptedOutIntegration_whenStart_thenNoEventTopicNorConsumer() {
        TbPlatformIntegration integration = mock(TbPlatformIntegration.class);
        when(integration.getIntegrationId()).thenReturn(ID);
        when(integration.getLifecycleMsg()).thenReturn(optedIn(false));
        when(topicService.createTopic(ID)).thenReturn("tbmq.msg.ie.x");
        when(topicService.getConsumerGroup(ID)).thenReturn("ie-msg-consumer-group-x");
        var dataCons = controlledConsumer();
        when(queueProvider.getIeMsgConsumer(anyString(), anyString(), eq(ID))).thenReturn(dataCons);

        processor.startProcessingIntegrationMessages(integration);

        verify(topicService, never()).createEventTopic(anyString());
        verify(queueProvider, never()).getIeEventMsgConsumer(anyString(), anyString(), anyString());
    }

    @Test
    void givenStartedEvents_whenStop_thenEventConsumerCancelled() {
        TbPlatformIntegration integration = mock(TbPlatformIntegration.class);
        when(integration.getIntegrationId()).thenReturn(ID);
        when(integration.getLifecycleMsg()).thenReturn(optedIn(true));
        when(topicService.createTopic(ID)).thenReturn("tbmq.msg.ie.x");
        when(topicService.createEventTopic(ID)).thenReturn(EVENT_TOPIC);
        when(topicService.getConsumerGroup(ID)).thenReturn("ie-msg-consumer-group-x");
        when(topicService.getEventConsumerGroup(ID)).thenReturn(EVENT_GROUP);
        var dataCons = controlledConsumer();
        var eventCons = controlledEventConsumer();
        when(queueProvider.getIeMsgConsumer(anyString(), anyString(), eq(ID))).thenReturn(dataCons);
        when(queueProvider.getIeEventMsgConsumer(eq(EVENT_TOPIC), eq(EVENT_GROUP), eq(ID))).thenReturn(eventCons);

        processor.startProcessingIntegrationMessages(integration);
        verify(eventCons, timeout(2000).atLeastOnce()).poll(anyLong());

        processor.stopProcessingIntegrationMessages(ID);

        verify(eventCons, timeout(2000)).unsubscribeAndClose();
    }

    // ---- helpers ----

    private IntegrationLifecycleMsg optedIn(boolean optIn) {
        String json = optIn
                ? "{\"lifecycleEventTypes\":[\"CLIENT_CONNECTED\"]}"
                : "{\"topicFilters\":[\"a/b\"]}";
        return IntegrationLifecycleMsg.builder()
                .integrationId(UUID.fromString(ID))
                .name("ie")
                .configuration(JacksonUtil.toJsonNode(json))
                .build();
    }

    @SuppressWarnings("unchecked")
    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishIntegrationMsgProto>> controlledConsumer() {
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishIntegrationMsgProto>> c =
                mock(TbQueueControlledOffsetConsumer.class);
        doReturn("tbmq.msg.ie.x").when(c).getTopic();
        doReturn(Optional.of(0L)).when(c).getCommittedOffset(anyString(), anyInt());
        doReturn(List.of()).when(c).poll(anyLong());
        return c;
    }

    @SuppressWarnings("unchecked")
    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<ClientLifecycleEventMsgProto>> controlledEventConsumer() {
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<ClientLifecycleEventMsgProto>> c =
                mock(TbQueueControlledOffsetConsumer.class);
        doReturn(EVENT_TOPIC).when(c).getTopic();
        doReturn(Optional.of(0L)).when(c).getCommittedOffset(anyString(), anyInt());
        doReturn(List.of()).when(c).poll(anyLong());
        return c;
    }
}
