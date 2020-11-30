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

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Component
public class TbKafkaTopicConfigs {
    @Value("${queue.kafka.topic-properties.client-session}")
    private String clientSessionProperties;
    @Value("${queue.kafka.topic-properties.publish-msg}")
    private String publishMsgProperties;
    @Value("${queue.kafka.topic-properties.successful-msg-offset}")
    private String successfulMsgOffsetProperties;
    @Value("${queue.kafka.topic-properties.retain-msg}")
    private String retainMsgProperties;

    @Getter
    private Map<String, String> clientSessionConfigs;
    @Getter
    private Map<String, String> publishMsgConfigs;
    @Getter
    private Map<String, String> successfulMsgOffsetConfigs;
    @Getter
    private Map<String, String> retainMsgConfigs;

    @PostConstruct
    private void init() {
        clientSessionConfigs = getConfigs(clientSessionProperties);
        publishMsgConfigs = getConfigs(publishMsgProperties);
        successfulMsgOffsetConfigs = getConfigs(successfulMsgOffsetProperties);
        retainMsgConfigs = getConfigs(retainMsgProperties);
    }

    private Map<String, String> getConfigs(String properties) {
        Map<String, String> configs = new HashMap<>();
        for (String property : properties.split(";")) {
            int delimiterPosition = property.indexOf(":");
            String key = property.substring(0, delimiterPosition);
            String value = property.substring(delimiterPosition + 1);
            configs.put(key, value);
        }
        return configs;
    }
}
