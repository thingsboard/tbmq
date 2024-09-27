/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao.service;

import io.netty.handler.codec.mqtt.MqttProperties;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@DaoSqlTest
public class DeviceMsgServiceTest extends AbstractServiceTest {

    @Autowired
    private DeviceMsgService deviceMsgService;

    private final String TEST_CLIENT_ID = "testClientId";
    private final byte[] TEST_PAYLOAD = "testPayload".getBytes();

    @After
    public void clearState() {
        deviceMsgService.removePersistedMessages(TEST_CLIENT_ID);
    }

    @Test
    public void testCRUDLastPacketId() {
        deviceMsgService.saveLastPacketId(TEST_CLIENT_ID, 5);
        assertThat(deviceMsgService.getLastPacketId(TEST_CLIENT_ID)).isEqualTo(5);

        deviceMsgService.saveLastPacketId(TEST_CLIENT_ID, 6);
        assertThat(deviceMsgService.getLastPacketId(TEST_CLIENT_ID)).isEqualTo(6);

        deviceMsgService.removeLastPacketId(TEST_CLIENT_ID);
        assertThat(deviceMsgService.getLastPacketId(TEST_CLIENT_ID)).isEqualTo(0);
    }

    @Test
    public void testSaveAndGetMessages() {
        var devicePublishMsgs = getDevicePublishMsgs(10);

        int actualPreviousPacketId = deviceMsgService.saveAndReturnPreviousPacketId(TEST_CLIENT_ID, devicePublishMsgs, false);
        assertThat(actualPreviousPacketId).isEqualTo(0);

        devicePublishMsgs = getDevicePublishMsgs(5);

        actualPreviousPacketId = deviceMsgService.saveAndReturnPreviousPacketId(TEST_CLIENT_ID, devicePublishMsgs, false);
        assertThat(actualPreviousPacketId).isEqualTo(10);

        int actualLastPacketId = deviceMsgService.getLastPacketId(TEST_CLIENT_ID);
        assertThat(actualLastPacketId).isEqualTo(15);

        var persistedMessages = deviceMsgService.findPersistedMessages(TEST_CLIENT_ID);
        assertThat(persistedMessages).isNotNull().isNotEmpty().hasSize(10);

        List<Integer> packetIds = persistedMessages.stream().map(DevicePublishMsg::getPacketId).sorted().toList();

        int expectedPacketId = 6;
        for (int packetId : packetIds) {
            assertThat(packetId).isEqualTo(expectedPacketId);
            expectedPacketId++;
        }
    }

    @Test
    public void testUpdateAndRemoveMessage() {
        var msg = newDevicePublishMsgWithBlankPacketId();
        int actualPreviousPacketId = deviceMsgService.saveAndReturnPreviousPacketId(TEST_CLIENT_ID, List.of(msg), false);
        assertThat(actualPreviousPacketId).isEqualTo(0);
        assertThat(msg.getPacketType()).isEqualTo(PersistedPacketType.PUBLISH);

        deviceMsgService.updatePacketReceived(TEST_CLIENT_ID, 1);

        var persistedMessages = deviceMsgService.findPersistedMessages(TEST_CLIENT_ID);
        assertThat(persistedMessages).isNotNull().isNotEmpty().hasSize(1);
        DevicePublishMsg updatedMsg = persistedMessages.get(0);
        assertThat(updatedMsg.getPacketId()).isEqualTo(1);
        assertThat(updatedMsg.getPacketType()).isEqualTo(PersistedPacketType.PUBREL);

        deviceMsgService.removePersistedMessage(TEST_CLIENT_ID, 1);

        persistedMessages = deviceMsgService.findPersistedMessages(TEST_CLIENT_ID);
        assertThat(persistedMessages).isEmpty();
    }

    @Test
    public void testRemoveNonExistingMessage() {
        var persistedMessages = deviceMsgService.findPersistedMessages(TEST_CLIENT_ID);
        assertThat(persistedMessages).isEmpty();
        assertThatNoException().isThrownBy(() -> deviceMsgService.removePersistedMessage(TEST_CLIENT_ID, 1));
    }

    @Test
    public void testUpdateNonExistingMessage() {
        var persistedMessages = deviceMsgService.findPersistedMessages(TEST_CLIENT_ID);
        assertThat(persistedMessages).isEmpty();
        assertThatNoException().isThrownBy(() -> deviceMsgService.updatePacketReceived(TEST_CLIENT_ID, 1));
    }

    private DevicePublishMsg newDevicePublishMsgWithBlankPacketId() {
        return new DevicePublishMsg(TEST_CLIENT_ID, UUID.randomUUID().toString(), 0L, 0, BrokerConstants.BLANK_PACKET_ID,
                PersistedPacketType.PUBLISH, TEST_PAYLOAD, new MqttProperties(), false);
    }

    private List<DevicePublishMsg> getDevicePublishMsgs(int msgCount) {
        return IntStream.range(0, msgCount)
                .mapToObj(__ -> newDevicePublishMsgWithBlankPacketId())
                .toList();
    }

}
