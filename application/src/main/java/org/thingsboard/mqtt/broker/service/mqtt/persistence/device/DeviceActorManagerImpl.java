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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.TbActorRef;
import org.thingsboard.mqtt.broker.actors.TbActorSystem;
import org.thingsboard.mqtt.broker.actors.TbStringActorId;
import org.thingsboard.mqtt.broker.actors.config.ActorSystemLifecycle;
import org.thingsboard.mqtt.broker.actors.device.PersistedDeviceActorCreator;
import org.thingsboard.mqtt.broker.actors.device.messages.DeviceConnectedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.DeviceDisconnectedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.IncomingPublishMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketAcknowledgedEventMsg;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

@Slf4j
@Service
public class DeviceActorManagerImpl implements DeviceActorManager {
    private final ActorSystemContext actorSystemContext;
    private final TbActorSystem actorSystem;

    public DeviceActorManagerImpl(ActorSystemContext actorSystemContext) {
        this.actorSystemContext = actorSystemContext;
        this.actorSystem = actorSystemContext.getActorSystem();
    }

    @Override
    public void notifyClientConnected(ClientSessionCtx clientSessionCtx) {
        String clientId = clientSessionCtx.getClientId();
        TbActorRef deviceActorRef = actorSystem.getActor(new TbStringActorId(clientId));
        if (deviceActorRef == null) {
            deviceActorRef = actorSystem.createRootActor(ActorSystemLifecycle.PERSISTED_DEVICE_DISPATCHER_NAME,
                    new PersistedDeviceActorCreator(actorSystemContext, clientId));
        }
        // TODO: what if actor stops between 'getActor' and 'tell' methods?
        deviceActorRef.tellWithHighPriority(new DeviceConnectedEventMsg(clientSessionCtx));
    }

    @Override
    public void notifyClientDisconnected(String clientId) {
        TbActorRef deviceActorRef = actorSystem.getActor(new TbStringActorId(clientId));
        if (deviceActorRef == null) {
            log.warn("[{}] Cannot find device actor for disconnect event.", clientId);
        } else {
            deviceActorRef.tellWithHighPriority(new DeviceDisconnectedEventMsg());
        }
    }

    @Override
    public void notifyPacketAcknowledged(String clientId, int packetId) {
        TbActorRef deviceActorRef = actorSystem.getActor(new TbStringActorId(clientId));
        if (deviceActorRef == null) {
            log.warn("[{}] Cannot find device actor for packet acknowledge event, packetId - {}.", clientId, packetId);
        } else {
            deviceActorRef.tell(new PacketAcknowledgedEventMsg(packetId));
        }
    }

    @Override
    public void sendMsgToActor(DevicePublishMsg devicePublishMsg) {
        String clientId = devicePublishMsg.getClientId();
        TbActorRef deviceActorRef = actorSystem.getActor(new TbStringActorId(clientId));
        if (deviceActorRef == null) {
            log.trace("[{}] No actor for device.", clientId);
        } else {
            deviceActorRef.tell(new IncomingPublishMsg(devicePublishMsg));
        }
    }
}
