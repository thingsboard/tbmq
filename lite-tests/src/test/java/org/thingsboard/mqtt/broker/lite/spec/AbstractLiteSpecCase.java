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
package org.thingsboard.mqtt.broker.lite.spec;

import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.After;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The base of the conformance cases: raw MQTT clients that the test drives packet by packet, closed after each test,
 * and the session state the broker keeps for them. Each test uses client ids and topics of its own.
 */
public abstract class AbstractLiteSpecCase extends AbstractPubSubIntegrationTest {

    static final Duration TIMEOUT = Duration.ofSeconds(10);
    static final int SUCCESS = 0x00;

    @Autowired
    protected ClientSessionCache clientSessionCache;

    private final List<RawMqttClient> clients = new ArrayList<>();

    @After
    public void closeClients() {
        clients.forEach(RawMqttClient::close);
        clients.clear();
    }

    /**
     * An MQTT 5 client whose session outlives its connections, subscribed to {@code topicFilter}.
     */
    RawMqttClient persistentSubscriber(String clientId, String topicFilter, int qos) throws IOException {
        RawMqttClient client = connectPersistent(clientId);
        client.subscribe(topicFilter, qos);
        return client;
    }

    /**
     * Connects an MQTT 5 client with the session it had, if any.
     */
    RawMqttClient connectPersistent(String clientId) throws IOException {
        return track(RawMqttClient.connectPersistent(mqttPort, clientId, MqttVersion.MQTT_5));
    }

    /**
     * Connects an MQTT 5 client with the session it had, if any, that expires {@code sessionExpiryIntervalSec} seconds
     * after the connection closes.
     */
    RawMqttClient connectPersistent(String clientId, int sessionExpiryIntervalSec) throws IOException {
        return track(RawMqttClient.connectPersistent(mqttPort, clientId, sessionExpiryIntervalSec));
    }

    RawMqttClient connectClean(String clientId, MqttVersion version) throws IOException {
        return track(RawMqttClient.connectClean(mqttPort, clientId, version));
    }

    /**
     * Disconnects the client and waits until the broker has the session as disconnected, so that what is published
     * next is published to an offline subscriber.
     */
    void disconnect(RawMqttClient client, String clientId) throws IOException {
        client.disconnect();
        await("session of " + clientId + " disconnected").atMost(TIMEOUT).until(() -> {
            ClientSessionInfo session = clientSessionCache.getClientSessionInfo(clientId);
            return session != null && !session.isConnected();
        });
    }

    /**
     * @return the next packet, which must be a PUBLISH
     */
    static RawMqttClient.Packet nextPublish(RawMqttClient client) throws IOException {
        RawMqttClient.Packet packet = client.next(TIMEOUT);
        assertThat(packet.type()).as("packet type of %s", packet).isEqualTo(MqttMessageType.PUBLISH);
        return packet;
    }

    /**
     * A precondition of every resend check: a new connection numbers its packets from 1, and an empty queue numbers its
     * entries from 1 too, so an original packet id of 1 could not tell a resend with the original id from one that was
     * numbered again.
     */
    static void assertOriginalPacketIdIsNotTheFirst(RawMqttClient.Packet sent) {
        assertThat(sent.packetId()).as("original packet id, which must not be the first id of a connection").isNotEqualTo(1);
    }

    private RawMqttClient track(RawMqttClient client) {
        clients.add(client);
        return client;
    }

}
