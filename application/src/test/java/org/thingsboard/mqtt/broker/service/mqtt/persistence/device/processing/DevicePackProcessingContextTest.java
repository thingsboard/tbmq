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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.device.processing;

import io.netty.handler.codec.mqtt.MqttProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

@RunWith(MockitoJUnitRunner.class)
class DevicePackProcessingContextTest {

    private final String TEST_CLIENT_ID = "clientId";

    private DevicePackProcessingContext devicePackProcessingContext;

    @BeforeEach
    public void setUp() {
        devicePackProcessingContext = spy(new DevicePackProcessingContext(new ConcurrentHashMap<>()));
    }

    @Test
    void testUpdatePacketIdsStartWithZero() {
        int packSize = 10;
        var publishMsgs = IntStream.range(0, packSize)
                .mapToObj(i -> getDevicePublishMsgWithBlankPacketId()).toList();

        var updatedDevicePublishMsgs = devicePackProcessingContext
                .updatePacketIds(0, new ClientIdMessagesPack(TEST_CLIENT_ID, publishMsgs));

        assertThat(updatedDevicePublishMsgs).isNotNull().hasSize(packSize);
        List<Integer> expectedPacketIds = List.of(1, 2, 3,
                4, 5, 6, 7, 8, 9, 10);
        List<Integer> actualPacketIds = updatedDevicePublishMsgs.stream().map(DevicePublishMsg::getPacketId).toList();
        assertThat(actualPacketIds).containsExactlyElementsOf(expectedPacketIds);
    }

    @Test
    void testUpdatePacketIdsMaxValueReached() {
        int packSize = 10;
        var publishMsgs = IntStream.range(0, packSize)
                .mapToObj(i -> getDevicePublishMsgWithBlankPacketId()).toList();

        var updatedDevicePublishMsgs = devicePackProcessingContext
                .updatePacketIds(0xfff5, new ClientIdMessagesPack(TEST_CLIENT_ID, publishMsgs));

        assertThat(updatedDevicePublishMsgs).isNotNull().hasSize(packSize);
        List<Integer> expectedPacketIds = List.of(0xfff6, 0xfff7, 0xfff8, 0xfff9,
                0xfffa, 0xfffb, 0xfffc, 0xfffd, 0xfffe, 0xffff);
        List<Integer> actualPacketIds = updatedDevicePublishMsgs.stream().map(DevicePublishMsg::getPacketId).toList();
        assertThat(actualPacketIds).containsExactlyElementsOf(expectedPacketIds);
    }

    @Test
    void testUpdatePacketIdsMaxValueExceeded() {
        int packSize = 10;
        var publishMsgs = IntStream.range(0, packSize)
                .mapToObj(i -> getDevicePublishMsgWithBlankPacketId()).toList();

        var updatedDevicePublishMsgs = devicePackProcessingContext
                .updatePacketIds(0xfffe, new ClientIdMessagesPack(TEST_CLIENT_ID, publishMsgs));

        assertThat(updatedDevicePublishMsgs).isNotNull().hasSize(packSize);
        List<Integer> expectedPacketIds = List.of(0xffff, 1, 2, 3,
                4, 5, 6, 7, 8, 9);
        List<Integer> actualPacketIds = updatedDevicePublishMsgs.stream().map(DevicePublishMsg::getPacketId).toList();
        assertThat(actualPacketIds).containsExactlyElementsOf(expectedPacketIds);
    }

    @Test
    void testUpdatePacketIdsMaxValueExceededStartWithMaxValue() {
        int packSize = 10;
        var publishMsgs = IntStream.range(0, packSize)
                .mapToObj(i -> getDevicePublishMsgWithBlankPacketId()).toList();

        var updatedDevicePublishMsgs = devicePackProcessingContext
                .updatePacketIds(0xffff, new ClientIdMessagesPack(TEST_CLIENT_ID, publishMsgs));

        assertThat(updatedDevicePublishMsgs).isNotNull().hasSize(packSize);
        List<Integer> expectedPacketIds = List.of(1, 2, 3,
                4, 5, 6, 7, 8, 9, 10);
        List<Integer> actualPacketIds = updatedDevicePublishMsgs.stream().map(DevicePublishMsg::getPacketId).toList();
        assertThat(actualPacketIds).containsExactlyElementsOf(expectedPacketIds);
    }

    private DevicePublishMsg getDevicePublishMsgWithBlankPacketId() {
        return new DevicePublishMsg(TEST_CLIENT_ID, "some_topic", 0L, 1, BrokerConstants.BLANK_PACKET_ID,
                PersistedPacketType.PUBLISH, "test payload".getBytes(), MqttProperties.NO_PROPERTIES, false);
    }

}
