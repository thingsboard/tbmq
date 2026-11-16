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
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgQueueStore;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A message of a shared subscription, once handed to one member, is that member's: it is moved from the group's queue
 * into the member's own queue, completed with that member when it reconnects, and never sent to another member
 * ([MQTT-4.8.2-4], [MQTT-4.8.2-5]). The member's queue keeps its packet id across the hand-over and the reconnect
 * (the card #654 rule in Lite: a queue's packet ids never restart at 1 while it has entries).
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = SharedSubscriptionHandOverLiteTest.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class SharedSubscriptionHandOverLiteTest extends AbstractLiteSpecCase {

    /**
     * How long member B is watched for a message it must not get. B's device actor takes over the group's queue as
     * soon as B connects, which takes milliseconds; a longer wait only slows the test down.
     */
    private static final Duration NOTHING_ARRIVES_WITHIN = Duration.ofSeconds(3);

    @Autowired
    private DeviceMsgQueueStore deviceMsgQueueStore;

    @Test
    public void mqtt_4_8_2_4_and_4_8_2_5_aQos2MessageHandedToOneMemberNeverReachesAnother() throws Exception {
        String shareName = "handover";
        String topic = "spec/shared/qos2";
        String groupQueue = new TopicSharedSubscription(topic, shareName).getKey();
        String memberA = "handover-member-a";
        String memberB = "handover-member-b";
        String sharedFilter = "$share/" + shareName + "/" + topic;
        RawMqttClient firstA = persistentSubscriber(memberA, sharedFilter, 2);
        disconnect(persistentSubscriber(memberB, sharedFilter, 2), memberB);
        RawMqttClient publisher = connectClean("handover-pub", MqttVersion.MQTT_5);

        // A, the only member online, gets and completes one message first, so that the message handed over later does
        // not get the first packet id of A's queue
        assertThat(publisher.publishQos2(topic, 1, "first", null)).isEqualTo(SUCCESS);
        RawMqttClient.Packet first = nextPublish(firstA);
        assertThat(first.payload()).isEqualTo("first");
        firstA.pubRec(first.packetId());
        firstA.expect(MqttMessageType.PUBREL, first.packetId());
        firstA.pubComp(first.packetId());
        await("member A's queue empty").atMost(TIMEOUT).until(() -> deviceMsgQueueStore.info(memberA).count() == 0);
        disconnect(firstA, memberA);

        // the whole group is offline: the message waits in the group's queue
        assertThat(publisher.publishQos2(topic, 2, "once", null)).isEqualTo(SUCCESS);
        assertThat(deviceMsgQueueStore.info(groupQueue).count()).isEqualTo(1);

        // member A connects first and is handed the message; it withholds its PUBREC
        RawMqttClient a = connectPersistent(memberA);
        RawMqttClient.Packet sent = nextPublish(a);
        assertThat(sent.qos()).isEqualTo(2);
        assertThat(sent.dup()).isFalse();
        assertThat(sent.payload()).isEqualTo("once");
        assertOriginalPacketIdIsNotTheFirst(sent);
        disconnect(a, memberA);
        assertThat(deviceMsgQueueStore.info(groupQueue).count()).as("entries left in the group's queue").isZero();
        assertThat(deviceMsgQueueStore.info(memberA).count()).as("entries in member A's queue").isEqualTo(1);

        // member B is now the only member online: the message is not B's to get
        RawMqttClient b = connectPersistent(memberB);
        // B still has its session and its shared subscription, so it could be sent the message
        assertThat(b.sessionPresent()).as("member B's session present").isTrue();
        assertThat(b.poll(NOTHING_ARRIVES_WITHIN)).as("packet to member B").isEmpty();
        disconnect(b, memberB);

        // member A reconnects and completes the message
        RawMqttClient aAgain = connectPersistent(memberA);
        RawMqttClient.Packet resent = nextPublish(aAgain);
        assertThat(resent.packetId()).as("packet id").isEqualTo(sent.packetId());
        assertThat(resent.dup()).as("DUP").isTrue();
        assertThat(resent.payload()).isEqualTo("once");
        aAgain.pubRec(resent.packetId());
        aAgain.expect(MqttMessageType.PUBREL, resent.packetId());
        aAgain.pubComp(resent.packetId());
        await("member A's queue empty").atMost(TIMEOUT).until(() -> deviceMsgQueueStore.info(memberA).count() == 0);
    }

}
