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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.dto.PacketIdDto;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.processing.downlink.DownLinkProxy;

import java.util.List;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

@RunWith(SpringRunner.class)
public class DeviceMsgProcessorImplTest {

    private final String TEST_CLIENT_ID = "clientId";

    @MockBean
    ClientSessionCache clientSessionCache;
    @MockBean
    DownLinkProxy downLinkProxy;
    @MockBean
    DeviceMsgService deviceMsgService;

    @SpyBean
    DeviceMsgProcessorImpl deviceMsgProcessor;

    @Test
    public void givenClientMsgPackAndCallbackMock_whenPersistClientDeviceMessages_thenVerifySuccessServicesInvocations() {
        // GIVEN
        var pack = new ClientIdMessagesPack(
                TEST_CLIENT_ID,
                List.of(getDevicePublishMsgWithBlankPacketId()));

        var completionStateMock = mock(CompletionStage.class);

        given(deviceMsgService.saveAndReturnPreviousPacketId(any(), anyList(), anyBoolean())).willReturn(completionStateMock);

        // WHEN
        deviceMsgProcessor.persistClientDeviceMessages(pack);

        // THEN
        then(deviceMsgService).should().saveAndReturnPreviousPacketId(TEST_CLIENT_ID, pack.messages(), false);
    }

    @Test
    public void givenClientIdAndMessagesWithValidPacketIds_whenDeliverClientDeviceMessages_thenVerifySuccessServicesInvocations() {
        // GIVEN
        var firstMsg = getDevicePublishMsgWithBlankPacketId();
        var secondMsg = getDevicePublishMsgWithBlankPacketId();
        var devicePublishMessages = new DevicePublishMsgListAndPrevPacketId(List.of(firstMsg, secondMsg), 0);

        var clientSessionInfo = mock(ClientSessionInfo.class);

        given(clientSessionCache.getClientSessionInfo(TEST_CLIENT_ID)).willReturn(clientSessionInfo);
        given(clientSessionInfo.isConnected()).willReturn(true);
        given(clientSessionInfo.getServiceId()).willReturn("service-id");

        // WHEN
        deviceMsgProcessor.deliverClientDeviceMessages(TEST_CLIENT_ID, devicePublishMessages);

        // THEN
        var msgArgumentCaptor = ArgumentCaptor.forClass(DevicePublishMsg.class);
        then(downLinkProxy).should(times(2))
                .sendPersistentMsg(eq("service-id"), eq(TEST_CLIENT_ID), msgArgumentCaptor.capture());

        var packetIdDto = new PacketIdDto(0);
        for (var devicePublishMsg : devicePublishMessages.messages()) {
            devicePublishMsg.setPacketId(packetIdDto.getNextPacketId());
        }
        assertThat(msgArgumentCaptor.getAllValues()).isNotNull().hasSize(2)
                .containsExactlyElementsOf(devicePublishMessages.messages());
    }

    @Test
    public void givenClientIdAndMessages_whenDeliverClientDeviceMessagesAndClientSessionInfoIsNull_thenVerifyDownlinkProxyHaveNoInteractions() {
        // GIVEN
        var clientSessionInfo = mock(ClientSessionInfo.class);

        given(clientSessionCache.getClientSessionInfo(TEST_CLIENT_ID)).willReturn(clientSessionInfo);
        given(clientSessionInfo.isConnected()).willReturn(false);

        // WHEN
        deviceMsgProcessor.deliverClientDeviceMessages(TEST_CLIENT_ID, mock(DevicePublishMsgListAndPrevPacketId.class));

        // THEN
        then(downLinkProxy).shouldHaveNoInteractions();
    }

    @Test
    public void givenClientIdAndMessages_whenDeliverClientDeviceMessagesAndClientSessionInfoIsNotConnected_thenVerifyDownlinkProxyHaveNoInteractions() {
        // GIVEN
        given(clientSessionCache.getClientSessionInfo(TEST_CLIENT_ID)).willReturn(null);

        // WHEN
        deviceMsgProcessor.deliverClientDeviceMessages(TEST_CLIENT_ID, mock(DevicePublishMsgListAndPrevPacketId.class));

        // THEN
        then(downLinkProxy).shouldHaveNoInteractions();
    }

    private DevicePublishMsg getDevicePublishMsgWithBlankPacketId() {
        return getDevicePublishMsg(BrokerConstants.BLANK_PACKET_ID);
    }

    private DevicePublishMsg getDevicePublishMsg(int packetId) {
        return new DevicePublishMsg(TEST_CLIENT_ID, "some_topic", 0L, 1, packetId,
                PersistedPacketType.PUBLISH, "test payload".getBytes(), MqttProperties.NO_PROPERTIES, false);
    }

}
