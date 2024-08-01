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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.processing.downlink.DownLinkProxy;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMsgProcessorImpl implements DeviceMsgProcessor {

    private final ClientSessionCache clientSessionCache;
    private final ClientLogger clientLogger;
    private final DownLinkProxy downLinkProxy;
    private final DeviceMsgService deviceMsgService;

    @Override
    public void persistClientDeviceMessages(ClientIdMessagesPack pack, DefaultClientIdPersistedMsgsCallback callback) {
        String clientId = pack.clientId();
        List<DevicePublishMsg> devicePublishMessages = pack.messages();
        clientLogger.logEvent(clientId, this.getClass(), "Start persisting DEVICE msg");
        try {
            callback.onSuccess(deviceMsgService.saveAndReturnPreviousPacketId(clientId, devicePublishMessages, false));
        } catch (Exception e) {
            callback.onFailure(e);
        }
        clientLogger.logEvent(clientId, this.getClass(), "Finished persisting DEVICE msg");
    }

    @Override
    public void deliverClientDeviceMessages(String clientId, List<DevicePublishMsg> devicePublishMessages) {
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
        for (var devicePublishMsg : devicePublishMessages) {
            String targetServiceId = clientSessionInfo.getServiceId();
            if (messageWasPersisted(devicePublishMsg)) {
                downLinkProxy.sendPersistentMsg(
                        targetServiceId,
                        devicePublishMsg.getClientId(),
                        devicePublishMsg);
            } else {
                downLinkProxy.sendBasicMsg(
                        targetServiceId,
                        devicePublishMsg.getClientId(),
                        ProtoConverter.convertToPublishMsgProto(devicePublishMsg));
            }
        }
    }

    private boolean messageWasPersisted(DevicePublishMsg devicePublishMsg) {
        return !devicePublishMsg.getPacketId().equals(BrokerConstants.BLANK_PACKET_ID);
    }

}
