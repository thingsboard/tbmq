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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@ConfigurationProperties(prefix = "queue.kafka.application-publish-ctx")
@Component("application-publish-ctx")
@AllArgsConstructor
@NoArgsConstructor
public class ApplicationPublishCtxKafkaSettings extends TbAbstractKafkaSettings {
    @Getter
    @Setter
    private String topic;

    @Setter
    private KafkaProducerSettings producer;
    @Setter
    private KafkaConsumerSettings consumer;

    @Getter
    @Setter
    private List<TbKafkaProperty> other;

    @Override
    KafkaProducerSettings getProducerSettings() {
        return producer;
    }

    @Override
    KafkaConsumerSettings getConsumerSettings() {
        return consumer;
    }
}
