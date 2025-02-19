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

public class IntegrationUtil {

    private static final String IE_MSG_TOPIC_PREFIX = "tbmq.msg.ie.";
    private static final String IE_CONSUMER_GROUP_PREFIX = "ie-msg-consumer-group-";

    public static String getIntegrationTopic(String integrationId) {
        return IE_MSG_TOPIC_PREFIX + integrationId;
    }

    public static String getIntegrationConsumerGroup(String integrationId) {
        return IE_CONSUMER_GROUP_PREFIX + integrationId;
    }

}
