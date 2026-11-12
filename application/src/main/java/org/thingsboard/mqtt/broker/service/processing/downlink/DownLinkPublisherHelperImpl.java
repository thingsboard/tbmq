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
package org.thingsboard.mqtt.broker.service.processing.downlink;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.edition.ClusterComponent;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DOT;

@Service
@ClusterComponent
public class DownLinkPublisherHelperImpl implements DownLinkPublisherHelper {

    @Value("${queue.kafka.basic-downlink-msg.topic-prefix}")
    private String basicDownLinkTopicPrefixProperty;
    @Value("${queue.kafka.persisted-downlink-msg.topic-prefix}")
    private String persistentDownLinkTopicPrefixProperty;

    @Value("${queue.kafka.kafka-prefix:}")
    private String kafkaPrefix;

    private String basicDownLinkTopicPrefix;
    private String persistentDownLinkTopicPrefix;

    @PostConstruct
    public void init() {
        basicDownLinkTopicPrefix = kafkaPrefix + basicDownLinkTopicPrefixProperty + DOT;
        persistentDownLinkTopicPrefix = kafkaPrefix + persistentDownLinkTopicPrefixProperty + DOT;
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
