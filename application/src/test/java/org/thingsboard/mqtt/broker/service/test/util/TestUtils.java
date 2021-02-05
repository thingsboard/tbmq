package org.thingsboard.mqtt.broker.service.test.util;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.service.mqtt.TopicSubscription;

import java.util.Collection;

public class TestUtils {
    public static void clearPersistedClient(MqttClient persistedClient) throws Exception {
        if (persistedClient.isConnected()) {
            persistedClient.disconnect();
        }
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(true);
        persistedClient.connect(connectOptions);
        persistedClient.disconnect();
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
