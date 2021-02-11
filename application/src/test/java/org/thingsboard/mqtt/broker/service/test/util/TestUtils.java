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
package org.thingsboard.mqtt.broker.service.test.util;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.service.mqtt.TopicSubscription;

import java.util.Collection;

public class TestUtils {
    public static void clearPersistedClient(MqttClient persistedClient, MqttClient newClientToClear) throws Exception {
        if (persistedClient.isConnected()) {
            persistedClient.disconnect();
            persistedClient.close();
        }
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(true);
        newClientToClear.connect(connectOptions);
        newClientToClear.disconnect();
        newClientToClear.close();
    }

    public static String[] getTopicNames(Collection<TopicSubscription> topicSubscriptions) {
        return topicSubscriptions.stream().map(TopicSubscription::getTopic).toArray(String[]::new);
    }

    public static int[] getQoSLevels(Collection<TopicSubscription> topicSubscriptions) {
        return topicSubscriptions.stream().map(TopicSubscription::getQos).mapToInt(x -> x).toArray();
    }

    public static org.thingsboard.mqtt.broker.common.data.MqttClient createApplicationClient() {
        org.thingsboard.mqtt.broker.common.data.MqttClient appClient = new org.thingsboard.mqtt.broker.common.data.MqttClient();
        appClient.setName("test-application-client");
        appClient.setClientId("test-application-client");
        appClient.setType(ClientType.APPLICATION);
        return appClient;
    }
}
