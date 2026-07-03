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
import org.thingsboard.mqtt.broker.integration.service.processing.backpressure.IntegrationAckStrategyFactory;
import org.thingsboard.mqtt.broker.integration.service.processing.backpressure.IntegrationSubmitStrategyFactory;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.integration.IntegrationMsgQueueProvider;
import org.thingsboard.mqtt.broker.service.queue.IntegrationTopicService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Execution(ExecutionMode.SAME_THREAD)
class IntegrationMsgProcessorImplEventsOptOutTest {

    private static final String ID = "0198e1a0-aaaa-bbbb-cccc-ddddeeee0001";
    private static final String EVENT_TOPIC = "tbmq.ie.event.optout";
    private static final String EVENT_GROUP = "ie-event-cg-optout";

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

    /**
     * Simulates an integration UPDATE that removes lifecycleEventTypes (opt-out):
     * 1) start with opt-in  → events consumer is created
     * 2) stop               → events consumer is torn down
     * 3) start with opt-out → events consumer must NOT be recreated
     * Also verifies that a subsequent opt-in restart re-creates it (round-trip).
     * Each "restart" uses a fresh integration mock (as AbstractIntegration.update creates a new init),
     * but all share the same integrationId so the processor treats them as the same integration.
     */
    @Test
    void givenOptInThenOptOutRestart_whenStart_thenEventConsumerNotRecreated() {
        // --- common topic stubs ---
        when(topicService.createTopic(ID)).thenReturn("tbmq.msg.ie.optout");
        when(topicService.createEventTopic(ID)).thenReturn(EVENT_TOPIC);
        when(topicService.getConsumerGroup(ID)).thenReturn("ie-msg-cg-optout");
        when(topicService.getEventConsumerGroup(ID)).thenReturn(EVENT_GROUP);

        var dataCons1 = controlledConsumer();
        var dataCons2 = controlledConsumer();
        var dataCons3 = controlledConsumer();
        var eventCons = controlledEventConsumer();
        when(queueProvider.getIeMsgConsumer(anyString(), anyString(), eq(ID)))
                .thenReturn(dataCons1, dataCons2, dataCons3);
        when(queueProvider.getIeEventMsgConsumer(eq(EVENT_TOPIC), eq(EVENT_GROUP), eq(ID)))
                .thenReturn(eventCons);

        // Step 1: start opt-in → events consumer created
        TbPlatformIntegration integrationOptIn1 = integrationMock(ID, true);
        processor.startProcessingIntegrationMessages(integrationOptIn1);

        verify(topicService, times(1)).createEventTopic(ID);
        verify(queueProvider, times(1)).getIeEventMsgConsumer(EVENT_TOPIC, EVENT_GROUP, ID);

        // Step 2: stop → both data and events consumers are torn down
        processor.stopProcessingIntegrationMessages(ID);

        // Step 3: restart with opt-out → NO new events consumer
        TbPlatformIntegration integrationOptOut = integrationMock(ID, false);
        processor.startProcessingIntegrationMessages(integrationOptOut);

        // createEventTopic and getIeEventMsgConsumer still called exactly once overall
        verify(topicService, times(1)).createEventTopic(ID);
        verify(queueProvider, times(1)).getIeEventMsgConsumer(anyString(), anyString(), anyString());

        // Step 4 (round-trip): stop and restart with opt-in again → events consumer recreated
        processor.stopProcessingIntegrationMessages(ID);
        TbPlatformIntegration integrationOptIn2 = integrationMock(ID, true);
        processor.startProcessingIntegrationMessages(integrationOptIn2);

        verify(topicService, times(2)).createEventTopic(ID);
        verify(queueProvider, times(2)).getIeEventMsgConsumer(EVENT_TOPIC, EVENT_GROUP, ID);
    }

    /**
     * If createEventTopic throws (e.g. transient Kafka error), the data-path start
     * must still complete successfully — events are best-effort.
     */
    @Test
    void givenCreateEventTopicThrows_whenStart_thenDataStartNotAffected() {
        when(topicService.createTopic(ID)).thenReturn("tbmq.msg.ie.optout");
        when(topicService.getConsumerGroup(ID)).thenReturn("ie-msg-cg-optout");
        when(topicService.createEventTopic(ID)).thenThrow(new RuntimeException("kafka down"));

        var dataCons = controlledConsumer();
        when(queueProvider.getIeMsgConsumer(anyString(), anyString(), eq(ID))).thenReturn(dataCons);

        TbPlatformIntegration integration = integrationMock(ID, true);
        assertDoesNotThrow(() -> processor.startProcessingIntegrationMessages(integration));

        // data consumer must still be created despite the events topic failure
        verify(queueProvider).getIeMsgConsumer(anyString(), anyString(), eq(ID));
    }

    // ---- helpers ----

    private TbPlatformIntegration integrationMock(String id, boolean optIn) {
        TbPlatformIntegration integration = mock(TbPlatformIntegration.class);
        when(integration.getIntegrationId()).thenReturn(id);
        when(integration.getLifecycleMsg()).thenReturn(optedIn(optIn));
        return integration;
    }

    private IntegrationLifecycleMsg optedIn(boolean optIn) {
        String json = optIn
                ? "{\"lifecycleEventTypes\":[\"CLIENT_CONNECTED\"]}"
                : "{\"topicFilters\":[\"a/b\"]}";
        return IntegrationLifecycleMsg.builder()
                .integrationId(UUID.fromString(ID))
                .name("ie-optout")
                .configuration(JacksonUtil.toJsonNode(json))
                .build();
    }

    @SuppressWarnings("unchecked")
    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishIntegrationMsgProto>> controlledConsumer() {
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishIntegrationMsgProto>> c =
                mock(TbQueueControlledOffsetConsumer.class);
        doReturn("tbmq.msg.ie.optout").when(c).getTopic();
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
