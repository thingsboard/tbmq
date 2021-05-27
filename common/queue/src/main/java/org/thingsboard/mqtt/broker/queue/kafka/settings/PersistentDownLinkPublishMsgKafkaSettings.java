/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.queue.kafka.settings;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class PersistentDownLinkPublishMsgKafkaSettings {
    @Value("${queue.kafka.persistent-downlink-publish-msg.topic-prefix}")
    private String topicPrefix;
    @Value("${queue.kafka.persistent-downlink-publish-msg.topic-properties}")
    private String topicProperties;
    @Value("${queue.kafka.persistent-downlink-publish-msg.producer}")
    private String producerProperties;
    @Value("${queue.kafka.persistent-downlink-publish-msg.consumer}")
    private String consumerProperties;
}
