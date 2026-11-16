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

import io.micrometer.core.instrument.MeterRegistry;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Message Expiry Interval of a message kept for a persistent DEVICE subscriber ([MQTT-3.3.2-5], [MQTT-3.3.2-6]). An
 * interval of 0 is a lifetime of 0 seconds: the message reaches a subscriber that is online when it is dispatched, and
 * is not kept for one that is offline (the card #653 rule in Lite).
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = MessageExpiryLiteTest.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class MessageExpiryLiteTest extends AbstractLiteSpecCase {

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    public void mqtt_3_3_2_5_and_3_3_2_6_intervalZeroReachesAnOnlinePersistentSubscriberWithIntervalZero() throws Exception {
        String topic = "spec/expiry/zero-online";
        RawMqttClient subscriber = persistentSubscriber("expiry-zero-online-sub", topic, 1);
        RawMqttClient publisher = connectClean("expiry-zero-online-pub", MqttVersion.MQTT_5);

        assertThat(publisher.publishQos1(topic, 1, "zero", 0)).isEqualTo(SUCCESS);

        RawMqttClient.Packet publish = nextPublish(subscriber);
        assertThat(publish.payload()).isEqualTo("zero");
        // [MQTT-3.3.2-6] the interval received less the time the message waited, so 0
        assertThat(publish.msgExpiryInterval()).isZero();
        subscriber.pubAck(publish.packetId());
    }

    @Test
    public void mqtt_3_3_2_5_intervalZeroIsNotKeptForAnOfflinePersistentSubscriber() throws Exception {
        String topic = "spec/expiry/zero-offline";
        String subscriberId = "expiry-zero-offline-sub";
        disconnect(persistentSubscriber(subscriberId, topic, 1), subscriberId);
        double expiredBefore = expiredDeviceMsgs();
        double droppedBefore = droppedMsgs();
        RawMqttClient publisher = connectClean("expiry-zero-offline-pub", MqttVersion.MQTT_5);

        assertThat(publisher.publishQos1(topic, 1, "zero", 0)).isEqualTo(SUCCESS);
        // the copy is discarded and counted before the publish is acknowledged; one kept in the queue would count
        // only once the store expired it
        assertThat(expiredDeviceMsgs()).isEqualTo(expiredBefore + 1);

        RawMqttClient subscriber = connectPersistent(subscriberId);
        assertThat(subscriber.sessionPresent()).isTrue();
        assertTheNextMessageIsAMarker(publisher, subscriber, topic);
        // counted as expired, not as dropped
        assertThat(droppedMsgs()).as("droppedMsgs").isEqualTo(droppedBefore);
    }

    @Test
    public void mqtt_3_3_2_5_anUnsentMessageIsNotDeliveredAfterItsIntervalPassed() throws Exception {
        String topic = "spec/expiry/unsent";
        String subscriberId = "expiry-unsent-sub";
        disconnect(persistentSubscriber(subscriberId, topic, 1), subscriberId);
        double expiredBefore = expiredDeviceMsgs();
        RawMqttClient publisher = connectClean("expiry-unsent-pub", MqttVersion.MQTT_5);

        assertThat(publisher.publishQos1(topic, 1, "expiring", 1)).isEqualTo(SUCCESS);
        // the subscriber stays away 2 s, past the interval of 1 s
        Thread.sleep(Duration.ofSeconds(2));

        RawMqttClient subscriber = connectPersistent(subscriberId);
        assertThat(subscriber.sessionPresent()).isTrue();
        assertTheNextMessageIsAMarker(publisher, subscriber, topic);
        // the store counts the entry it deletes as expired
        await("expired entry counted").atMost(TIMEOUT).until(() -> expiredDeviceMsgs() == expiredBefore + 1);
    }

    /**
     * Publishes a marker without an interval to the reconnected subscriber and checks that it is the first message the
     * subscriber gets. The queue is delivered in order, so a message still kept for the subscriber would arrive before
     * the marker: no waiting interval is needed to show that nothing else arrives.
     */
    private void assertTheNextMessageIsAMarker(RawMqttClient publisher, RawMqttClient subscriber, String topic) throws IOException {
        assertThat(publisher.publishQos1(topic, 2, "marker", null)).isEqualTo(SUCCESS);
        RawMqttClient.Packet publish = nextPublish(subscriber);
        assertThat(publish.payload()).isEqualTo("marker");
        subscriber.pubAck(publish.packetId());
    }

    private double expiredDeviceMsgs() {
        return meterRegistry.get(StatsType.EXPIRED_DEVICE_MSGS.getPrintName()).functionCounter().count();
    }

    /**
     * The Micrometer counter, which only grows; the stats counter next to it is reset by a scheduler.
     */
    private double droppedMsgs() {
        return meterRegistry.get(StatsType.DROPPED_MSGS.getPrintName()).counter().count();
    }

}
