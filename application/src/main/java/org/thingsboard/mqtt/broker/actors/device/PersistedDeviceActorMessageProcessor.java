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
package org.thingsboard.mqtt.broker.actors.device;

import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.TbActorCtx;
import org.thingsboard.mqtt.broker.actors.device.messages.DeviceConnectedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.IncomingPublishMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketAcknowledgedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.StopDeviceActorCommandMsg;
import org.thingsboard.mqtt.broker.actors.shared.AbstractContextAwareMsgProcessor;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.SessionState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
class PersistedDeviceActorMessageProcessor extends AbstractContextAwareMsgProcessor {

    private final DeviceMsgService deviceMsgService;
    private final PublishMsgDeliveryService publishMsgDeliveryService;
    private final DeviceActorConfiguration deviceActorConfig;

    private final String clientId;

    private final Set<Integer> inFlightPacketIds = new HashSet<>();
    private ClientSessionCtx sessionCtx;
    private Long lastSentSerialNumber = 0L;
    private UUID stopActorCommandUUID;

    PersistedDeviceActorMessageProcessor(ActorSystemContext systemContext, String clientId) {
        super(systemContext);
        this.clientId = clientId;
        this.deviceMsgService = systemContext.getDeviceMsgService();
        this.publishMsgDeliveryService = systemContext.getPublishMsgDeliveryService();
        this.deviceActorConfig = systemContext.getDeviceActorConfiguration();
    }

    public void processDeviceConnect(DeviceConnectedEventMsg msg) {
        this.sessionCtx = msg.getSessionCtx();
        this.stopActorCommandUUID = null;
        List<DevicePublishMsg> persistedMessages = deviceMsgService.findPersistedMessages(clientId);
        for (DevicePublishMsg persistedMessage : persistedMessages) {
            if (sessionCtx.getSessionState() != SessionState.CONNECTED) {
                log.debug("[{}] Client is already disconnected.", clientId);
                break;
            }
            processPersistedMsg(persistedMessage);
        }
    }

    private void processPersistedMsg(DevicePublishMsg persistedMessage) {
        // TODO: guaranty that DUP flag is correctly set even if Device Actor is dropped
        boolean isDup = inFlightPacketIds.contains(persistedMessage.getPacketId());
        if (!isDup) {
            inFlightPacketIds.add(persistedMessage.getPacketId());
        }
        lastSentSerialNumber = persistedMessage.getSerialNumber();
        publishMsgDeliveryService.sendPublishMsgToClient(sessionCtx, persistedMessage.getPacketId(),
                persistedMessage.getTopic(), MqttQoS.valueOf(persistedMessage.getQos()), isDup,
                persistedMessage.getPayload());
    }

    public void processDeviceDisconnect(TbActorCtx actorCtx) {
        this.sessionCtx = null;
        long delayMs = TimeUnit.MINUTES.toMillis(deviceActorConfig.getTimeToWaitBeforeActorStopMinutes());
        this.stopActorCommandUUID = UUID.randomUUID();
        systemContext.scheduleMsgWithDelay(actorCtx, new StopDeviceActorCommandMsg(stopActorCommandUUID), delayMs);
    }

    public void process(IncomingPublishMsg msg) {
        DevicePublishMsg publishMsg = msg.getPublishMsg();
        if (sessionCtx == null || sessionCtx.getSessionState() != SessionState.CONNECTED) {
            log.trace("[{}] Client is not connected, ignoring message {}.", clientId, publishMsg.getSerialNumber());
            return;
        }
        if (publishMsg.getSerialNumber() <= lastSentSerialNumber) {
            log.trace("[{}] Message was already sent to client, ignoring message {}.", clientId, publishMsg.getSerialNumber());
            return;
        }
        inFlightPacketIds.add(publishMsg.getPacketId());
        publishMsgDeliveryService.sendPublishMsgToClient(sessionCtx, publishMsg.getPacketId(),
                publishMsg.getTopic(), MqttQoS.valueOf(publishMsg.getQos()), false,
                publishMsg.getPayload());
    }

    public void processPacketAcknowledge(PacketAcknowledgedEventMsg msg) {
        deviceMsgService.removePersistedMessage(clientId, msg.getPacketId());
        inFlightPacketIds.remove(msg.getPacketId());
    }

    public void processActorStop(TbActorCtx ctx, StopDeviceActorCommandMsg msg) {
        if (msg.getCommandUUID().equals(stopActorCommandUUID)) {
            ctx.stop(ctx.getSelf());
        } else {
            log.debug("[{}] Device was reconnected, ignoring actor stop command.", clientId);
        }
    }
}
