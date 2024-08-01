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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.processing.downlink.DownLinkProxy;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@RunWith(SpringRunner.class)
public class DeviceMsgProcessorImplTest {

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

    // TODO: add negative test cases.
    @Test
    public void givenClientMsgPackAndCallbackMock_whenPersistClientDeviceMessages_thenVerifyServicesInvocations() {
        // GIVEN
        String clientId = "clientId";
        byte[] testPayload = "test payload".getBytes();

        var pack = new ClientIdMessagesPack(
                clientId,
                List.of(getDevicePublishMsg(clientId, testPayload)));
        var callback = mock(DefaultClientIdPersistedMsgsCallback.class);

        given(deviceMsgService.saveAndReturnPreviousPacketId(any(), anyList(), anyBoolean())).willReturn(0);

        // WHEN
        deviceMsgProcessor.persistClientDeviceMessages(pack, callback);


        // THEN
        then(clientLogger).should().logEvent(clientId, deviceMsgProcessor.getClass(), "Start persisting DEVICE msgs");
        then(deviceMsgService).should().saveAndReturnPreviousPacketId(clientId, pack.messages(), false);
        then(callback).should().onSuccess(0);
        then(clientLogger).should().logEvent(clientId, deviceMsgProcessor.getClass(), "Finished persisting DEVICE msgs");
    }

    // TODO: add negative test cases.
    @Test
    public void givenClientIdAndMessages_whenDeliverClientDeviceMessages_thenVerifyServicesInvocations() {
        // GIVEN
        String clientId = "clientId";
        String serviceId = "service-id";
        byte[] testPayload = "test payload".getBytes();

        List<DevicePublishMsg> devicePublishMessages = List.of(getDevicePublishMsg(clientId, testPayload));

        var clientSessionInfo = mock(ClientSessionInfo.class);

        given(clientSessionCache.getClientSessionInfo(clientId)).willReturn(clientSessionInfo);
        given(clientSessionInfo.isConnected()).willReturn(true);
        given(clientSessionInfo.getServiceId()).willReturn(serviceId);

        // WHEN
        deviceMsgProcessor.deliverClientDeviceMessages(clientId, devicePublishMessages);

        // THEN
        then(downLinkProxy).should().sendPersistentMsg(serviceId, clientId, devicePublishMessages.get(0));
    }

    private DevicePublishMsg getDevicePublishMsg(String clientId, byte[] testPayload) {
        return new DevicePublishMsg(clientId, "some_topic", 0L, 1, 1,
                PersistedPacketType.PUBLISH, testPayload, MqttProperties.NO_PROPERTIES, false);
    }


}
