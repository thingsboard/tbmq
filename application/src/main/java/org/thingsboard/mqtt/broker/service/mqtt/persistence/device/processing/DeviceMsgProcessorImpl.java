/**
 * Copyright © 2016-2025 The Thingsboard Authors
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.dto.PacketIdDto;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.processing.downlink.DownLinkProxy;

import java.util.concurrent.CompletionStage;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMsgProcessorImpl implements DeviceMsgProcessor {

    private final ClientSessionCache clientSessionCache;
    private final DownLinkProxy downLinkProxy;
    private final DeviceMsgService deviceMsgService;

    @Override
    public CompletionStage<Integer> persistClientDeviceMessages(ClientIdMessagesPack pack) {
        return deviceMsgService.saveAndReturnPreviousPacketId(pack.clientId(), pack.messages(), false);
    }

    @Override
    public void deliverClientDeviceMessages(String clientId, DevicePublishMsgListAndPrevPacketId devicePubMsgsAndPrevId) {
        var clientSessionInfo = clientSessionCache.getClientSessionInfo(clientId);
        if (clientSessionInfo == null) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] Client session is not found for persisted messages.", clientId);
            }
            return;
        }
        if (!clientSessionInfo.isConnected()) {
            if (log.isTraceEnabled()) {
                log.trace("[{}] Client session is disconnected.", clientId);
            }
            return;
        }
        String targetServiceId = clientSessionInfo.getServiceId();
        var packetIdDto = new PacketIdDto(devicePubMsgsAndPrevId.previousPacketId());
        for (var devicePublishMsg : devicePubMsgsAndPrevId.messages()) {
            devicePublishMsg.setPacketId(packetIdDto.getNextPacketId());
            downLinkProxy.sendPersistentMsg(
                    targetServiceId,
                    devicePublishMsg.getClientId(),
                    devicePublishMsg);
        }
    }

}
