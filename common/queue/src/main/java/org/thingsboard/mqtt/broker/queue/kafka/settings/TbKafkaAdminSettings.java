/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
import lombok.Setter;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.queue.kafka.settings.common.TbKafkaCommonSettings;
import org.thingsboard.mqtt.broker.queue.util.QueueUtil;

import java.util.Properties;

@Setter
@Getter
@Component
public class TbKafkaAdminSettings {

    private final TbKafkaCommonSettings commonSettings;

    @Value("${queue.kafka.bootstrap.servers}")
    private String servers;

    @Value("${queue.kafka.enable-topic-deletion:true}")
    private boolean enableTopicDeletion;

    @Value("${queue.kafka.admin.config:#{null}}")
    private String config;

    @Value("${queue.kafka.admin.command-timeout:30}")
    private int kafkaAdminCommandTimeout;

    @Value("${queue.kafka.admin.topics-cache-ttl-ms:300000}")
    private int topicsCacheTtlMs;

    @Value("${queue.kafka.kafka-prefix:}")
    private String kafkaPrefix;

    @Autowired
    public TbKafkaAdminSettings(TbKafkaCommonSettings commonSettings) {
        this.commonSettings = commonSettings;
    }

    public Properties toProps() {
        Properties props = new Properties();

        props.putAll(QueueUtil.getConfigs(commonSettings.getCommonConfig()));
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, servers);

        if (config != null) {
            props.putAll(QueueUtil.getConfigs(config));
        }
        return props;
    }
}
