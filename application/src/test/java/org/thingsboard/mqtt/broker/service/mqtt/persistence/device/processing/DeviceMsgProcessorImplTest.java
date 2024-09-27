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
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.processing.downlink.DownLinkProxy;

import java.util.List;

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
    private final String TEST_SERVICE_ID = "service-id";

    @MockBean
    ClientSessionCache clientSessionCache;
    @MockBean
    ClientLogger clientLogger;
    @MockBean
    DownLinkProxy downLinkProxy;
    @MockBean
    DeviceMsgAcknowledgeStrategyFactory ackStrategyFactory;
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
        var callback = mock(DefaultClientIdPersistedMsgsCallback.class);

        given(deviceMsgService.saveAndReturnPreviousPacketId(any(), anyList(), anyBoolean())).willReturn(0);

        // WHEN
        deviceMsgProcessor.persistClientDeviceMessages(pack, callback);

        // THEN
        then(clientLogger).should()
                .logEvent(TEST_CLIENT_ID, deviceMsgProcessor.getClass(), "Start persisting DEVICE msgs");
        then(deviceMsgService).should().saveAndReturnPreviousPacketId(TEST_CLIENT_ID, pack.messages(), false);
        then(callback).should().onSuccess(0);
        then(clientLogger).should()
                .logEvent(TEST_CLIENT_ID, deviceMsgProcessor.getClass(), "Finished persisting DEVICE msgs");
    }

    @Test
    public void givenClientMsgPackAndCallbackMock_whenPersistClientDeviceMessages_thenVerifyFailureServicesInvocations() {
        // GIVEN
        var pack = new ClientIdMessagesPack(
                TEST_CLIENT_ID,
                List.of(getDevicePublishMsgWithBlankPacketId()));
        var callback = mock(DefaultClientIdPersistedMsgsCallback.class);

        var invalidDataAccessApiUsageException = new InvalidDataAccessApiUsageException("Failed to process lua script!");
        given(deviceMsgService.saveAndReturnPreviousPacketId(any(), anyList(), anyBoolean()))
                .willThrow(invalidDataAccessApiUsageException);

        // WHEN
        deviceMsgProcessor.persistClientDeviceMessages(pack, callback);

        // THEN
        then(clientLogger).should()
                .logEvent(TEST_CLIENT_ID, deviceMsgProcessor.getClass(), "Start persisting DEVICE msgs");
        then(deviceMsgService).should().saveAndReturnPreviousPacketId(TEST_CLIENT_ID, pack.messages(), false);
        then(callback).should().onFailure(invalidDataAccessApiUsageException);
        then(clientLogger).should()
                .logEvent(TEST_CLIENT_ID, deviceMsgProcessor.getClass(), "Finished persisting DEVICE msgs");
    }

    // TODO: add negative test cases.
    @Test
    public void givenClientIdAndMessagesWithValidPacketIds_whenDeliverClientDeviceMessages_thenVerifySuccessServicesInvocations() {
        // GIVEN
        var firstMsg = getDevicePublishMsg(1);
        var secondMsg = getDevicePublishMsg(2);
        List<DevicePublishMsg> devicePublishMessages = List.of(firstMsg, secondMsg);

        var clientSessionInfo = mock(ClientSessionInfo.class);

        given(clientSessionCache.getClientSessionInfo(TEST_CLIENT_ID)).willReturn(clientSessionInfo);
        given(clientSessionInfo.isConnected()).willReturn(true);
        given(clientSessionInfo.getServiceId()).willReturn(TEST_SERVICE_ID);

        // WHEN
        deviceMsgProcessor.deliverClientDeviceMessages(TEST_CLIENT_ID, devicePublishMessages);

        // THEN
        var msgArgumentCaptor = ArgumentCaptor.forClass(DevicePublishMsg.class);
        then(downLinkProxy).should(times(2))
                .sendPersistentMsg(eq(TEST_SERVICE_ID), eq(TEST_CLIENT_ID), msgArgumentCaptor.capture());
        assertThat(msgArgumentCaptor.getAllValues()).isNotNull().hasSize(2)
                .containsExactlyElementsOf(devicePublishMessages);
    }

    @Test
    public void givenClientIdAndMessagesWithBlankPacketIds_whenDeliverClientDeviceMessages_thenVerifySuccessServicesInvocations() {
        // GIVEN
        var firstMsg = getDevicePublishMsgWithBlankPacketId();
        var secondMsg = getDevicePublishMsgWithBlankPacketId();
        List<DevicePublishMsg> devicePublishMessages = List.of(firstMsg, secondMsg);

        var clientSessionInfo = mock(ClientSessionInfo.class);

        given(clientSessionCache.getClientSessionInfo(TEST_CLIENT_ID)).willReturn(clientSessionInfo);
        given(clientSessionInfo.isConnected()).willReturn(true);
        given(clientSessionInfo.getServiceId()).willReturn(TEST_SERVICE_ID);

        // WHEN
        deviceMsgProcessor.deliverClientDeviceMessages(TEST_CLIENT_ID, devicePublishMessages);

        // THEN
        var msgArgumentCaptor = ArgumentCaptor.forClass(QueueProtos.PublishMsgProto.class);
        then(downLinkProxy).should(times(2))
                .sendBasicMsg(eq(TEST_SERVICE_ID), eq(TEST_CLIENT_ID), msgArgumentCaptor.capture());
        List<QueueProtos.PublishMsgProto> expectedMsgs = devicePublishMessages.stream().map(ProtoConverter::convertToPublishMsgProto).toList();
        assertThat(msgArgumentCaptor.getAllValues()).isNotNull().hasSize(2)
                .containsExactlyElementsOf(expectedMsgs);
    }

    @Test
    public void givenClientIdAndMessagesWithMixedPacketIds_whenDeliverClientDeviceMessages_thenVerifySuccessServicesInvocations() {
        // GIVEN
        var firstMsg = getDevicePublishMsg(1);
        var secondMsg = getDevicePublishMsgWithBlankPacketId();
        List<DevicePublishMsg> devicePublishMessages = List.of(firstMsg, secondMsg);

        var clientSessionInfo = mock(ClientSessionInfo.class);

        given(clientSessionCache.getClientSessionInfo(TEST_CLIENT_ID)).willReturn(clientSessionInfo);
        given(clientSessionInfo.isConnected()).willReturn(true);
        given(clientSessionInfo.getServiceId()).willReturn(TEST_SERVICE_ID);

        // WHEN
        deviceMsgProcessor.deliverClientDeviceMessages(TEST_CLIENT_ID, devicePublishMessages);

        // THEN
        then(downLinkProxy).should().sendPersistentMsg(TEST_SERVICE_ID, TEST_CLIENT_ID, firstMsg);
        then(downLinkProxy).should().sendBasicMsg(TEST_SERVICE_ID, TEST_CLIENT_ID, ProtoConverter.convertToPublishMsgProto(secondMsg));
    }

    @Test
    public void givenClientIdAndMessages_whenDeliverClientDeviceMessagesAndClientSessionInfoIsNull_thenVerifyDownlinkProxyHaveNoInteractions() {
        // GIVEN
        var firstMsg = getDevicePublishMsg(1);
        var secondMsg = getDevicePublishMsg(2);
        List<DevicePublishMsg> devicePublishMessages = List.of(firstMsg, secondMsg);

        var clientSessionInfo = mock(ClientSessionInfo.class);

        given(clientSessionCache.getClientSessionInfo(TEST_CLIENT_ID)).willReturn(clientSessionInfo);
        given(clientSessionInfo.isConnected()).willReturn(false);

        // WHEN
        deviceMsgProcessor.deliverClientDeviceMessages(TEST_CLIENT_ID, devicePublishMessages);

        // THEN
        then(downLinkProxy).shouldHaveNoInteractions();
    }

    @Test
    public void givenClientIdAndMessages_whenDeliverClientDeviceMessagesAndClientSessionInfoIsNotConnected_thenVerifyDownlinkProxyHaveNoInteractions() {
        // GIVEN
        var firstMsg = getDevicePublishMsg(1);
        var secondMsg = getDevicePublishMsg(2);
        List<DevicePublishMsg> devicePublishMessages = List.of(firstMsg, secondMsg);

        given(clientSessionCache.getClientSessionInfo(TEST_CLIENT_ID)).willReturn(null);

        // WHEN
        deviceMsgProcessor.deliverClientDeviceMessages(TEST_CLIENT_ID, devicePublishMessages);

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
