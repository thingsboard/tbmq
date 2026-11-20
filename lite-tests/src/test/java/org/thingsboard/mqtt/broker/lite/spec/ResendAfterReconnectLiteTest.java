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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgQueueStore;
import org.thingsboard.mqtt.broker.service.test.util.RestartingSpringJUnit4ClassRunner;
import org.thingsboard.mqtt.broker.service.test.util.SpringRestarter;
import org.thingsboard.mqtt.broker.testsupport.EditionTest;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A message sent to a persistent DEVICE subscriber and not acknowledged is resent when the subscriber reconnects, with
 * its original packet id and DUP=1 ([MQTT-4.4.0-1], [MQTT-3.3.1-1]), even when its Message Expiry Interval passed while
 * the subscriber was away: the interval applies only until the message is sent ([MQTT-4.3.3-7]), PUBREL entries
 * included (the card #652 rule in Lite). The resent PUBLISH carries the interval left, 0 ([MQTT-3.3.2-6]). The
 * subscriber is a raw client that withholds its PUBACK, PUBREC or PUBCOMP.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = ResendAfterReconnectLiteTest.class, loader = SpringBootContextLoader.class)
@EditionTest
@RunWith(RestartingSpringJUnit4ClassRunner.class)
public class ResendAfterReconnectLiteTest extends AbstractLiteSpecCase {

    // long enough to outlast the first delivery to the online subscriber on a loaded machine
    private static final int MSG_EXPIRY_INTERVAL_SEC = 2;
    // the subscriber stays away for longer than the interval
    private static final Duration AWAY = Duration.ofSeconds(MSG_EXPIRY_INTERVAL_SEC + 1);

    @Autowired
    private DeviceMsgQueueStore deviceMsgQueueStore;

    @Test
    public void mqtt_4_4_0_1_and_3_3_1_1_anUnacknowledgedQos1MessageIsResentWithDupAndTheSamePacketIdAfterItsIntervalPassed() throws Exception {
        String topic = "spec/resend/qos1";
        String subscriberId = "resend-qos1-sub";
        RawMqttClient subscriber = persistentSubscriber(subscriberId, topic, 1);
        RawMqttClient publisher = connectClean("resend-qos1-pub", MqttVersion.MQTT_5);
        deliverAndAcknowledgeOne(publisher, subscriber, subscriberId, topic, 1);
        assertThat(publisher.publishQos1(topic, 2, "qos1", MSG_EXPIRY_INTERVAL_SEC)).isEqualTo(SUCCESS);

        RawMqttClient.Packet sent = nextPublish(subscriber);
        assertThat(sent.dup()).isFalse();
        assertOriginalPacketIdIsNotTheFirst(sent);
        // no PUBACK
        disconnect(subscriber, subscriberId);
        Thread.sleep(AWAY);

        RawMqttClient reconnected = connectPersistent(subscriberId);
        RawMqttClient.Packet resent = nextPublish(reconnected);
        assertResentAfterItsIntervalPassed(resent, sent, 1);

        reconnected.pubAck(resent.packetId());
        awaitQueueEmpty(subscriberId);
    }

    @Test
    public void mqtt_4_3_3_7_anUnacknowledgedQos2MessageIsResentAfterItsIntervalPassed() throws Exception {
        String topic = "spec/resend/qos2";
        String subscriberId = "resend-qos2-sub";
        RawMqttClient subscriber = persistentSubscriber(subscriberId, topic, 2);
        RawMqttClient publisher = connectClean("resend-qos2-pub", MqttVersion.MQTT_5);
        deliverAndAcknowledgeOne(publisher, subscriber, subscriberId, topic, 2);
        assertThat(publisher.publishQos2(topic, 2, "qos2", MSG_EXPIRY_INTERVAL_SEC)).isEqualTo(SUCCESS);

        RawMqttClient.Packet sent = nextPublish(subscriber);
        assertThat(sent.dup()).isFalse();
        assertOriginalPacketIdIsNotTheFirst(sent);
        // no PUBREC
        disconnect(subscriber, subscriberId);
        Thread.sleep(AWAY);

        RawMqttClient reconnected = connectPersistent(subscriberId);
        RawMqttClient.Packet resent = nextPublish(reconnected);
        assertResentAfterItsIntervalPassed(resent, sent, 2);

        reconnected.pubRec(resent.packetId());
        reconnected.expect(MqttMessageType.PUBREL, resent.packetId());
        reconnected.pubComp(resent.packetId());
        awaitQueueEmpty(subscriberId);
    }

    @Test
    public void mqtt_4_4_0_1_aPubRelIsResentAfterItsIntervalPassed() throws Exception {
        String topic = "spec/resend/pubrel";
        String subscriberId = "resend-pubrel-sub";
        RawMqttClient subscriber = persistentSubscriber(subscriberId, topic, 2);
        RawMqttClient publisher = connectClean("resend-pubrel-pub", MqttVersion.MQTT_5);
        deliverAndAcknowledgeOne(publisher, subscriber, subscriberId, topic, 2);
        assertThat(publisher.publishQos2(topic, 2, "pubrel", MSG_EXPIRY_INTERVAL_SEC)).isEqualTo(SUCCESS);

        RawMqttClient.Packet sent = nextPublish(subscriber);
        assertOriginalPacketIdIsNotTheFirst(sent);
        subscriber.pubRec(sent.packetId());
        subscriber.expect(MqttMessageType.PUBREL, sent.packetId());
        // no PUBCOMP
        disconnect(subscriber, subscriberId);
        Thread.sleep(AWAY);

        RawMqttClient reconnected = connectPersistent(subscriberId);
        // the first packet: the PUBREL with the original packet id, not the PUBLISH again
        reconnected.expect(MqttMessageType.PUBREL, sent.packetId());

        reconnected.pubComp(sent.packetId());
        awaitQueueEmpty(subscriberId);
    }

