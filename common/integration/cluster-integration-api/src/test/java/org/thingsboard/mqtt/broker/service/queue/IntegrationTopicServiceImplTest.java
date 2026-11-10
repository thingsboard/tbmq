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
package org.thingsboard.mqtt.broker.service.queue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumerGroupNotFoundException;
import org.thingsboard.mqtt.broker.queue.provider.integration.IntegrationMsgQueueProvider;
import org.thingsboard.mqtt.broker.service.util.IntegrationHelperService;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationTopicServiceImplTest {

    static final String INTEGRATION_ID = "0198e1a0-1111-2222-3333-444455556666";
    static final String EVENT_TOPIC = "tbmq.ie.event." + "0198e1a0111122223333444455556666";
    static final String EVENT_GROUP = "ie-event-consumer-group-" + "0198e1a0111122223333444455556666";
    static final String DATA_TOPIC = "tbmq.ie." + "0198e1a0111122223333444455556666";
    static final String DATA_GROUP = "ie-consumer-group-" + "0198e1a0111122223333444455556666";

    @Mock
    TbQueueAdmin queueAdmin;
    @Mock
    IntegrationMsgQueueProvider integrationMsgQueueProvider;
    @Mock
    IntegrationHelperService integrationHelperService;

    @InjectMocks
    IntegrationTopicServiceImpl service;

    @Test
    void givenIntegrationId_whenCreateEventTopic_thenCreatesTopicWithEventConfigs() {
        Map<String, String> cfg = Map.of("retention.ms", "60000");
        when(integrationHelperService.getIntegrationEventTopic(INTEGRATION_ID)).thenReturn(EVENT_TOPIC);
        when(integrationMsgQueueProvider.getIeEventMsgTopicConfigs()).thenReturn(cfg);

        String topic = service.createEventTopic(INTEGRATION_ID);

        assertThat(topic).isEqualTo(EVENT_TOPIC);
        verify(queueAdmin).createTopic(EVENT_TOPIC, cfg);
    }

    @Test
    void givenIntegrationId_whenDeleteEventTopic_thenDeletesGroupAndTopic() throws Exception {
        when(integrationHelperService.getIntegrationEventConsumerGroup(INTEGRATION_ID)).thenReturn(EVENT_GROUP);
        when(integrationHelperService.getIntegrationEventTopic(INTEGRATION_ID)).thenReturn(EVENT_TOPIC);
        BasicCallback cb = mock(BasicCallback.class);

        service.deleteEventTopic(INTEGRATION_ID, cb);

        verify(queueAdmin).deleteConsumerGroup(EVENT_GROUP);
        verify(queueAdmin).deleteTopic(EVENT_TOPIC, cb);
    }

    @Test
    void givenMissingConsumerGroup_whenDeleteTopic_thenTopicIsStillDeleted() throws Exception {
        when(integrationHelperService.getIntegrationConsumerGroup(INTEGRATION_ID)).thenReturn(DATA_GROUP);
        when(integrationHelperService.getIntegrationTopic(INTEGRATION_ID)).thenReturn(DATA_TOPIC);
        doThrow(new TbQueueConsumerGroupNotFoundException(DATA_GROUP, null)).when(queueAdmin).deleteConsumerGroup(DATA_GROUP);
        BasicCallback cb = mock(BasicCallback.class);

        service.deleteTopic(INTEGRATION_ID, cb);

        verify(queueAdmin).deleteTopic(DATA_TOPIC, cb);
    }

    @Test
    void givenMissingConsumerGroup_whenDeleteEventTopic_thenTopicIsStillDeleted() throws Exception {
        when(integrationHelperService.getIntegrationEventConsumerGroup(INTEGRATION_ID)).thenReturn(EVENT_GROUP);
        when(integrationHelperService.getIntegrationEventTopic(INTEGRATION_ID)).thenReturn(EVENT_TOPIC);
        doThrow(new TbQueueConsumerGroupNotFoundException(EVENT_GROUP, null)).when(queueAdmin).deleteConsumerGroup(EVENT_GROUP);
        BasicCallback cb = mock(BasicCallback.class);

        service.deleteEventTopic(INTEGRATION_ID, cb);

        verify(queueAdmin).deleteTopic(EVENT_TOPIC, cb);
    }

    @Test
    void givenConsumerGroupDeleteTimeout_whenDeleteTopic_thenRethrowsAndKeepsTopic() throws Exception {
        when(integrationHelperService.getIntegrationConsumerGroup(INTEGRATION_ID)).thenReturn(DATA_GROUP);
        TimeoutException timeout = new TimeoutException();
        doThrow(timeout).when(queueAdmin).deleteConsumerGroup(DATA_GROUP);
        BasicCallback cb = mock(BasicCallback.class);

        assertThatThrownBy(() -> service.deleteTopic(INTEGRATION_ID, cb))
                .isInstanceOf(RuntimeException.class)
                .hasCause(timeout);

        verify(queueAdmin, never()).deleteTopic(any(), any());
    }

}
