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

import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionService;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.topic.TopicValidationService;
import org.thingsboard.mqtt.broker.dto.PayloadEncoding;
import org.thingsboard.mqtt.broker.dto.RestPublishProperties;
import org.thingsboard.mqtt.broker.dto.RestPublishRequest;
import org.thingsboard.mqtt.broker.dto.RestPublishResponse;
import org.thingsboard.mqtt.broker.exception.DataValidationException;
import org.thingsboard.mqtt.broker.exception.TbRateLimitsException;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.limits.ThroughputQuotaService;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgProcessor;
import org.thingsboard.mqtt.broker.service.processing.MsgDispatcherService;
import org.thingsboard.mqtt.broker.service.stats.RestPublishStats;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.subscription.EntitySubscription;
import org.thingsboard.mqtt.broker.service.subscription.ValueWithTopicFilter;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestPublishServiceImplTest {

    private static final String TOPIC = "devices/a/commands";

    @Mock
    TopicValidationService topicValidationService;
    @Mock
    ThroughputQuotaService throughputQuotaService;
    @Mock
    RetainedMsgProcessor retainedMsgProcessor;
    @Mock
    SubscriptionService subscriptionService;
    @Mock
    MsgDispatcherService msgDispatcherService;
    @Mock
    TbMessageStatsReportClient tbMessageStatsReportClient;
    @Mock
    StatsManager statsManager;
    @Mock
    RestPublishStats restPublishStats;

    @InjectMocks
    RestPublishServiceImpl service;

    @BeforeEach
    void setUp() {
        when(statsManager.getRestPublishStats()).thenReturn(restPublishStats);
        service.maxPayloadSize = 64;
        service.init();
    }

    @Test
    void givenPlainPayload_whenPublish_thenDispatchesUtf8BytesUnderRestApiClientId() {
        when(throughputQuotaService.tryConsumeIncoming()).thenReturn(true);

        service.publish(request("hello", PayloadEncoding.TEXT));

        PublishMsg dispatched = capturedPublishMsg();
        assertThat(dispatched.getTopicName()).isEqualTo(TOPIC);
        assertThat(dispatched.getPayload()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        assertThat(dispatched.getQos()).isEqualTo(1);
        assertThat(dispatched.isRetained()).isFalse();
        assertThat(dispatched.isDup()).isFalse();
        assertThat(dispatched.getPacketId()).isZero();
        verify(msgDispatcherService).persistPublishMsg(eq(BrokerConstants.REST_API_CLIENT_ID), any(), any());
    }

    @Test
    void givenJsonObjectPayload_whenPublish_thenDispatchesCompactJsonText() {
        when(throughputQuotaService.tryConsumeIncoming()).thenReturn(true);
        RestPublishRequest request = request("ignored", PayloadEncoding.JSON);
        request.setPayload(JacksonUtil.toJsonNode("{\"cmd\": \"reboot\", \"delay\": 5}"));

        service.publish(request);

        assertThat(new String(capturedPublishMsg().getPayload(), StandardCharsets.UTF_8)).isEqualTo("{\"cmd\":\"reboot\",\"delay\":5}");
    }

    @Test
    void givenJsonNumberPayload_whenPublish_thenDispatchesItsText() {
        when(throughputQuotaService.tryConsumeIncoming()).thenReturn(true);
        RestPublishRequest request = request("ignored", PayloadEncoding.JSON);
        request.setPayload(JacksonUtil.toJsonNode("42"));

        service.publish(request);

        assertThat(capturedPublishMsg().getPayload()).isEqualTo("42".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void givenJsonNullPayload_whenPublish_thenRejects() {
        RestPublishRequest request = request("ignored", PayloadEncoding.TEXT);
        request.setPayload(NullNode.getInstance());

        assertThatThrownBy(() -> service.publish(request))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("Payload is required");
        verify(throughputQuotaService, never()).tryConsumeIncoming();
    }

    @Test
    void givenJsonEncodingWithStringPayload_whenPublish_thenDispatchesQuotedJsonString() {
        when(throughputQuotaService.tryConsumeIncoming()).thenReturn(true);

        service.publish(request("hello", PayloadEncoding.JSON));

        assertThat(capturedPublishMsg().getPayload()).isEqualTo("\"hello\"".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void givenTextEncodingWithNonStringPayload_whenPublish_thenRejects() {
        RestPublishRequest request = request("ignored", PayloadEncoding.TEXT);
        request.setPayload(JacksonUtil.toJsonNode("{\"cmd\":\"reboot\"}"));

        assertThatThrownBy(() -> service.publish(request))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("TEXT")
                .hasMessageContaining("JSON");
        verify(throughputQuotaService, never()).tryConsumeIncoming();
    }

    @Test
    void givenLegacyTopLevelProperties_whenPublish_thenMappedOntoPublishMsg() {
        when(throughputQuotaService.tryConsumeIncoming()).thenReturn(true);
        RestPublishRequest request = request("hello", PayloadEncoding.TEXT);
        request.setMessageExpiryInterval(45);
        request.setContentType("text/csv");

        service.publish(request);

        MqttProperties props = capturedPublishMsg().getProperties();
        assertThat(intProp(props, BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID)).isEqualTo(45);
        assertThat(stringProp(props, BrokerConstants.CONTENT_TYPE_PROP_ID)).isEqualTo("text/csv");
    }

    @Test
    void givenLegacyAndNestedProperties_whenPublish_thenNestedWins() {
        when(throughputQuotaService.tryConsumeIncoming()).thenReturn(true);
        RestPublishRequest request = request("hello", PayloadEncoding.TEXT);
        request.setMessageExpiryInterval(45);
        request.setContentType("text/csv");
        RestPublishProperties properties = new RestPublishProperties();
        properties.setMessageExpiryInterval(30);
        properties.setContentType("text/plain");
        request.setProperties(properties);

        service.publish(request);

        MqttProperties props = capturedPublishMsg().getProperties();
        assertThat(intProp(props, BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID)).isEqualTo(30);
        assertThat(stringProp(props, BrokerConstants.CONTENT_TYPE_PROP_ID)).isEqualTo("text/plain");
    }

    @Test
    void givenBase64EncodingWithNonStringPayload_whenPublish_thenRejects() {
        RestPublishRequest request = request("ignored", PayloadEncoding.BASE64);
        request.setPayload(JacksonUtil.toJsonNode("{\"cmd\":\"reboot\"}"));

        assertThatThrownBy(() -> service.publish(request))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("BASE64");
        verify(throughputQuotaService, never()).tryConsumeIncoming();
    }

    @Test
    void givenBase64Payload_whenPublish_thenDispatchesDecodedBytes() {
        when(throughputQuotaService.tryConsumeIncoming()).thenReturn(true);
        byte[] binary = {0, 1, 2, (byte) 0xFF};

        service.publish(request(Base64.getEncoder().encodeToString(binary), PayloadEncoding.BASE64));

        assertThat(capturedPublishMsg().getPayload()).isEqualTo(binary);
    }

    @Test
    void givenInvalidBase64Payload_whenPublish_thenRejectsBeforeQuota() {
        assertThatThrownBy(() -> service.publish(request("not base64!", PayloadEncoding.BASE64)))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("Base64");
        verify(throughputQuotaService, never()).tryConsumeIncoming();
    }

    @Test
    void givenNullEncoding_whenPublish_thenDefaultsToBase64() {
        when(throughputQuotaService.tryConsumeIncoming()).thenReturn(true);
        byte[] binary = {0, 1, 2, (byte) 0xFF};

        service.publish(request(Base64.getEncoder().encodeToString(binary), null));

        assertThat(capturedPublishMsg().getPayload()).isEqualTo(binary);
    }

    @Test
    void givenOversizedDecodedPayload_whenPublish_thenRejectsBeforeQuota() {
        String payload = "x".repeat(65);

        assertThatThrownBy(() -> service.publish(request(payload, PayloadEncoding.TEXT)))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("64 bytes");
        verify(throughputQuotaService, never()).tryConsumeIncoming();
        verify(msgDispatcherService, never()).persistPublishMsg(anyString(), any(), any());
    }

    @Test
    void givenInvalidTopic_whenPublish_thenTopicValidationFailurePropagates() {
        doThrow(new DataValidationException("Topic name cannot contain wildcard characters!"))
                .when(topicValidationService).validateTopic("devices/#");
        RestPublishRequest request = request("hello", PayloadEncoding.TEXT);
        request.setTopic("devices/#");

        assertThatThrownBy(() -> service.publish(request))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("wildcard");
        verify(throughputQuotaService, never()).tryConsumeIncoming();
    }

    @Test
    void givenInvalidResponseTopic_whenPublish_thenTopicValidationFailurePropagates() {
        // the main topic is validated first with a different argument, so match any and throw only for the response topic
        doAnswer(invocation -> {
            if ("replies/+".equals(invocation.getArgument(0))) {
                throw new DataValidationException("Topic name cannot contain wildcard characters!");
            }
            return null;
        }).when(topicValidationService).validateTopic(anyString());
        RestPublishRequest request = request("hello", PayloadEncoding.TEXT);
        RestPublishProperties properties = new RestPublishProperties();
        properties.setResponseTopic("replies/+");
        request.setProperties(properties);

        assertThatThrownBy(() -> service.publish(request)).isInstanceOf(DataValidationException.class);
        verify(throughputQuotaService, never()).tryConsumeIncoming();
    }

    @Test
    void givenQuotaExceeded_whenPublish_thenThrowsRateLimitsAndCountsDrop() {
        when(throughputQuotaService.tryConsumeIncoming()).thenReturn(false);

        assertThatThrownBy(() -> service.publish(request("hello", PayloadEncoding.TEXT)))
                .isInstanceOf(TbRateLimitsException.class);

        verify(tbMessageStatsReportClient).reportDroppedMsgs();
        verify(restPublishStats).increment(RestPublishOutcome.QUOTA_EXCEEDED);
        verify(msgDispatcherService, never()).persistPublishMsg(anyString(), any(), any());
    }

    @Test
    void givenAllMqtt5Properties_whenPublish_thenMappedOntoPublishMsg() {
        when(throughputQuotaService.tryConsumeIncoming()).thenReturn(true);
        RestPublishRequest request = request("hello", PayloadEncoding.TEXT);
        RestPublishProperties properties = new RestPublishProperties();
        properties.setPayloadFormatIndicator(1);
        properties.setMessageExpiryInterval(30);
        properties.setContentType("text/plain");
        properties.setResponseTopic("devices/a/replies");
        properties.setCorrelationData(Base64.getEncoder().encodeToString(new byte[]{9, 8}));
        properties.setUserProperties(Map.of("k1", "v1", "k2", "v2"));
        request.setProperties(properties);

        service.publish(request);

        MqttProperties props = capturedPublishMsg().getProperties();
        assertThat(intProp(props, BrokerConstants.PAYLOAD_FORMAT_INDICATOR_PROP_ID)).isEqualTo(1);
        assertThat(intProp(props, BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID)).isEqualTo(30);
        assertThat(stringProp(props, BrokerConstants.CONTENT_TYPE_PROP_ID)).isEqualTo("text/plain");
        assertThat(stringProp(props, BrokerConstants.RESPONSE_TOPIC_PROP_ID)).isEqualTo("devices/a/replies");
        assertThat(((MqttProperties.BinaryProperty) props.getProperty(BrokerConstants.CORRELATION_DATA_PROP_ID)).value())
                .isEqualTo(new byte[]{9, 8});
        MqttProperties.UserProperties userProperties = MqttPropertiesUtil.getUserProperties(props);
        assertThat(userProperties.value()).extracting(p -> p.key, p -> p.value)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("k1", "v1"), org.assertj.core.groups.Tuple.tuple("k2", "v2"));
        verify(topicValidationService).validateTopic("devices/a/replies");
    }

    @Test
    void givenInvalidBase64CorrelationData_whenPublish_thenRejects() {
        RestPublishRequest request = request("hello", PayloadEncoding.TEXT);
        RestPublishProperties properties = new RestPublishProperties();
        properties.setCorrelationData("%%%");
        request.setProperties(properties);

        assertThatThrownBy(() -> service.publish(request))
                .isInstanceOf(DataValidationException.class)
                .hasMessageContaining("Correlation data");
    }

    @Test
    void givenNoProperties_whenPublish_thenNoMqttPropertiesSet() {
        when(throughputQuotaService.tryConsumeIncoming()).thenReturn(true);

        service.publish(request("hello", PayloadEncoding.TEXT));

        assertThat(capturedPublishMsg().getProperties().listAll()).isEmpty();
    }

    @Test
    void givenRetainFlag_whenPublish_thenDispatchesMessageReturnedByRetainedProcessor() {
        when(throughputQuotaService.tryConsumeIncoming()).thenReturn(true);
        PublishMsg processed = PublishMsg.builder().topicName(TOPIC).payload(new byte[]{7}).qos(1).isRetained(true)
                .properties(new MqttProperties()).build();
        when(retainedMsgProcessor.process(any())).thenReturn(processed);
        RestPublishRequest request = request("hello", PayloadEncoding.TEXT);
        request.setRetain(true);

        service.publish(request);

        ArgumentCaptor<PublishMsg> toProcessor = ArgumentCaptor.forClass(PublishMsg.class);
        verify(retainedMsgProcessor).process(toProcessor.capture());
        assertThat(toProcessor.getValue().isRetained()).isTrue();
        assertThat(capturedPublishMsg()).isSameAs(processed);
    }

    @Test
    void givenNoRetainFlag_whenPublish_thenRetainedProcessorNotInvolved() {
        when(throughputQuotaService.tryConsumeIncoming()).thenReturn(true);

        service.publish(request("hello", PayloadEncoding.TEXT));

        verify(retainedMsgProcessor, never()).process(any());
    }

    @Test
    void givenMatchingSubscriptions_whenQueueAcks_thenSuccessReasonCodeAndAcceptedCounted() throws Exception {
        when(throughputQuotaService.tryConsumeIncoming()).thenReturn(true);
        when(subscriptionService.getSubscriptions(TOPIC))
                .thenReturn(List.of(new ValueWithTopicFilter<>(mock(EntitySubscription.class), "devices/+/commands")));

        ListenableFuture<RestPublishResponse> future = service.publish(request("hello", PayloadEncoding.TEXT));
        assertThat(future.isDone()).isFalse();
        capturedQueueCallback().onSuccess(mock(TbQueueMsgMetadata.class));

        assertThat(future.get().getReasonCode()).isEqualTo(MqttReasonCodes.PubAck.SUCCESS.byteValue());
        verify(restPublishStats).increment(RestPublishOutcome.ACCEPTED);
        verify(tbMessageStatsReportClient, never()).reportDroppedMsgs();
    }

    @Test
    void givenNoMatchingSubscriptions_whenQueueAcks_thenNoMatchingSubscribersReasonCode() throws Exception {
        when(throughputQuotaService.tryConsumeIncoming()).thenReturn(true);
        when(subscriptionService.getSubscriptions(TOPIC)).thenReturn(List.of());

        ListenableFuture<RestPublishResponse> future = service.publish(request("hello", PayloadEncoding.TEXT));
        capturedQueueCallback().onSuccess(mock(TbQueueMsgMetadata.class));

        assertThat(future.get().getReasonCode()).isEqualTo(MqttReasonCodes.PubAck.NO_MATCHING_SUBSCRIBERS.byteValue());
        verify(restPublishStats).increment(RestPublishOutcome.ACCEPTED);
    }

    @Test
    void givenQueueFailure_whenPublish_thenFutureFailsWithCauseAndDropCounted() {
        when(throughputQuotaService.tryConsumeIncoming()).thenReturn(true);
        RuntimeException failure = new RuntimeException("queue unavailable");

        ListenableFuture<RestPublishResponse> future = service.publish(request("hello", PayloadEncoding.TEXT));
        capturedQueueCallback().onFailure(failure);

        assertThatThrownBy(future::get).isInstanceOf(ExecutionException.class).hasCause(failure);
        verify(tbMessageStatsReportClient).reportDroppedMsgs();
        verify(restPublishStats).increment(RestPublishOutcome.FAILED);
    }

    private RestPublishRequest request(String payload, PayloadEncoding encoding) {
        RestPublishRequest request = new RestPublishRequest();
        request.setTopic(TOPIC);
        request.setPayload(new TextNode(payload));
        request.setPayloadEncoding(encoding);
        request.setQos(1);
        return request;
    }

    private PublishMsg capturedPublishMsg() {
        ArgumentCaptor<PublishMsg> captor = ArgumentCaptor.forClass(PublishMsg.class);
        verify(msgDispatcherService).persistPublishMsg(eq(BrokerConstants.REST_API_CLIENT_ID), captor.capture(), any());
        return captor.getValue();
    }

    private TbQueueCallback capturedQueueCallback() {
        ArgumentCaptor<TbQueueCallback> captor = ArgumentCaptor.forClass(TbQueueCallback.class);
        verify(msgDispatcherService).persistPublishMsg(anyString(), any(), captor.capture());
        return captor.getValue();
    }

    private static int intProp(MqttProperties props, int id) {
        return ((MqttProperties.IntegerProperty) props.getProperty(id)).value();
    }

    private static String stringProp(MqttProperties props, int id) {
        return ((MqttProperties.StringProperty) props.getProperty(id)).value();
    }

}
