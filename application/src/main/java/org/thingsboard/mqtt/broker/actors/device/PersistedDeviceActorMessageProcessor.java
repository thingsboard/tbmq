/**
 * Copyright © 2016-2022 The Thingsboard Authors
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

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.TbActorCtx;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.DeviceConnectedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.IncomingPublishMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketAcknowledgedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketCompletedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketReceivedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.StopDeviceActorCommandMsg;
import org.thingsboard.mqtt.broker.actors.shared.AbstractContextAwareMsgProcessor;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
class PersistedDeviceActorMessageProcessor extends AbstractContextAwareMsgProcessor {

    private final DeviceMsgService deviceMsgService;
    private final PublishMsgDeliveryService publishMsgDeliveryService;
    private final ClientMqttActorManager clientMqttActorManager;
    private final ClientLogger clientLogger;
    private final DeviceActorConfiguration deviceActorConfig;

    private final String clientId;

    // works only if Actor wasn't deleted
    private final Set<Integer> inFlightPacketIds = Sets.newConcurrentHashSet();
    private volatile ClientSessionCtx sessionCtx;
    private volatile long lastPersistedMsgSentSerialNumber = -1L;
    private volatile boolean processedAnyMsg = false;
    private volatile UUID stopActorCommandUUID;

    PersistedDeviceActorMessageProcessor(ActorSystemContext systemContext, String clientId) {
        super(systemContext);
        this.clientId = clientId;
        this.deviceMsgService = systemContext.getDeviceMsgService();
        this.clientLogger = systemContext.getClientLogger();
        this.publishMsgDeliveryService = systemContext.getPublishMsgDeliveryService();
        this.clientMqttActorManager = systemContext.getClientMqttActorManager();
        this.deviceActorConfig = systemContext.getDeviceActorConfiguration();
    }

    public void processDeviceConnect(DeviceConnectedEventMsg msg) {
        this.sessionCtx = msg.getSessionCtx();
        this.stopActorCommandUUID = null;
        List<DevicePublishMsg> persistedMessages = deviceMsgService.findPersistedMessages(clientId);
        try {
            persistedMessages.forEach(this::deliverPersistedMsg);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to process persisted messages. Exception - {}, reason - {}", clientId,
                    sessionCtx.getSessionId(), e.getClass().getSimpleName(), e.getMessage());
            log.trace("Detailed error: ", e);
            disconnect("Failed to process persisted messages");
        }
    }

    private void deliverPersistedMsg(DevicePublishMsg persistedMessage) {
        switch (persistedMessage.getPacketType()) {
            case PUBLISH:
                // TODO: guaranty that DUP flag is correctly set even if Device Actor is dropped
                boolean isDup = inFlightPacketIds.contains(persistedMessage.getPacketId());
                if (!isDup) {
                    inFlightPacketIds.add(persistedMessage.getPacketId());
                }
                lastPersistedMsgSentSerialNumber = persistedMessage.getSerialNumber();
                PublishMsg pubMsg = getPublishMsg(persistedMessage, isDup);
                publishMsgDeliveryService.sendPublishMsgToClient(sessionCtx, pubMsg);
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

        checkForMissedMsgsAndProcessBeforeFirstIncomingMsg(publishMsg);
        processedAnyMsg = true;

        inFlightPacketIds.add(publishMsg.getPacketId());
        try {
            PublishMsg pubMsg = getPublishMsg(publishMsg, false);
            publishMsgDeliveryService.sendPublishMsgToClient(sessionCtx, pubMsg);
            clientLogger.logEvent(clientId, this.getClass(), "Delivered msg to device client");
        } catch (Exception e) {
            log.warn("[{}] Failed to send PUBLISH msg", clientId, e);
            disconnect("Failed to send PUBLISH msg");
        }
    }

    private void checkForMissedMsgsAndProcessBeforeFirstIncomingMsg(DevicePublishMsg publishMsg) {
        if (!processedAnyMsg && publishMsg.getSerialNumber() > lastPersistedMsgSentSerialNumber + 1) {
            long nextPersistedSerialNumber = lastPersistedMsgSentSerialNumber + 1;
            log.debug("[{}] Sending not processed persisted messages, 'from' serial number - {}, 'to' serial number - {}",
                    clientId, nextPersistedSerialNumber, publishMsg.getSerialNumber());

            List<DevicePublishMsg> persistedMessages = findMissedPersistedMsgs(publishMsg, nextPersistedSerialNumber);
            try {
                persistedMessages.forEach(this::deliverPersistedMsg);
            } catch (Exception e) {
                log.warn("[{}] Failed to process missed persisted messages", clientId, e);
                disconnect("Failed to process missed persisted messages");
            }
        }
    }

    private List<DevicePublishMsg> findMissedPersistedMsgs(DevicePublishMsg publishMsg, long nextPersistedSerialNumber) {
        return deviceMsgService.findPersistedMessages(clientId, nextPersistedSerialNumber, publishMsg.getSerialNumber());
    }

    private void disconnect(String message) {
        clientMqttActorManager.disconnect(clientId, new MqttDisconnectMsg(
                sessionCtx.getSessionId(),
                new DisconnectReason(
                        DisconnectReasonType.ON_ERROR, message)));
    }

    private PublishMsg getPublishMsg(DevicePublishMsg publishMsg, boolean isDup) {
        return PublishMsg.builder()
                .packetId(publishMsg.getPacketId())
                .topicName(publishMsg.getTopic())
                .payload(publishMsg.getPayload())
                .qosLevel(publishMsg.getQos())
                .isDup(isDup)
                .properties(publishMsg.getProperties())
                .isRetained(publishMsg.isRetained())
                .build();
    }

    public void processPacketAcknowledge(PacketAcknowledgedEventMsg msg) {
        ListenableFuture<Void> future = deviceMsgService.tryRemovePersistedMessage(clientId, msg.getPacketId());
        future.addListener(() -> {
            try {
                inFlightPacketIds.remove(msg.getPacketId());
            } catch (Exception e) {
                log.warn("[{}] Failed to process packet acknowledge, packetId - {}, exception - {}, reason - {}",
                        clientId, msg.getPacketId(), e.getClass().getSimpleName(), e.getMessage());
            }
        }, MoreExecutors.directExecutor());
    }

    public void processPacketReceived(PacketReceivedEventMsg msg) {
        ListenableFuture<Void> future = deviceMsgService.tryUpdatePacketReceived(clientId, msg.getPacketId());
        future.addListener(() -> {
            try {
                inFlightPacketIds.remove(msg.getPacketId());
                if (sessionCtx != null) {
                    publishMsgDeliveryService.sendPubRelMsgToClient(sessionCtx, msg.getPacketId());
                }
            } catch (Exception e) {
                log.warn("[{}] Failed to process packet received, packetId - {}, exception - {}, reason - {}",
                        clientId, msg.getPacketId(), e.getClass().getSimpleName(), e.getMessage());
            }
        }, MoreExecutors.directExecutor());
    }

    public void processPacketComplete(PacketCompletedEventMsg msg) {
        ListenableFuture<Void> resultFuture = deviceMsgService.tryRemovePersistedMessage(clientId, msg.getPacketId());
        DonAsynchron.withCallback(
                resultFuture,
                unused -> log.debug("[{}] Removed persisted msg {} from the DB", clientId, msg.getPacketId()),
                throwable -> log.warn("[{}] Failed to remove persisted msg {} from the DB", clientId, msg.getPacketId(), throwable)
        );
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
