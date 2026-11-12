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
package org.thingsboard.mqtt.broker.service.notification;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.edition.ClusterComponent;
import org.thingsboard.mqtt.broker.service.system.SystemInfoService;

import java.util.List;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DOT;

@Service
@ClusterComponent
@RequiredArgsConstructor
public class InternodeNotificationsHelperImpl implements InternodeNotificationsHelper {

    private final SystemInfoService systemInfoService;

    @Value("${queue.kafka.internode-notifications.topic-prefix}")
    private String internodeNotificationsTopicPrefix;

    @Value("${queue.kafka.kafka-prefix:}")
    private String kafkaPrefix;

    private String topicPrefix;

    @PostConstruct
    public void init() {
        topicPrefix = kafkaPrefix + internodeNotificationsTopicPrefix + DOT;
    }

    @Override
    public String getServiceTopic(String serviceId) {
        return topicPrefix + serviceId;
    }

    @Override
    public List<String> getServiceIds() {
        return systemInfoService.getTbmqServiceIds();
    }

}
