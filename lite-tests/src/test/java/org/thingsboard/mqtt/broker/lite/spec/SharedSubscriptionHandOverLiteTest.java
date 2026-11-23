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
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgQueueStore;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A message of a shared subscription, once handed to one member, is that member's: it is moved from the group's queue
 * into the member's own queue, completed with that member when it reconnects ([MQTT-4.8.2-4]), and never sent to
 * another member, also when the member's session ends before it reconnects ([MQTT-4.8.2-5]). The member's queue keeps
 * its packet id across the hand-over and the reconnect (the card #654 rule in Lite: a queue's packet ids never restart
 * at 1 while it has entries).
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = SharedSubscriptionHandOverLiteTest.class, loader = SpringBootContextLoader.class)
@RunWith(SpringRunner.class)
public class SharedSubscriptionHandOverLiteTest extends AbstractLiteSpecCase {

    private static final String MARKER = "marker";

    @Autowired
    private DeviceMsgQueueStore deviceMsgQueueStore;

    @Test
    public void mqtt_4_8_2_4_aQos2MessageHandedToOneMemberIsCompletedWithThatMemberOnly() throws Exception {
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
        assertThat(b.sessionPresent()).as("member B's session present").isTrue();
        assertNextPublishIsTheMarker(b, memberB, publisher, topic, 3);
        assertThat(deviceMsgQueueStore.info(memberA).count()).as("entries in member A's queue").isEqualTo(1);
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

    @Test
    public void mqtt_4_8_2_5_aQos2MessageOfAMemberWhoseSessionEndedNeverReachesAnother() throws Exception {
        String shareName = "session-end";
        String topic = "spec/shared/session-end";
        String groupQueue = new TopicSharedSubscription(topic, shareName).getKey();
        String memberA = "session-end-member-a";
        String memberB = "session-end-member-b";
        String sharedFilter = "$share/" + shareName + "/" + topic;
        disconnect(persistentSubscriber(memberA, sharedFilter, 2), memberA);
        disconnect(persistentSubscriber(memberB, sharedFilter, 2), memberB);
        RawMqttClient publisher = connectClean("session-end-pub", MqttVersion.MQTT_5);

        // the whole group is offline: the message waits in the group's queue
        assertThat(publisher.publishQos2(topic, 1, "once", null)).isEqualTo(SUCCESS);
        assertThat(deviceMsgQueueStore.info(groupQueue).count()).isEqualTo(1);

        // member A connects first and is handed the message; it withholds its PUBREC
        RawMqttClient a = connectPersistent(memberA);
        RawMqttClient.Packet sent = nextPublish(a);
        assertThat(sent.qos()).isEqualTo(2);
        assertThat(sent.payload()).isEqualTo("once");
        disconnect(a, memberA);
        assertThat(deviceMsgQueueStore.info(memberA).count()).as("entries in member A's queue").isEqualTo(1);

        // A's session ends before A reconnects: A comes back with Clean Start 1 and leaves without a session
        RawMqttClient aClean = connectClean(memberA, MqttVersion.MQTT_5);
        assertThat(aClean.sessionPresent()).as("member A's session present").isFalse();
        aClean.disconnect();
        await("member A's session removed").atMost(TIMEOUT)
                .until(() -> clientSessionCache.getClientSessionInfo(memberA) == null);
        await("member A's queue empty").atMost(TIMEOUT).until(() -> deviceMsgQueueStore.info(memberA).count() == 0);
        assertThat(deviceMsgQueueStore.info(groupQueue).count()).as("entries in the group's queue").isZero();

        // member B, now the group's only member, must not get the message
        RawMqttClient b = connectPersistent(memberB);
        assertThat(b.sessionPresent()).as("member B's session present").isTrue();
        assertNextPublishIsTheMarker(b, memberB, publisher, topic, 2);
    }

    /**
     * Publishes a marker to the group and checks that it is the member's next PUBLISH and that nothing is left for the
     * member afterwards. Only connected members are sent a shared subscription's messages, so the marker proves the
     * member's shared subscription is live: a message that was the member's to get would have come before the marker or
     * would still wait in the member's queue.
     */
    private void assertNextPublishIsTheMarker(RawMqttClient member, String memberId, RawMqttClient publisher, String topic,
                                              int publisherPacketId) throws IOException {
        assertThat(publisher.publishQos2(topic, publisherPacketId, MARKER, null)).isEqualTo(SUCCESS);
        RawMqttClient.Packet next = nextPublish(member);
        assertThat(next.payload()).as("payload of the member's next PUBLISH").isEqualTo(MARKER);
        member.pubRec(next.packetId());
        member.expect(MqttMessageType.PUBREL, next.packetId());
        member.pubComp(next.packetId());
        await("queue of " + memberId + " empty").atMost(TIMEOUT).until(() -> deviceMsgQueueStore.info(memberId).count() == 0);
    }

}
