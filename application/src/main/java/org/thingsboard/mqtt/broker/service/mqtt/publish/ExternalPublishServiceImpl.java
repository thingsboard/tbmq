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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.MqttQoS;
import org.thingsboard.mqtt.broker.dao.topic.TopicValidationService;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.limits.ThroughputQuotaService;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgProcessor;
import org.thingsboard.mqtt.broker.service.processing.MsgDispatcherService;

@Service
@RequiredArgsConstructor
public class ExternalPublishServiceImpl implements ExternalPublishService {

    static final String REST_PUBLISHER_ID = "tbmq-rest-api";

    private final TopicValidationService topicValidationService;
    private final ThroughputQuotaService throughputQuotaService;
    private final RetainedMsgProcessor retainedMsgProcessor;
    private final MsgDispatcherService msgDispatcherService;
    private final TbMessageStatsReportClient tbMessageStatsReportClient;

    @Value("${mqtt.rest.publish.max-payload-size:65536}")
    int maxPayloadSize;

    @Override
    public void publish(ExternalPublishCommand command, TbQueueCallback callback) {
        validate(command);
        if (!throughputQuotaService.tryConsumeIncoming()) {
            tbMessageStatsReportClient.reportDroppedMsgs();
            callback.onFailure(new ExternalPublishRateLimitException());
            return;
        }

        PublishMsg publishMsg = toPublishMsg(command);
        if (publishMsg.isRetained()) {
            publishMsg = retainedMsgProcessor.process(publishMsg);
        }

        msgDispatcherService.persistExternalPublishMsg(REST_PUBLISHER_ID, publishMsg, new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                callback.onSuccess(metadata);
            }

            @Override
            public void onFailure(Throwable t) {
                tbMessageStatsReportClient.reportDroppedMsgs();
                callback.onFailure(t);
            }
        });
    }

    private void validate(ExternalPublishCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Publish command cannot be null");
        }
        topicValidationService.validateTopic(command.topic());
        MqttQoS.valueOf(command.qos());
        if (command.payload() == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }
        if (command.payload().length > maxPayloadSize) {
            throw new IllegalArgumentException("Payload size exceeds the maximum of " + maxPayloadSize + " bytes");
        }
        if (command.messageExpiryInterval() != null && command.messageExpiryInterval() < 0) {
            throw new IllegalArgumentException("Message expiry interval cannot be negative");
        }
    }

    private PublishMsg toPublishMsg(ExternalPublishCommand command) {
        MqttProperties properties = new MqttProperties();
        if (command.messageExpiryInterval() != null) {
            properties.add(new MqttProperties.IntegerProperty(
                    BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID, command.messageExpiryInterval()));
        }
        if (command.contentType() != null && !command.contentType().isBlank()) {
            properties.add(new MqttProperties.StringProperty(BrokerConstants.CONTENT_TYPE_PROP_ID, command.contentType()));
        }
        return PublishMsg.builder()
                .packetId(0)
                .topicName(command.topic())
                .payload(command.payload())
                .qos(command.qos())
                .isRetained(command.retained())
                .isDup(false)
                .properties(properties)
                .build();
    }

}
