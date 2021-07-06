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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.util;

public class MqttApplicationClientUtil {
    private static final String TOPIC_PREFIX = "mqtt_broker_application_client_";
    private static final String CONSUMER_GROUP_PREFIX = "application-persisted-msg-group-";

    public static String getTopic(String clientId) {
        return TOPIC_PREFIX + clientId;
    }

    public static String getConsumerGroup(String clientId) {
        return CONSUMER_GROUP_PREFIX + clientId;
    }
}