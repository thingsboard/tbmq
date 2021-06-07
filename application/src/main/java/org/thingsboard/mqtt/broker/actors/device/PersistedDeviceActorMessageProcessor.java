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

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.TbActorCtx;
import org.thingsboard.mqtt.broker.actors.device.messages.DeviceConnectedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.IncomingPublishMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketAcknowledgedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketCompletedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketReceivedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.StopDeviceActorCommandMsg;
import org.thingsboard.mqtt.broker.actors.shared.AbstractContextAwareMsgProcessor;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
class PersistedDeviceActorMessageProcessor extends AbstractContextAwareMsgProcessor {

    private final DeviceMsgService deviceMsgService;
    private final PublishMsgDeliveryService publishMsgDeliveryService;
    private final ClientMqttActorManager clientMqttActorManager;
    private final DeviceActorConfiguration deviceActorConfig;

    private final String clientId;

    // works only if Actor wasn't deleted
    private final Set<Integer> inFlightPacketIds = new HashSet<>();
    private ClientSessionCtx sessionCtx;
    private Long lastPersistedMsgSentSerialNumber = 0L;
    private UUID stopActorCommandUUID;

    PersistedDeviceActorMessageProcessor(ActorSystemContext systemContext, String clientId) {
        super(systemContext);
        this.clientId = clientId;
        this.deviceMsgService = systemContext.getDeviceMsgService();
        this.publishMsgDeliveryService = systemContext.getPublishMsgDeliveryService();
        this.clientMqttActorManager = systemContext.getClientMqttActorManager();
        this.deviceActorConfig = systemContext.getDeviceActorConfiguration();
    }

    public void processDeviceConnect(DeviceConnectedEventMsg msg) {
        this.sessionCtx = msg.getSessionCtx();
        this.stopActorCommandUUID = null;
        List<DevicePublishMsg> persistedMessages = deviceMsgService.findPersistedMessages(clientId);
        try {
            for (DevicePublishMsg persistedMessage : persistedMessages) {
                processPersistedMsg(persistedMessage);
            }
        } catch (Exception e) {
            clientMqttActorManager.disconnect(clientId, sessionCtx.getSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR, "Failed to process persisted messages"));
        }
    }

    private void processPersistedMsg(DevicePublishMsg persistedMessage) {
        switch (persistedMessage.getPacketType()) {
            case PUBLISH:
                // TODO: guaranty that DUP flag is correctly set even if Device Actor is dropped
                boolean isDup = inFlightPacketIds.contains(persistedMessage.getPacketId());
                if (!isDup) {
                    inFlightPacketIds.add(persistedMessage.getPacketId());
                }
                lastPersistedMsgSentSerialNumber = persistedMessage.getSerialNumber();
                publishMsgDeliveryService.sendPublishMsgToClient(sessionCtx, persistedMessage.getPacketId(),
                        persistedMessage.getTopic(), persistedMessage.getQos(), isDup,
                        persistedMessage.getPayload());
                break;
            case PUBREL:
                publishMsgDeliveryService.sendPubRelMsgToClient(sessionCtx, persistedMessage.getPacketId());
                break;
            default:
                break;
        }

    }

    public void processDeviceDisconnect(TbActorCtx actorCtx) {
        this.sessionCtx = null;
        long delayMs = TimeUnit.MINUTES.toMillis(deviceActorConfig.getTimeToWaitBeforeActorStopMinutes());
        this.stopActorCommandUUID = UUID.randomUUID();
        systemContext.scheduleMsgWithDelay(actorCtx, new StopDeviceActorCommandMsg(stopActorCommandUUID), delayMs);
    }

    public void process(IncomingPublishMsg msg) {
        DevicePublishMsg publishMsg = msg.getPublishMsg();
        if (publishMsg.getSerialNumber() <= lastPersistedMsgSentSerialNumber) {
            log.trace("[{}] Message was already sent to client, ignoring message {}.", clientId, publishMsg.getSerialNumber());
            return;
        }
        inFlightPacketIds.add(publishMsg.getPacketId());
        try {
            publishMsgDeliveryService.sendPublishMsgToClient(sessionCtx, publishMsg.getPacketId(),
                    publishMsg.getTopic(), publishMsg.getQos(), false,
                    publishMsg.getPayload());
        } catch (Exception e) {
            clientMqttActorManager.disconnect(clientId, sessionCtx.getSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR, "Failed to send PUBLISH msg"));
        }
    }

    public void processPacketAcknowledge(PacketAcknowledgedEventMsg msg) {
        deviceMsgService.removePersistedMessage(clientId, msg.getPacketId());
        inFlightPacketIds.remove(msg.getPacketId());
    }

    public void processPacketReceived(PacketReceivedEventMsg msg) {
        deviceMsgService.updatePacketReceived(clientId, msg.getPacketId());
        inFlightPacketIds.remove(msg.getPacketId());
        publishMsgDeliveryService.sendPubRelMsgToClient(sessionCtx, msg.getPacketId());
    }

    public void processPacketComplete(PacketCompletedEventMsg msg) {
        deviceMsgService.removePersistedMessage(clientId, msg.getPacketId());
    }

    public void processActorStop(TbActorCtx ctx, StopDeviceActorCommandMsg msg) {
        if (msg.getCommandUUID().equals(stopActorCommandUUID)) {
            log.debug("[{}] Stopping DEVICE actor.", clientId);
            ctx.stop(ctx.getSelf());
        } else {
            log.debug("[{}] Device was reconnected, ignoring actor stop command.", clientId);
        }
    }
}
