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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.device;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.TbActorRef;
import org.thingsboard.mqtt.broker.actors.TbActorSystem;
import org.thingsboard.mqtt.broker.actors.TbTypeActorId;
import org.thingsboard.mqtt.broker.actors.config.ActorSystemLifecycle;
import org.thingsboard.mqtt.broker.actors.device.PersistedDeviceActorCreator;
import org.thingsboard.mqtt.broker.actors.device.messages.ChannelNonWritableEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.ChannelWritableEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.DeviceConnectedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.DeviceDisconnectedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.IncomingPublishMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketAcknowledgedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketCompletedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketReceivedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketReceivedNoDeliveryEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.RemovePersistedMessagesEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.SharedSubscriptionEventMsg;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.id.ActorType;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceActorManagerImpl implements DeviceActorManager {

    private final @Lazy ActorSystemContext actorSystemContext;
    private final TbActorSystem actorSystem;

    @Override
    public void notifyClientConnected(ClientSessionCtx clientSessionCtx) {
        String clientId = clientSessionCtx.getClientId();
        TbActorRef deviceActorRef = getActorByClientId(clientId);
        if (deviceActorRef == null) {
            deviceActorRef = actorSystem.createRootActor(ActorSystemLifecycle.PERSISTED_DEVICE_DISPATCHER_NAME,
                    new PersistedDeviceActorCreator(actorSystemContext, clientId));
        }
        deviceActorRef.tellWithHighPriority(new DeviceConnectedEventMsg(clientSessionCtx));
    }

    @Override
    public void notifySubscribeToSharedSubscriptions(ClientSessionCtx clientSessionCtx, Set<TopicSharedSubscription> subscriptions) {
        String clientId = clientSessionCtx.getClientId();
        TbActorRef deviceActorRef = getActorByClientId(clientId);
        if (deviceActorRef == null) {
            log.debug("[{}] Cannot find device actor to process shared subscription for received event, skipping.", clientId);
        } else {
            deviceActorRef.tellWithHighPriority(new SharedSubscriptionEventMsg(subscriptions));
        }
    }

    @Override
    public void notifyClientDisconnected(String clientId) {
        TbActorRef deviceActorRef = getActorByClientId(clientId);
        if (deviceActorRef == null) {
            log.debug("[{}] Cannot find device actor to be stopped for received disconnect event, skipping.", clientId);
        } else {
            deviceActorRef.tellWithHighPriority(new DeviceDisconnectedEventMsg());
        }
    }

    @Override
    public void notifyRemovePersistedMessages(String clientId) {
        TbActorRef deviceActorRef = getActorByClientId(clientId);
        if (deviceActorRef == null) {
            deviceActorRef = actorSystem.createRootActor(ActorSystemLifecycle.PERSISTED_DEVICE_DISPATCHER_NAME,
                    new PersistedDeviceActorCreator(actorSystemContext, clientId));
        }
        deviceActorRef.tellWithHighPriority(RemovePersistedMessagesEventMsg.DEFAULT);
    }

    @Override
    public void notifyPublishMsg(String clientId, DevicePublishMsg devicePublishMsg) {
        TbActorRef deviceActorRef = getActorByClientId(clientId);
        if (deviceActorRef == null) {
            log.trace("[{}] No active actor for device.", clientId);
        } else {
            deviceActorRef.tell(new IncomingPublishMsg(devicePublishMsg));
        }
    }

    @Override
    public void notifyPacketAcknowledged(String clientId, int packetId) {
        TbActorRef deviceActorRef = getActorByClientId(clientId);
        if (deviceActorRef == null) {
            log.warn("[{}] Cannot find device actor for packet acknowledge event, packetId - {}.", clientId, packetId);
        } else {
            deviceActorRef.tell(new PacketAcknowledgedEventMsg(packetId));
        }
    }

    @Override
    public void notifyPacketReceived(String clientId, int packetId) {
        TbActorRef deviceActorRef = getActorByClientId(clientId);
        if (deviceActorRef == null) {
            log.warn("[{}] Cannot find device actor for packet received event, packetId - {}.", clientId, packetId);
        } else {
            deviceActorRef.tell(new PacketReceivedEventMsg(packetId));
        }
    }

    @Override
    public void notifyPacketReceivedNoDelivery(String clientId, int packetId) {
        TbActorRef deviceActorRef = getActorByClientId(clientId);
        if (deviceActorRef == null) {
            log.warn("[{}] Cannot find device actor for packet received no delivery event, packetId - {}.", clientId, packetId);
        } else {
            deviceActorRef.tell(new PacketReceivedNoDeliveryEventMsg(packetId));
        }
    }

    @Override
    public void notifyPacketCompleted(String clientId, int packetId) {
        TbActorRef deviceActorRef = getActorByClientId(clientId);
        if (deviceActorRef == null) {
            log.warn("[{}] Cannot find device actor for packet completed event, packetId - {}.", clientId, packetId);
        } else {
            deviceActorRef.tell(new PacketCompletedEventMsg(packetId));
        }
    }

    @Override
    public void notifyChannelWritable(String clientId) {
        TbActorRef deviceActorRef = getActorByClientId(clientId);
        if (deviceActorRef == null) {
            log.warn("[{}] Cannot find device actor for channel writable event", clientId);
        } else {
            deviceActorRef.tellWithHighPriority(ChannelWritableEventMsg.INSTANCE);
        }
    }

    @Override
    public void notifyChannelNonWritable(String clientId) {
        TbActorRef deviceActorRef = getActorByClientId(clientId);
        if (deviceActorRef == null) {
            log.warn("[{}] Cannot find device actor for channel non-writable event", clientId);
        } else {
            deviceActorRef.tellWithHighPriority(ChannelNonWritableEventMsg.INSTANCE);
        }
    }

    private TbActorRef getActorByClientId(String clientId) {
        return actorSystem.getActor(new TbTypeActorId(ActorType.PERSISTED_DEVICE, clientId));
    }
}
