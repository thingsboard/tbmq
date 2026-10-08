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
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationLifecycleMsg;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.integration.api.TbPlatformIntegration;
import org.thingsboard.mqtt.broker.integration.api.callback.IntegrationMsgCallback;
import org.thingsboard.mqtt.broker.integration.service.processing.backpressure.IntegrationAckStrategyConfiguration;
import org.thingsboard.mqtt.broker.integration.service.processing.backpressure.IntegrationAckStrategyFactory;
import org.thingsboard.mqtt.broker.integration.service.processing.backpressure.IntegrationAckStrategyType;
import org.thingsboard.mqtt.broker.integration.service.processing.backpressure.IntegrationEventAckStrategyConfiguration;
import org.thingsboard.mqtt.broker.integration.service.processing.backpressure.IntegrationSubmitStrategyFactory;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.integration.IntegrationMsgQueueProvider;
import org.thingsboard.mqtt.broker.service.queue.IntegrationTopicService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A stop that lands while a pack is in flight: the integration's stop fails the messages it did not get to write, and
 * committing that pack under SKIP_ALL would lose them. The pack stays uncommitted, so the next consumer of the
 * integration's topic (after an update, a restart or on another executor) gets it again.
 */
@Execution(ExecutionMode.SAME_THREAD)
class IntegrationMsgProcessorImplStopTest {

    private static final String ID = "0198e1a0-1111-2222-3333-444455556666";
    private static final long WAIT_MS = 5000;

    private IntegrationMsgQueueProvider queueProvider;
    private IntegrationTopicService topicService;
    private IntegrationMsgProcessorImpl processor;
    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishIntegrationMsgProto>> consumer;
    private TbPlatformIntegration integration;

    @BeforeEach
    void setUp() {
        queueProvider = mock(IntegrationMsgQueueProvider.class);
        topicService = mock(IntegrationTopicService.class);
        IntegrationAckStrategyConfiguration ack = new IntegrationAckStrategyConfiguration();
        ack.setType(IntegrationAckStrategyType.SKIP_ALL);
        processor = new IntegrationMsgProcessorImpl(
                queueProvider, topicService,
                new IntegrationAckStrategyFactory(ack, new IntegrationEventAckStrategyConfiguration()),
                new IntegrationSubmitStrategyFactory(),
                Optional.empty());
        ReflectionTestUtils.setField(processor, "pollDuration", 10L);
        ReflectionTestUtils.setField(processor, "packProcessingTimeout", WAIT_MS);
        processor.init();

        integration = mock(TbPlatformIntegration.class);
        when(integration.getIntegrationId()).thenReturn(ID);
        when(integration.getLifecycleMsg()).thenReturn(IntegrationLifecycleMsg.builder()
                .integrationId(UUID.fromString(ID))
                .name("ie")
                .configuration(JacksonUtil.toJsonNode("{\"topicFilters\":[\"a/b\"]}"))
                .build());
        when(topicService.createTopic(ID)).thenReturn("tbmq.msg.ie.x");
        when(topicService.getConsumerGroup(ID)).thenReturn("ie-msg-consumer-group-x");
        consumer = consumerWithOnePack(msg("m1"), msg("m2"));
        when(queueProvider.getIeMsgConsumer(anyString(), anyString(), eq(ID))).thenReturn(consumer);
    }

    @AfterEach
    void tearDown() {
        processor.destroy();
    }

    @Test
    void givenStopDuringPack_whenAMessageFailsBecauseOfTheStop_thenThePackIsNotCommitted() throws Exception {
        CountDownLatch secondDispatched = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        doAnswer(inv -> {
            PublishIntegrationMsgProto msg = inv.getArgument(0);
            IntegrationMsgCallback callback = inv.getArgument(1);
            if ("m1".equals(msg.getPublishMsgProto().getTopicName())) {
                callback.onSuccess();
            } else {
                secondDispatched.countDown();
                assertThat(stopped.await(WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
                callback.onFailure(new IllegalStateException("Integration is not initialized"));
            }
            return null;
        }).when(integration).process(any(), any());

        processor.startProcessingIntegrationMessages(integration);
        assertThat(secondDispatched.await(WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
        processor.stopProcessingIntegrationMessages(ID);
        stopped.countDown();

        verify(consumer, timeout(WAIT_MS)).unsubscribeAndClose();
        verify(consumer, never()).commitSync();
    }

    @Test
    void givenStopDuringPack_whenEveryMessageSucceeds_thenThePackIsCommitted() throws Exception {
        CountDownLatch secondDispatched = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        doAnswer(inv -> {
            PublishIntegrationMsgProto msg = inv.getArgument(0);
            IntegrationMsgCallback callback = inv.getArgument(1);
            if ("m2".equals(msg.getPublishMsgProto().getTopicName())) {
                secondDispatched.countDown();
                assertThat(stopped.await(WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
            }
            callback.onSuccess();
            return null;
        }).when(integration).process(any(), any());

        processor.startProcessingIntegrationMessages(integration);
        assertThat(secondDispatched.await(WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
        processor.stopProcessingIntegrationMessages(ID);
        stopped.countDown();

        verify(consumer, timeout(WAIT_MS)).unsubscribeAndClose();
        verify(consumer).commitSync();
    }

    private static TbProtoQueueMsg<PublishIntegrationMsgProto> msg(String topic) {
        return new TbProtoQueueMsg<>(PublishIntegrationMsgProto.newBuilder()
                .setPublishMsgProto(PublishMsgProto.newBuilder().setTopicName(topic))
                .build());
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    private static TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishIntegrationMsgProto>> consumerWithOnePack(
            TbProtoQueueMsg<PublishIntegrationMsgProto>... pack) {
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishIntegrationMsgProto>> c =
                mock(TbQueueControlledOffsetConsumer.class);
        doReturn("tbmq.msg.ie.x").when(c).getTopic();
        doReturn(Optional.of(0L)).when(c).getCommittedOffset(anyString(), anyInt());
        doReturn(List.of(pack)).doReturn(List.of()).when(c).poll(anyLong());
        return c;
    }
}
