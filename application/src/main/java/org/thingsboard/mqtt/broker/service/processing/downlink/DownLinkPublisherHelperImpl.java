/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.processing.downlink;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.queue.kafka.settings.BasicDownLinkPublishMsgKafkaSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.PersistentDownLinkPublishMsgKafkaSettings;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DOT;

@Service
@RequiredArgsConstructor
public class DownLinkPublisherHelperImpl implements DownLinkPublisherHelper {

    private final BasicDownLinkPublishMsgKafkaSettings basicDownLinkPublishMsgKafkaSettings;
    private final PersistentDownLinkPublishMsgKafkaSettings persistentDownLinkPublishMsgKafkaSettings;

    @Value("${queue.kafka.kafka-prefix:}")
    private String kafkaPrefix;

    private String basicDownLinkTopicPrefix;
    private String persistentDownLinkTopicPrefix;

    @PostConstruct
    public void init() {
        basicDownLinkTopicPrefix = kafkaPrefix + basicDownLinkPublishMsgKafkaSettings.getTopicPrefix() + DOT;
        persistentDownLinkTopicPrefix = kafkaPrefix + persistentDownLinkPublishMsgKafkaSettings.getTopicPrefix() + DOT;
    }

    @Override
    public String getBasicDownLinkServiceTopic(String serviceId) {
        return basicDownLinkTopicPrefix + serviceId;
    }

    @Override
    public String getPersistentDownLinkServiceTopic(String serviceId) {
        return persistentDownLinkTopicPrefix + serviceId;
    }
}
