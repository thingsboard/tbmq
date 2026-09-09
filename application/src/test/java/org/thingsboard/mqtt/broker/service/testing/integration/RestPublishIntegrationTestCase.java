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
package org.thingsboard.mqtt.broker.service.testing.integration;

import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.controller.AbstractControllerTest;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dto.PayloadEncoding;
import org.thingsboard.mqtt.broker.dto.RestPublishProperties;
import org.thingsboard.mqtt.broker.dto.RestPublishRequest;
import org.thingsboard.mqtt.broker.dto.RestPublishResponse;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives {@code POST /api/mqtt/publish} through the real broker: quota, retained store, Kafka publish queue,
 * subscription matching and delivery to a Paho MQTT 5 subscriber.
 */
@Slf4j
@DaoSqlTest
public class RestPublishIntegrationTestCase extends AbstractControllerTest {

    private static final String PUBLISH_URL = "/api/mqtt/publish";

    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();
    }

    @Test
    public void givenSubscribedClient_whenRestPublishWithMqtt5Properties_thenDeliveredWithPropertiesAndOk() throws Throwable {
        String topic = uniqueTopic();
        AtomicReference<MqttMessage> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        MqttClient subClient = subscribe(topic, 1, (t, msg) -> {
            received.set(msg);
            latch.countDown();
        });

        RestPublishRequest request = request(topic, "ignored", PayloadEncoding.PLAIN);
        // a JSON object payload is published as its compact JSON text
        request.setPayload(JacksonUtil.toJsonNode("{\"cmd\": \"reboot\"}"));
        request.setQos(1);
        RestPublishProperties properties = new RestPublishProperties();
        properties.setPayloadFormatIndicator(1);
        properties.setMessageExpiryInterval(600);
        properties.setContentType("application/json");
        properties.setResponseTopic(topic + "/replies");
        properties.setCorrelationData(Base64.getEncoder().encodeToString("req-42".getBytes(StandardCharsets.UTF_8)));
        properties.setUserProperties(Map.of("source", "backend"));
        request.setProperties(properties);

        RestPublishResponse response = publish(request, 200);
        assertThat(response.getReasonCode()).isZero();

        assertThat(latch.await(5, TimeUnit.SECONDS)).as("message delivered to the MQTT subscriber").isTrue();
        MqttMessage msg = received.get();
        assertThat(new String(msg.getPayload(), StandardCharsets.UTF_8)).isEqualTo("{\"cmd\":\"reboot\"}");
        assertThat(msg.getQos()).isEqualTo(1);
        assertThat(msg.isRetained()).isFalse();
        assertThat(msg.getProperties().getPayloadFormat()).isTrue();
        assertThat(msg.getProperties().getContentType()).isEqualTo("application/json");
        assertThat(msg.getProperties().getResponseTopic()).isEqualTo(topic + "/replies");
        assertThat(msg.getProperties().getCorrelationData()).isEqualTo("req-42".getBytes(StandardCharsets.UTF_8));
        // message expiry travels in the queue headers and is only re-attached on persisted/retained delivery
        // (MqttPropertiesProto has no expiry field), so live delivery to an online subscriber does not carry it -
        // the retained case below asserts it
        assertThat(msg.getProperties().getUserProperties()).extracting(UserProperty::getKey, UserProperty::getValue)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("source", "backend"));

        TestUtils.disconnectAndCloseClient(subClient);
    }

    @Test
    public void givenSubscribedClient_whenRestPublishBase64Payload_thenBinaryDeliveredByteExact() throws Throwable {
        String topic = uniqueTopic();
        byte[] binary = {0, 1, 2, (byte) 0xFE, (byte) 0xFF};
        AtomicReference<MqttMessage> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        MqttClient subClient = subscribe(topic, 0, (t, msg) -> {
            received.set(msg);
            latch.countDown();
        });

        publish(request(topic, Base64.getEncoder().encodeToString(binary), PayloadEncoding.BASE64), 200);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get().getPayload()).isEqualTo(binary);

        TestUtils.disconnectAndCloseClient(subClient);
    }

    @Test
    public void givenNoSubscribers_whenRestPublish_thenAcceptedWithNoMatchingSubscribersReasonCode() throws Throwable {
        RestPublishResponse response = publish(request(uniqueTopic(), "nobody home", PayloadEncoding.PLAIN), 202);

        assertThat(response.getReasonCode()).isEqualTo(16);
    }

    @Test
    public void givenRetainedRestPublish_whenClientSubscribesLater_thenReceivesRetainedMessage() throws Throwable {
        String topic = uniqueTopic();
        RestPublishRequest request = request(topic, "online", PayloadEncoding.PLAIN);
        request.setRetain(true);
        RestPublishProperties properties = new RestPublishProperties();
        properties.setMessageExpiryInterval(600);
        request.setProperties(properties);
        // nobody is subscribed yet, so the queue accepts it with reason code 16 but the retained store keeps it
        publish(request, 202);

        AtomicReference<MqttMessage> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        MqttClient subClient = subscribe(topic, 1, (t, msg) -> {
            received.set(msg);
            latch.countDown();
        });

        assertThat(latch.await(5, TimeUnit.SECONDS)).as("retained message delivered on subscribe").isTrue();
        assertThat(received.get().isRetained()).isTrue();
        assertThat(new String(received.get().getPayload(), StandardCharsets.UTF_8)).isEqualTo("online");
        assertThat(received.get().getProperties().getMessageExpiryInterval()).as("remaining expiry re-attached on retained delivery")
                .isNotNull().isPositive().isLessThanOrEqualTo(600L);

        TestUtils.disconnectAndCloseClient(subClient);
    }

    @Test
    public void givenRetainedMessage_whenRestPublishEmptyRetainedPayload_thenRetainedMessageCleared() throws Throwable {
        String topic = uniqueTopic();
        RestPublishRequest store = request(topic, "online", PayloadEncoding.PLAIN);
        store.setRetain(true);
        publish(store, 202);

        RestPublishRequest clear = request(topic, "", PayloadEncoding.PLAIN);
        clear.setRetain(true);
        publish(clear, 202);

        CountDownLatch latch = new CountDownLatch(1);
        MqttClient subClient = subscribe(topic, 1, (t, msg) -> latch.countDown());

        assertThat(latch.await(2, TimeUnit.SECONDS)).as("no retained message must arrive after it was cleared").isFalse();

        TestUtils.disconnectAndCloseClient(subClient);
    }

    @Test
    public void givenWildcardTopic_whenRestPublish_thenBadRequest() throws Exception {
        doPost(PUBLISH_URL, request("devices/+/commands", "x", PayloadEncoding.PLAIN)).andExpect(status().isBadRequest());
    }

    private MqttClient subscribe(String topic, int qos, IMqttMessageListener listener) throws Exception {
        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, RandomStringUtils.randomAlphabetic(10));
        subClient.connect();
        subClient.subscribe(new MqttSubscription[]{new MqttSubscription(topic, qos)}, new IMqttMessageListener[]{listener});
        return subClient;
    }

    private RestPublishResponse publish(RestPublishRequest request, int expectedStatus) throws Exception {
        return readResponse(doPostAsync(PUBLISH_URL, request, -1L).andExpect(status().is(expectedStatus)), RestPublishResponse.class);
    }

    private static RestPublishRequest request(String topic, String payload, PayloadEncoding encoding) {
        RestPublishRequest request = new RestPublishRequest();
        request.setTopic(topic);
        request.setPayload(new TextNode(payload));
        request.setPayloadEncoding(encoding);
        return request;
    }

    private static String uniqueTopic() {
        return "rest/publish/" + RandomStringUtils.randomAlphabetic(8);
    }

}
