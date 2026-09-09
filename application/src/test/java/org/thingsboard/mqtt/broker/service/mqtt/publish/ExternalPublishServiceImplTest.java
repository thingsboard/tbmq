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
package org.thingsboard.mqtt.broker.service.mqtt.publish;

import io.netty.handler.codec.mqtt.MqttProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.dao.topic.TopicValidationService;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.limits.ThroughputQuotaService;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgProcessor;
import org.thingsboard.mqtt.broker.service.processing.MsgDispatcherService;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalPublishServiceImplTest {

    private TopicValidationService topicValidationService;
    private ThroughputQuotaService throughputQuotaService;
    private RetainedMsgProcessor retainedMsgProcessor;
    private MsgDispatcherService msgDispatcherService;
    private TbMessageStatsReportClient stats;
    private ExternalPublishServiceImpl service;

    @BeforeEach
    void setUp() {
        topicValidationService = mock(TopicValidationService.class);
        throughputQuotaService = mock(ThroughputQuotaService.class);
        retainedMsgProcessor = mock(RetainedMsgProcessor.class);
        msgDispatcherService = mock(MsgDispatcherService.class);
        stats = mock(TbMessageStatsReportClient.class);
        service = new ExternalPublishServiceImpl(topicValidationService, throughputQuotaService,
                retainedMsgProcessor, msgDispatcherService, stats);
        service.maxPayloadSize = 64;
    }

    @Test
    void publishesThroughTheStandardDispatcher() {
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        ExternalPublishCommand command = new ExternalPublishCommand(
                "devices/a/commands", payload, 1, false, 30, "text/plain");
        TbQueueCallback callback = mock(TbQueueCallback.class);
        when(throughputQuotaService.tryConsumeIncoming()).thenReturn(true);

        service.publish(command, callback);

        ArgumentCaptor<PublishMsg> message = ArgumentCaptor.forClass(PublishMsg.class);
        verify(msgDispatcherService).persistExternalPublishMsg(
                eq(ExternalPublishServiceImpl.REST_PUBLISHER_ID), message.capture(), any());
        assertThat(message.getValue().getTopicName()).isEqualTo("devices/a/commands");
        assertThat(message.getValue().getPayload()).isEqualTo(payload);
        assertThat(message.getValue().getQos()).isEqualTo(1);
        assertThat(message.getValue().isRetained()).isFalse();
        MqttProperties.IntegerProperty expiry = (MqttProperties.IntegerProperty) message.getValue().getProperties()
                .getProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID);
        MqttProperties.StringProperty contentType = (MqttProperties.StringProperty) message.getValue().getProperties()
                .getProperty(BrokerConstants.CONTENT_TYPE_PROP_ID);
        assertThat(expiry.value()).isEqualTo(30);
        assertThat(contentType.value()).isEqualTo("text/plain");
    }

    @Test
    void processesRetainedMessageBeforeDispatch() {
        ExternalPublishCommand command = new ExternalPublishCommand("devices/a/state", new byte[]{1}, 1,
                true, null, null);
        TbQueueCallback callback = mock(TbQueueCallback.class);
        when(throughputQuotaService.tryConsumeIncoming()).thenReturn(true);
        when(retainedMsgProcessor.process(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.publish(command, callback);

        verify(retainedMsgProcessor).process(any(PublishMsg.class));
        verify(msgDispatcherService).persistExternalPublishMsg(
                eq(ExternalPublishServiceImpl.REST_PUBLISHER_ID), any(PublishMsg.class), any());
    }

    @Test
    void rejectsPublishWhenQuotaIsExceeded() {
        ExternalPublishCommand command = new ExternalPublishCommand("devices/a/commands", new byte[]{1}, 0,
                false, null, null);
        TbQueueCallback callback = mock(TbQueueCallback.class);
        when(throughputQuotaService.tryConsumeIncoming()).thenReturn(false);

        service.publish(command, callback);

        verify(callback).onFailure(any(ExternalPublishRateLimitException.class));
        verify(stats).reportDroppedMsgs();
        verify(msgDispatcherService, never()).persistExternalPublishMsg(any(), any(), any());
    }

    @Test
    void reportsQueueFailureAsDroppedMessage() {
        ExternalPublishCommand command = new ExternalPublishCommand("devices/a/commands", new byte[]{1}, 0,
                false, null, null);
        TbQueueCallback callback = mock(TbQueueCallback.class);
        when(throughputQuotaService.tryConsumeIncoming()).thenReturn(true);

        service.publish(command, callback);
        ArgumentCaptor<TbQueueCallback> queueCallback = ArgumentCaptor.forClass(TbQueueCallback.class);
        verify(msgDispatcherService).persistExternalPublishMsg(any(), any(), queueCallback.capture());
        RuntimeException failure = new RuntimeException("queue unavailable");
        queueCallback.getValue().onFailure(failure);

        verify(stats).reportDroppedMsgs();
        verify(callback).onFailure(failure);
    }

    @Test
    void rejectsOversizedPayload() {
        byte[] payload = new byte[65];
        ExternalPublishCommand command = new ExternalPublishCommand("devices/a/commands", payload, 0,
                false, null, null);

        assertThatThrownBy(() -> service.publish(command, mock(TbQueueCallback.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64 bytes");
        verify(throughputQuotaService, never()).tryConsumeIncoming();
    }

    @Test
    void propagatesQueueSuccess() {
        ExternalPublishCommand command = new ExternalPublishCommand("devices/a/commands", new byte[]{1}, 0,
                false, null, null);
        TbQueueCallback callback = mock(TbQueueCallback.class);
        TbQueueMsgMetadata metadata = mock(TbQueueMsgMetadata.class);
        when(throughputQuotaService.tryConsumeIncoming()).thenReturn(true);

        service.publish(command, callback);
        ArgumentCaptor<TbQueueCallback> queueCallback = ArgumentCaptor.forClass(TbQueueCallback.class);
        verify(msgDispatcherService).persistExternalPublishMsg(any(), any(), queueCallback.capture());
        queueCallback.getValue().onSuccess(metadata);

        verify(callback).onSuccess(metadata);
    }

}
