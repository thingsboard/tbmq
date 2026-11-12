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
package org.thingsboard.mqtt.broker.standalone.device;

import io.netty.handler.codec.mqtt.MqttProperties;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.dao.messages.NewDeviceMsg;
import org.thingsboard.mqtt.broker.dao.messages.StoredDeviceMsg;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceMsgBodyCodecTest {

    private static final long CREATED_MS = 1_700_000_000_000L;

    @Test
    void toNewCarriesTheCreatedTimeAndTheMessageExpiryInterval() {
        DevicePublishMsg msg = msg();
        msg.setMsgExpiryInterval(30);

        NewDeviceMsg created = DeviceMsgBodyCodec.toNew(msg);

        assertThat(created.createdTimeMs()).isEqualTo(CREATED_MS);
        assertThat(created.msgExpiryIntervalSec()).isEqualTo(30);
        assertThat(created.body()).isNotEmpty();
    }

    @Test
    void toNewHasNoIntervalWhenTheMessageHasNone() {
        assertThat(DeviceMsgBodyCodec.toNew(msg()).msgExpiryIntervalSec()).isNull();
    }

    @Test
    void toMsgTakesTheKeyAndTheHeaderAndKeepsTheBody() {
        DevicePublishMsg msg = msg();
        msg.setContentType("text/plain");
        msg.setSubscriptionIds(List.of(4));
        NewDeviceMsg created = DeviceMsgBodyCodec.toNew(msg);

        DevicePublishMsg restored = DeviceMsgBodyCodec.toMsg("queue-key", stored(created.body(), 17, PersistedPacketType.PUBREL));

        assertThat(restored.getClientId()).isEqualTo("queue-key");
        assertThat(restored.getPacketId()).isEqualTo(17);
        assertThat(restored.getPacketType()).isEqualTo(PersistedPacketType.PUBREL);
        assertThat(restored.getTime()).isEqualTo(CREATED_MS);
        assertThat(restored.getTopicName()).isEqualTo("a/b");
        assertThat(restored.getQos()).isEqualTo(1);
        assertThat(restored.getPayload()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        assertThat(restored.getContentType()).isEqualTo("text/plain");
        assertThat(restored.getSubscriptionIds()).containsExactly(4);
    }

    @Test
    void toMsgLeavesTheOriginalMessageExpiryIntervalAsStored() {      // Task 12 computes the remaining interval from it
        DevicePublishMsg msg = msg();
        msg.setMsgExpiryInterval(30);

        DevicePublishMsg restored = DeviceMsgBodyCodec.toMsg("c1", stored(DeviceMsgBodyCodec.toNew(msg).body(), 1, PersistedPacketType.PUBLISH));

        assertThat(restored.getMsgExpiryInterval()).isEqualTo(30);
    }

    @Test
    void toMsgOfAMessageWithoutAnIntervalHasNone() {
        DevicePublishMsg restored = DeviceMsgBodyCodec.toMsg("c1", stored(DeviceMsgBodyCodec.toNew(msg()).body(), 1, PersistedPacketType.PUBLISH));

        assertThat(restored.getMsgExpiryInterval()).isNull();
    }

    @Test
    void withQosAndSubscriptionIdLowersTheQosAndAddsTheId() {
        byte[] body = DeviceMsgBodyCodec.toNew(msg()).body();

        DevicePublishMsg restored = DeviceMsgBodyCodec.toMsg("c1",
                stored(DeviceMsgBodyCodec.withQosAndSubscriptionId(body, 0, 9), 1, PersistedPacketType.PUBLISH));

        assertThat(restored.getQos()).isZero();
        assertThat(restored.getSubscriptionIds()).containsExactly(9);
    }

    @Test
    void withQosAndSubscriptionIdNeverRaisesTheQosAndSkipsAZeroId() {
        byte[] body = DeviceMsgBodyCodec.toNew(msg()).body();

        DevicePublishMsg restored = DeviceMsgBodyCodec.toMsg("c1",
                stored(DeviceMsgBodyCodec.withQosAndSubscriptionId(body, 2, 0), 1, PersistedPacketType.PUBLISH));

        assertThat(restored.getQos()).isEqualTo(1);
        assertThat(restored.getSubscriptionIds()).isNull();
    }

    @Test
    void withQosAndSubscriptionIdKeepsTheExistingSubscriptionIdsAndTheInterval() {
        DevicePublishMsg msg = msg();
        msg.setSubscriptionIds(List.of(3));
        msg.setMsgExpiryInterval(60);

        DevicePublishMsg restored = DeviceMsgBodyCodec.toMsg("c1", stored(
                DeviceMsgBodyCodec.withQosAndSubscriptionId(DeviceMsgBodyCodec.toNew(msg).body(), 1, 8), 1, PersistedPacketType.PUBLISH));

        assertThat(restored.getSubscriptionIds()).containsExactly(3, 8);
        assertThat(restored.getMsgExpiryInterval()).isEqualTo(60);
    }

    private static DevicePublishMsg msg() {
        return new DevicePublishMsg("c1", "a/b", CREATED_MS, 1, BrokerConstants.BLANK_PACKET_ID, PersistedPacketType.PUBLISH,
                "hello".getBytes(StandardCharsets.UTF_8), new MqttProperties(), false);
    }

    private static StoredDeviceMsg stored(byte[] body, int packetId, PersistedPacketType type) {
        return new StoredDeviceMsg(packetId, packetId, type, false, CREATED_MS, 0L, body);
    }

}
