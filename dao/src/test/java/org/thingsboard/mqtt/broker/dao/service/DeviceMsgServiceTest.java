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
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

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
    public void testCRUDLastPacketId() throws InterruptedException {
        var saveLatch = new CountDownLatch(1);
        deviceMsgService.saveLastPacketId(TEST_CLIENT_ID, 5).whenComplete((status, throwable) -> {
            assertThat(throwable).isNull();
            assertThat(status).isEqualTo("OK");
            saveLatch.countDown();
        });
        assertThat(saveLatch.await(2, TimeUnit.SECONDS)).isTrue();

        var getLatch = new CountDownLatch(1);
        deviceMsgService.getLastPacketId(TEST_CLIENT_ID).whenComplete((lastPacketId, throwable) -> {
            assertThat(throwable).isNull();
            assertThat(lastPacketId).isEqualTo(5);
            getLatch.countDown();
        });
        assertThat(getLatch.await(2, TimeUnit.SECONDS)).isTrue();

        var secondSaveLatch = new CountDownLatch(1);
        deviceMsgService.saveLastPacketId(TEST_CLIENT_ID, 6).whenComplete((status, throwable) -> {
            assertThat(throwable).isNull();
            assertThat(status).isEqualTo("OK");
            secondSaveLatch.countDown();
        });
        assertThat(secondSaveLatch.await(2, TimeUnit.SECONDS)).isTrue();

        var secondGetLatch = new CountDownLatch(1);
        deviceMsgService.getLastPacketId(TEST_CLIENT_ID).whenComplete((lastPacketId, throwable) -> {
            assertThat(throwable).isNull();
            assertThat(lastPacketId).isEqualTo(6);
            secondGetLatch.countDown();
        });
        assertThat(secondGetLatch.await(2, TimeUnit.SECONDS)).isTrue();

        var removeLatch = new CountDownLatch(1);
        deviceMsgService.removeLastPacketId(TEST_CLIENT_ID).whenComplete((removedCount, throwable) -> {
            assertThat(throwable).isNull();
            assertThat(removedCount).isEqualTo(1);
            removeLatch.countDown();
        });
        assertThat(removeLatch.await(2, TimeUnit.SECONDS)).isTrue();

        var lastGetLatch = new CountDownLatch(1);
        deviceMsgService.getLastPacketId(TEST_CLIENT_ID).whenComplete((lastPacketId, throwable) -> {
            assertThat(throwable).isNull();
            assertThat(lastPacketId).isEqualTo(0);
            lastGetLatch.countDown();
        });
        assertThat(lastGetLatch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testSaveAndGetMessages() throws InterruptedException {
        var devicePublishMsgs = getDevicePublishMsgs(10);

        var saveAndReturnPreviousPacketIdLatch = new CountDownLatch(1);
        deviceMsgService.saveAndReturnPreviousPacketId(TEST_CLIENT_ID, devicePublishMsgs, false).whenComplete((prevPacketId, throwable) -> {
            assertThat(throwable).isNull();
            assertThat(prevPacketId).isEqualTo(0);
            saveAndReturnPreviousPacketIdLatch.countDown();
        });
        assertThat(saveAndReturnPreviousPacketIdLatch.await(2, TimeUnit.SECONDS)).isTrue();

        devicePublishMsgs = getDevicePublishMsgs(5);

        var secondSaveAndReturnPreviousPacketIdLatch = new CountDownLatch(1);
        deviceMsgService.saveAndReturnPreviousPacketId(TEST_CLIENT_ID, devicePublishMsgs, false).whenComplete((prevPacketId, throwable) -> {
            assertThat(throwable).isNull();
            assertThat(prevPacketId).isEqualTo(10);
            secondSaveAndReturnPreviousPacketIdLatch.countDown();
        });
        assertThat(secondSaveAndReturnPreviousPacketIdLatch.await(2, TimeUnit.SECONDS)).isTrue();

        var getLatch = new CountDownLatch(1);
        deviceMsgService.getLastPacketId(TEST_CLIENT_ID).whenComplete((lastPacketId, throwable) -> {
            assertThat(throwable).isNull();
            assertThat(lastPacketId).isEqualTo(15);
            getLatch.countDown();
        });
        assertThat(getLatch.await(2, TimeUnit.SECONDS)).isTrue();

        var findLatch = new CountDownLatch(1);
        deviceMsgService.findPersistedMessages(TEST_CLIENT_ID).whenComplete((persistedMsgs, throwable) -> {
            assertThat(throwable).isNull();
            assertThat(persistedMsgs).isNotNull().isNotEmpty().hasSize(10);
            List<Integer> packetIds = persistedMsgs.stream().map(DevicePublishMsg::getPacketId).sorted().toList();

            int expectedPacketId = 6;
            for (int packetId : packetIds) {
                assertThat(packetId).isEqualTo(expectedPacketId);
                expectedPacketId++;
            }
            findLatch.countDown();
        });
        assertThat(findLatch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testUpdateAndRemoveMessage() throws InterruptedException {
        var msg = newDevicePublishMsgWithBlankPacketId();
        assertThat(msg.getPacketType()).isEqualTo(PersistedPacketType.PUBLISH);

        var saveAndReturnPreviousPacketIdLatch = new CountDownLatch(1);
        deviceMsgService.saveAndReturnPreviousPacketId(TEST_CLIENT_ID, List.of(msg), false).whenComplete((prevPacketId, throwable) -> {
            assertThat(throwable).isNull();
            assertThat(prevPacketId).isEqualTo(0);
            saveAndReturnPreviousPacketIdLatch.countDown();
        });
        assertThat(saveAndReturnPreviousPacketIdLatch.await(2, TimeUnit.SECONDS)).isTrue();

        var updateLatch = new CountDownLatch(1);
        deviceMsgService.updatePacketReceived(TEST_CLIENT_ID, 1).whenComplete((status, throwable) -> {
            assertThat(throwable).isNull();
            assertThat(status).isEqualTo("OK");
            updateLatch.countDown();
        });
        assertThat(updateLatch.await(2, TimeUnit.SECONDS)).isTrue();

        var findLatch = new CountDownLatch(1);
        deviceMsgService.findPersistedMessages(TEST_CLIENT_ID).whenComplete((persistedMsgs, throwable) -> {
            assertThat(throwable).isNull();
            assertThat(persistedMsgs).isNotNull().isNotEmpty().hasSize(1);
            DevicePublishMsg updatedMsg = persistedMsgs.get(0);
            assertThat(updatedMsg.getPacketId()).isEqualTo(1);
            assertThat(updatedMsg.getPacketType()).isEqualTo(PersistedPacketType.PUBREL);
            findLatch.countDown();
        });
        assertThat(findLatch.await(2, TimeUnit.SECONDS)).isTrue();

        var removeLatch = new CountDownLatch(1);
        deviceMsgService.removePersistedMessage(TEST_CLIENT_ID, 1).whenComplete((status, throwable) -> {
            assertThat(throwable).isNull();
            assertThat(status).isEqualTo("OK");
            removeLatch.countDown();
        });
        assertThat(removeLatch.await(2, TimeUnit.SECONDS)).isTrue();

        var secondFindLatch = new CountDownLatch(1);
        deviceMsgService.findPersistedMessages(TEST_CLIENT_ID).whenComplete((persistedMsgs, throwable) -> {
            assertThat(throwable).isNull();
            assertThat(persistedMsgs).isEmpty();
            secondFindLatch.countDown();
        });
        assertThat(secondFindLatch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testRemoveNonExistingMessage() throws InterruptedException {
        var findLatch = new CountDownLatch(1);
        deviceMsgService.findPersistedMessages(TEST_CLIENT_ID).whenComplete((persistedMsgs, throwable) -> {
            assertThat(throwable).isNull();
            assertThat(persistedMsgs).isEmpty();
            findLatch.countDown();
        });
        assertThat(findLatch.await(2, TimeUnit.SECONDS)).isTrue();

        var removeLatch = new CountDownLatch(1);
        deviceMsgService.removePersistedMessage(TEST_CLIENT_ID, 1).whenComplete((o, throwable) -> {
            assertThat(throwable).isNull();
            removeLatch.countDown();
        });
        assertThat(removeLatch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testUpdateNonExistingMessage() throws InterruptedException {
        var findLatch = new CountDownLatch(1);
        deviceMsgService.findPersistedMessages(TEST_CLIENT_ID).whenComplete((persistedMsgs, throwable) -> {
            assertThat(throwable).isNull();
            assertThat(persistedMsgs).isEmpty();
            findLatch.countDown();
        });
        assertThat(findLatch.await(2, TimeUnit.SECONDS)).isTrue();

        var updateLatch = new CountDownLatch(1);
        deviceMsgService.updatePacketReceived(TEST_CLIENT_ID, 1).whenComplete((status, throwable) -> {
            assertThat(throwable).isNull();
            assertThat(status).isEqualTo("OK");
            updateLatch.countDown();
        });
        assertThat(updateLatch.await(2, TimeUnit.SECONDS)).isTrue();
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
