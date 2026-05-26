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
package org.thingsboard.mqtt.broker.service.mqtt.sparkplug;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgProcessor;
import org.thingsboard.mqtt.broker.service.processing.MsgDispatcherService;

@Service
@RequiredArgsConstructor
@Slf4j
public class SparkplugCertificateRepublisherImpl implements SparkplugCertificateRepublisher {

    private final MsgDispatcherService msgDispatcherService;
    private final RetainedMsgProcessor retainedMsgProcessor;

    @Override
    public void maybeRepublish(SessionInfo sessionInfo, PublishMsg publishMsg, String clientCertCn) {
        String certTopic = SparkplugTopicUtil.toCertificateTopic(publishMsg.getTopicName());
        if (certTopic == null) {
            return;
        }
        try {
            PublishMsg republished = publishMsg.toBuilder()
                    .topicName(certTopic)
                    .isRetained(true)
                    .build();
            // Store as retained first (same order as MqttPublishHandler.process for client-originated
            // retained publishes), then dispatch through the standard Kafka pipeline so live subscribers
            // receive it. Without the retained-store step the fresh-subscribe path would see nothing.
            republished = retainedMsgProcessor.process(republished);
            msgDispatcherService.persistPublishMsg(sessionInfo, republished, clientCertCn, new TbQueueCallback() {
                @Override
                public void onSuccess(TbQueueMsgMetadata metadata) {
                    if (log.isTraceEnabled()) {
                        log.trace("Republished Sparkplug certificate on topic {}", certTopic);
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    log.warn("Failed to republish Sparkplug certificate on topic {}", certTopic, t);
                }
            });
        } catch (Exception e) {
            log.warn("Failed to republish Sparkplug certificate on topic {}", certTopic, e);
        }
    }
}
