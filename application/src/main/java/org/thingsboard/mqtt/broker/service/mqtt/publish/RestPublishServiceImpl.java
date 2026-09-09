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

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.handler.codec.mqtt.MqttProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionService;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
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
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Mirrors the tail of {@link org.thingsboard.mqtt.broker.actors.client.service.handlers.MqttPublishHandler#process}
 * for a publisher without a client session: quota charge, retained processing, then the publish queue. Keep the two
 * in step when that ordering changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestPublishServiceImpl implements RestPublishService {

    private final TopicValidationService topicValidationService;
    private final ThroughputQuotaService throughputQuotaService;
    private final RetainedMsgProcessor retainedMsgProcessor;
    private final SubscriptionService subscriptionService;
    private final MsgDispatcherService msgDispatcherService;
    private final TbMessageStatsReportClient tbMessageStatsReportClient;
    private final StatsManager statsManager;

    @Value("${server.rest_publish.max_payload_size:65536}")
    int maxPayloadSize;

    private RestPublishStats stats;

    @PostConstruct
    public void init() {
        stats = statsManager.getRestPublishStats();
    }

    @Override
    public ListenableFuture<RestPublishResponse> publish(RestPublishRequest request) {
        PublishMsg publishMsg = toPublishMsg(request);

        if (!throughputQuotaService.tryConsumeIncoming()) {
            log.warn("[{}] REST publish refused by the total throughput quota, payload size: {}", publishMsg.getTopicName(), publishMsg.getPayload().length);
            tbMessageStatsReportClient.reportDroppedMsgs();
            stats.increment(RestPublishOutcome.QUOTA_EXCEEDED);
            throw new TbRateLimitsException("Total message rate limit exceeded");
        }

        if (publishMsg.isRetained()) {
            publishMsg = retainedMsgProcessor.process(publishMsg);
        }

        boolean hasSubscribers = !subscriptionService.getSubscriptions(publishMsg.getTopicName()).isEmpty();
        RestPublishResponse response = hasSubscribers ? RestPublishResponse.success() : RestPublishResponse.noMatchingSubscribers();

        SettableFuture<RestPublishResponse> future = SettableFuture.create();
        PublishMsg msgToLog = publishMsg;
        msgDispatcherService.persistPublishMsg(BrokerConstants.REST_API_CLIENT_ID, publishMsg, new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                stats.increment(RestPublishOutcome.ACCEPTED);
                future.set(response);
            }

            @Override
            public void onFailure(Throwable t) {
                log.warn("[{}] Failed to persist REST publish to the queue, payload size: {}", msgToLog.getTopicName(), msgToLog.getPayload().length, t);
                tbMessageStatsReportClient.reportDroppedMsgs();
                stats.increment(RestPublishOutcome.FAILED);
                future.setException(t);
            }
        });
        return future;
    }

    private PublishMsg toPublishMsg(RestPublishRequest request) {
        topicValidationService.validateTopic(request.getTopic());
        byte[] payload = decodePayload(request);
        if (payload.length > maxPayloadSize) {
            throw new DataValidationException("Payload size " + payload.length + " exceeds the maximum of " + maxPayloadSize + " bytes");
        }
        return PublishMsg.builder()
                .packetId(0)
                .topicName(request.getTopic())
                .payload(payload)
                .qos(request.getQos())
                .isRetained(request.isRetain())
                .isDup(false)
                .properties(toMqttProperties(request.getProperties()))
                .build();
    }

    private byte[] decodePayload(RestPublishRequest request) {
        JsonNode payload = request.getPayload();
        if (payload == null || payload.isNull()) {
            throw new DataValidationException("Payload is required");
        }
        boolean base64 = request.getPayloadEncoding() == PayloadEncoding.BASE64;
        if (!payload.isTextual()) {
            if (base64) {
                throw new DataValidationException("BASE64 payload encoding requires the payload to be a string");
            }
            // an object, array, number or boolean is published as its compact JSON text
            return payload.toString().getBytes(StandardCharsets.UTF_8);
        }
        return base64 ? decodeBase64(payload.textValue(), "Payload") : payload.textValue().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] decodeBase64(String value, String field) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new DataValidationException(field + " is not valid Base64: " + e.getMessage());
        }
    }

    private MqttProperties toMqttProperties(RestPublishProperties props) {
        MqttProperties properties = new MqttProperties();
        if (props == null) {
            return properties;
        }
        if (props.getPayloadFormatIndicator() != null) {
            MqttPropertiesUtil.addPayloadFormatIndicatorToProps(properties, props.getPayloadFormatIndicator());
        }
        if (props.getMessageExpiryInterval() != null) {
            MqttPropertiesUtil.addMsgExpiryIntervalToProps(properties, props.getMessageExpiryInterval());
        }
        if (StringUtils.isNotEmpty(props.getContentType())) {
            MqttPropertiesUtil.addContentTypeToProps(properties, props.getContentType());
        }
        if (StringUtils.isNotEmpty(props.getResponseTopic())) {
            topicValidationService.validateTopic(props.getResponseTopic());
            MqttPropertiesUtil.addResponseTopicToProps(properties, props.getResponseTopic());
        }
        if (StringUtils.isNotEmpty(props.getCorrelationData())) {
            MqttPropertiesUtil.addCorrelationDataToProps(properties, decodeBase64(props.getCorrelationData(), "Correlation data"));
        }
        if (props.getUserProperties() != null && !props.getUserProperties().isEmpty()) {
            MqttProperties.UserProperties userProperties = new MqttProperties.UserProperties();
            props.getUserProperties().forEach(userProperties::add);
            properties.add(userProperties);
        }
        return properties;
    }

}
