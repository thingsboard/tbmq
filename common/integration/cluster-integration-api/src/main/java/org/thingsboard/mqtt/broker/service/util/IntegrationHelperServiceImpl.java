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
package org.thingsboard.mqtt.broker.service.util;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class IntegrationHelperServiceImpl implements IntegrationHelperService {

    @Value("${queue.kafka.kafka-prefix:}")
    private String kafkaPrefix;

    @Override
    public String getIntegrationTopic(String integrationId) {
        return kafkaPrefix + IntegrationUtil.getIntegrationTopic(integrationId);
    }

    @Override
    public String getIntegrationConsumerGroup(String integrationId) {
        return kafkaPrefix + IntegrationUtil.getIntegrationConsumerGroup(integrationId);
    }
}