    /**
     * The broker restarts while the subscribers are away and the interval passes meanwhile: the store keeps that the
     * messages were sent, so a QoS 1 and a QoS 2 message are resent as they would be after a reconnect alone.
     */
    @Test
    public void mqtt_3_3_1_1_dupSurvivesABrokerRestart() throws Exception {
        String qos1Topic = "spec/resend/restart/qos1";
        String qos2Topic = "spec/resend/restart/qos2";
        String qos1SubscriberId = "resend-restart-qos1-sub";
        String qos2SubscriberId = "resend-restart-qos2-sub";
        RawMqttClient qos1Subscriber = persistentSubscriber(qos1SubscriberId, qos1Topic, 1);
        RawMqttClient qos2Subscriber = persistentSubscriber(qos2SubscriberId, qos2Topic, 2);
        RawMqttClient publisher = connectClean("resend-restart-pub", MqttVersion.MQTT_5);
        deliverAndAcknowledgeOne(publisher, qos1Subscriber, qos1SubscriberId, qos1Topic, 1);
        deliverAndAcknowledgeOne(publisher, qos2Subscriber, qos2SubscriberId, qos2Topic, 2);
        assertThat(publisher.publishQos1(qos1Topic, 2, "restart-qos1", MSG_EXPIRY_INTERVAL_SEC)).isEqualTo(SUCCESS);
        assertThat(publisher.publishQos2(qos2Topic, 3, "restart-qos2", MSG_EXPIRY_INTERVAL_SEC)).isEqualTo(SUCCESS);

        RawMqttClient.Packet qos1Sent = nextPublish(qos1Subscriber);
        RawMqttClient.Packet qos2Sent = nextPublish(qos2Subscriber);
        long sentAtNanos = System.nanoTime();
        for (RawMqttClient.Packet sent : List.of(qos1Sent, qos2Sent)) {
            assertThat(sent.dup()).isFalse();
            assertOriginalPacketIdIsNotTheFirst(sent);
        }
        // no PUBACK and no PUBREC; the restart closes the connections
        SpringRestarter.getInstance().restart();
        qos1Subscriber.close();
        qos2Subscriber.close();
        publisher.close();
        Thread.sleep(remaining(AWAY, sentAtNanos));

        RawMqttClient qos1Reconnected = connectPersistent(qos1SubscriberId);
        RawMqttClient qos2Reconnected = connectPersistent(qos2SubscriberId);
        assertThat(qos1Reconnected.sessionPresent()).isTrue();
        assertThat(qos2Reconnected.sessionPresent()).isTrue();
        RawMqttClient.Packet qos1Resent = nextPublish(qos1Reconnected);
        RawMqttClient.Packet qos2Resent = nextPublish(qos2Reconnected);
        assertResentAfterItsIntervalPassed(qos1Resent, qos1Sent, 1);
        assertResentAfterItsIntervalPassed(qos2Resent, qos2Sent, 2);

        qos1Reconnected.pubAck(qos1Resent.packetId());
        qos2Reconnected.pubRec(qos2Resent.packetId());
        qos2Reconnected.expect(MqttMessageType.PUBREL, qos2Resent.packetId());
        qos2Reconnected.pubComp(qos2Resent.packetId());
        awaitQueueEmpty(qos1SubscriberId);
        awaitQueueEmpty(qos2SubscriberId);
    }

    /**
     * Publishes one message (packet id 1 of the publisher) and has the subscriber complete it, so that the message
     * withheld next does not get the subscriber queue's first packet id.
     */
    private void deliverAndAcknowledgeOne(RawMqttClient publisher, RawMqttClient subscriber, String subscriberId,
                                          String topic, int qos) throws IOException {
        if (qos == 1) {
            assertThat(publisher.publishQos1(topic, 1, "first", null)).isEqualTo(SUCCESS);
        } else {
            assertThat(publisher.publishQos2(topic, 1, "first", null)).isEqualTo(SUCCESS);
        }
        RawMqttClient.Packet first = nextPublish(subscriber);
        assertThat(first.payload()).isEqualTo("first");
        if (qos == 1) {
            subscriber.pubAck(first.packetId());
        } else {
            subscriber.pubRec(first.packetId());
            subscriber.expect(MqttMessageType.PUBREL, first.packetId());
            subscriber.pubComp(first.packetId());
        }
        // completed and removed: a reconnect has only the withheld message to resend
        awaitQueueEmpty(subscriberId);
    }

    private static void assertResentAfterItsIntervalPassed(RawMqttClient.Packet resent, RawMqttClient.Packet sent, int qos) {
        assertThat(resent.packetId()).as("packet id").isEqualTo(sent.packetId());
        assertThat(resent.dup()).as("DUP").isTrue();
        assertThat(resent.qos()).as("QoS").isEqualTo(qos);
        assertThat(resent.payload()).isEqualTo(sent.payload());
        assertThat(resent.msgExpiryInterval()).as("Message Expiry Interval left").isZero();
    }

    /**
     * The acknowledged message is removed from the queue: the resend completed the flow.
     */
    private void awaitQueueEmpty(String clientId) {
        await("queue of " + clientId + " empty").atMost(TIMEOUT).until(() -> deviceMsgQueueStore.info(clientId).count() == 0);
    }

    private static Duration remaining(Duration duration, long sinceNanos) {
        Duration left = duration.minusNanos(System.nanoTime() - sinceNanos);
        return left.isNegative() ? Duration.ZERO : left;
    }

}
