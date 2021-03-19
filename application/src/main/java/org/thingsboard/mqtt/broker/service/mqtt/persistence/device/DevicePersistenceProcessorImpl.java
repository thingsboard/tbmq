/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.device;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.dao.client.device.DeviceSessionCtxService;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

@Slf4j
@Service
@RequiredArgsConstructor
public class DevicePersistenceProcessorImpl implements DevicePersistenceProcessor {

    private final DeviceMsgService deviceMsgService;
    private final DeviceSessionCtxService deviceSessionCtxService;
    private final DeviceActorManager deviceActorManager;

    @Override
    public void clearPersistedMsgs(String clientId) {
        // TODO: think about moving this code (could do async but delete only if msg.time < currentTime)
        deviceMsgService.removePersistedMessages(clientId);
        deviceSessionCtxService.removeDeviceSessionContext(clientId);
    }

    @Override
    public void acknowledgeDelivery(String clientId, int packetId) {
        deviceActorManager.notifyPacketAcknowledged(clientId, packetId);
    }

    @Override
    public void startProcessingPersistedMessages(ClientSessionCtx clientSessionCtx) {
        deviceActorManager.notifyClientConnected(clientSessionCtx);
    }

    @Override
    public void stopProcessingPersistedMessages(String clientId) {
        deviceActorManager.notifyClientDisconnected(clientId);
    }

}
