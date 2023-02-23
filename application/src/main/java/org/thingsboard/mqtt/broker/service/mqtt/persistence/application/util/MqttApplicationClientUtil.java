/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.util;

import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;

public class MqttApplicationClientUtil {

    private static final String SINGLE_LVL_WILDCARD_ABBREV = "slw";
    private static final String MULTI_LVL_WILDCARD_ABBREV = "mlw";
    private static final String TOPIC_PREFIX = "mqtt_broker_application_client_";
    private static final String CONSUMER_GROUP_PREFIX = "application-persisted-msg-group-";
    private static final String SHARED_CONSUMER_GROUP_PREFIX = "application-shared-msg-group-";

    public static String getTopic(String clientId) {
        return TOPIC_PREFIX + clientId;
    }

    public static String getConsumerGroup(String clientId) {
        return CONSUMER_GROUP_PREFIX + clientId;
    }

    // Note, replacing single and multi levels mqtt wildcards since Kafka does not support such chars for topic name
    public static String getKafkaTopic(String topic) {
        return topic
                .replaceAll("/", ".")
                .replaceAll("\\+", SINGLE_LVL_WILDCARD_ABBREV)
                .replaceAll("#", MULTI_LVL_WILDCARD_ABBREV);
    }

    public static String getConsumerGroup(TopicSharedSubscription subscription) {
        return SHARED_CONSUMER_GROUP_PREFIX + subscription.getShareName() + "-" + getKafkaTopic(subscription.getTopic());
    }
}
