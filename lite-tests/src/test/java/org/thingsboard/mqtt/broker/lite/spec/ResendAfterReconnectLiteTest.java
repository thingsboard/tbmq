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
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgQueueStore;
import org.thingsboard.mqtt.broker.service.test.util.RestartingSpringJUnit4ClassRunner;
import org.thingsboard.mqtt.broker.service.test.util.SpringRestarter;

import java.io.IOException;
import java.time.Duration;

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
@DaoSqlTest
@RunWith(RestartingSpringJUnit4ClassRunner.class)
public class ResendAfterReconnectLiteTest extends AbstractLiteSpecCase {

    private static final int MSG_EXPIRY_INTERVAL_SEC = 1;
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
        assertThat(publisher.publishQos1(topic, 1, "qos1", MSG_EXPIRY_INTERVAL_SEC)).isEqualTo(SUCCESS);

        RawMqttClient.Packet sent = nextPublish(subscriber);
        assertThat(sent.dup()).isFalse();
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
        assertThat(publisher.publishQos2(topic, 1, "qos2", MSG_EXPIRY_INTERVAL_SEC)).isEqualTo(SUCCESS);

        RawMqttClient.Packet sent = nextPublish(subscriber);
        assertThat(sent.dup()).isFalse();
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
        assertThat(publisher.publishQos2(topic, 1, "pubrel", MSG_EXPIRY_INTERVAL_SEC)).isEqualTo(SUCCESS);

        RawMqttClient.Packet sent = nextPublish(subscriber);
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
     * The broker restarts while the subscriber is away and the interval passes meanwhile: the store keeps that the
     * message was sent, so it is resent as it would be after a reconnect alone.
     */
    @Test
    public void mqtt_3_3_1_1_dupSurvivesABrokerRestart() throws Exception {
        String topic = "spec/resend/restart";
        String subscriberId = "resend-restart-sub";
        RawMqttClient subscriber = persistentSubscriber(subscriberId, topic, 1);
        RawMqttClient publisher = connectClean("resend-restart-pub", MqttVersion.MQTT_5);
        assertThat(publisher.publishQos1(topic, 1, "restart", MSG_EXPIRY_INTERVAL_SEC)).isEqualTo(SUCCESS);

        RawMqttClient.Packet sent = nextPublish(subscriber);
        assertThat(sent.dup()).isFalse();
        long sentAtNanos = System.nanoTime();
        // no PUBACK; the restart closes the connection
        SpringRestarter.getInstance().restart();
        subscriber.close();
        publisher.close();
        Thread.sleep(remaining(AWAY, sentAtNanos));

        RawMqttClient reconnected = connectPersistent(subscriberId);
        assertThat(reconnected.sessionPresent()).isTrue();
        RawMqttClient.Packet resent = nextPublish(reconnected);
        assertResentAfterItsIntervalPassed(resent, sent, 1);

        reconnected.pubAck(resent.packetId());
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
