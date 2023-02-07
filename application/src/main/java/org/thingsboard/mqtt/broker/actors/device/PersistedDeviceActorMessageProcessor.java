/**
 * Copyright © 2016-2023 The Thingsboard Authors
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

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.TbActorCtx;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.DeviceConnectedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.IncomingPublishMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketAcknowledgedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketCompletedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketReceivedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.SharedSubscriptionEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.StopDeviceActorCommandMsg;
import org.thingsboard.mqtt.broker.actors.shared.AbstractContextAwareMsgProcessor;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;
import org.thingsboard.mqtt.broker.dao.client.device.DevicePacketIdAndSerialNumberService;
import org.thingsboard.mqtt.broker.dao.client.device.DeviceSessionCtxService;
import org.thingsboard.mqtt.broker.dao.client.device.PacketIdAndSerialNumber;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.dto.PacketIdAndSerialNumberDto;
import org.thingsboard.mqtt.broker.dto.SharedSubscriptionPublishPacket;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
class PersistedDeviceActorMessageProcessor extends AbstractContextAwareMsgProcessor {

    private final String clientId;
    private final DeviceMsgService deviceMsgService;
    private final DeviceSessionCtxService deviceSessionCtxService;
    private final DevicePacketIdAndSerialNumberService serialNumberService;
    private final PublishMsgDeliveryService publishMsgDeliveryService;
    private final ClientMqttActorManager clientMqttActorManager;
    private final ClientLogger clientLogger;
    private final DeviceActorConfiguration deviceActorConfig;

    private final Set<Integer> inFlightPacketIds = Sets.newConcurrentHashSet();
    private final Map<Integer, SharedSubscriptionPublishPacket> sentPacketIdsFromSharedSubscription = Maps.newConcurrentMap();

    private volatile ClientSessionCtx sessionCtx;
    private volatile long lastPersistedMsgSentSerialNumber = -1L;
    private volatile boolean processedAnyMsg = false;
    private volatile UUID stopActorCommandUUID;

    PersistedDeviceActorMessageProcessor(ActorSystemContext systemContext, String clientId) {
        super(systemContext);
        this.clientId = clientId;
        this.deviceMsgService = systemContext.getDeviceMsgService();
        this.deviceSessionCtxService = systemContext.getDeviceSessionCtxService();
        this.serialNumberService = systemContext.getSerialNumberService();
        this.publishMsgDeliveryService = systemContext.getPublishMsgDeliveryService();
        this.clientMqttActorManager = systemContext.getClientMqttActorManager();
        this.clientLogger = systemContext.getClientLogger();
        this.deviceActorConfig = systemContext.getDeviceActorConfiguration();
    }

    public void processDeviceConnect(DeviceConnectedEventMsg msg) {
        this.sessionCtx = msg.getSessionCtx();
        this.stopActorCommandUUID = null;
        List<DevicePublishMsg> persistedMessages = deviceMsgService.findPersistedMessages(clientId);
        try {
            persistedMessages.forEach(this::deliverPersistedMsg);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to process persisted messages.", clientId, sessionCtx.getSessionId(), e);
            disconnect("Failed to process persisted messages");
        }
    }

    public void processingSharedSubscriptions(SharedSubscriptionEventMsg msg) {
        PacketIdAndSerialNumber lastPacketIdAndSerialNumber = getLastPacketIdAndSerialNumber();

        for (TopicSharedSubscription topicSharedSubscription : msg.getSubscriptions()) {
            String key = topicSharedSubscription.getKey();
            List<DevicePublishMsg> persistedMessages = deviceMsgService.findPersistedMessages(key);
            if (CollectionUtils.isEmpty(persistedMessages)) {
                continue;
            }

            updateMessagesBeforePublish(lastPacketIdAndSerialNumber, topicSharedSubscription, persistedMessages);

            try {
                persistedMessages.forEach(this::deliverPersistedMsg);
            } catch (Exception e) {
                log.warn("[{}][{}] Failed to process shared subscription persisted messages.", clientId, sessionCtx.getSessionId(), e);
                disconnect("Failed to process shared subscription persisted messages");
            }
            deviceSessionCtxService.removeDeviceSessionContext(key);
        }
        serialNumberService.saveLastSerialNumbers(Map.of(clientId, lastPacketIdAndSerialNumber));
    }

    private void updateMessagesBeforePublish(PacketIdAndSerialNumber lastPacketIdAndSerialNumber, TopicSharedSubscription topicSharedSubscription,
                                             List<DevicePublishMsg> persistedMessages) {
        for (DevicePublishMsg devicePublishMessage : persistedMessages) {
            PacketIdAndSerialNumberDto packetIdAndSerialNumberDto = getAndIncrementPacketIdAndSerialNumber(lastPacketIdAndSerialNumber);

            sentPacketIdsFromSharedSubscription.put(packetIdAndSerialNumberDto.getPacketId(),
                    newSharedSubscriptionPublishPacket(topicSharedSubscription.getKey(), devicePublishMessage.getPacketId()));

            devicePublishMessage.setPacketId(packetIdAndSerialNumberDto.getPacketId());
            devicePublishMessage.setSerialNumber(packetIdAndSerialNumberDto.getSerialNumber());
            devicePublishMessage.setQos(getMinQoSValue(topicSharedSubscription, devicePublishMessage));
        }
    }

    private int getMinQoSValue(TopicSharedSubscription topicSharedSubscription, DevicePublishMsg devicePublishMessage) {
        return Math.min(topicSharedSubscription.getQos(), devicePublishMessage.getQos());
    }

    private PacketIdAndSerialNumber getLastPacketIdAndSerialNumber() {
        try {
            return serialNumberService.getLastPacketIdAndSerialNumber(Set.of(clientId)).getOrDefault(clientId, newPacketIdAndSerialNumber());
        } catch (Exception e) {
            log.warn("[{}] Cannot get last packetId and serialNumbers", clientId, e);
            return newPacketIdAndSerialNumber();
        }
    }

    private PacketIdAndSerialNumber newPacketIdAndSerialNumber() {
        return new PacketIdAndSerialNumber(new AtomicInteger(0), new AtomicLong(-1));
    }

    private PacketIdAndSerialNumberDto getAndIncrementPacketIdAndSerialNumber(PacketIdAndSerialNumber packetIdAndSerialNumber) {
        AtomicInteger packetIdAtomic = packetIdAndSerialNumber.getPacketId();
        packetIdAtomic.incrementAndGet();
        packetIdAtomic.compareAndSet(0xffff, 1);
        return new PacketIdAndSerialNumberDto(packetIdAtomic.get(), packetIdAndSerialNumber.getSerialNumber().incrementAndGet());
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
            if (log.isTraceEnabled()) {
                log.trace("[{}] Message was already sent to client, ignoring message {}.", clientId, publishMsg.getSerialNumber());
            }
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
            if (log.isDebugEnabled()) {
                log.debug("[{}] Sending not processed persisted messages, 'from' serial number - {}, 'to' serial number - {}",
                        clientId, nextPersistedSerialNumber, publishMsg.getSerialNumber());
            }

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
        SharedSubscriptionPublishPacket packet = getSharedSubscriptionPublishPacket(msg.getPacketId());
        var targetClientId = packet.getKey();

        ListenableFuture<Void> future = deviceMsgService.tryRemovePersistedMessage(targetClientId, packet.getPacketId());
        future.addListener(() -> {
            try {
                inFlightPacketIds.remove(msg.getPacketId());
            } catch (Exception e) {
                log.warn("[{}] Failed to process packet acknowledge, packetId - {}, exception - {}, reason - {}",
                        targetClientId, msg.getPacketId(), e.getClass().getSimpleName(), e);
            }
        }, MoreExecutors.directExecutor());
    }

    public void processPacketReceived(PacketReceivedEventMsg msg) {
        SharedSubscriptionPublishPacket packet = getSharedSubscriptionPublishPacket(msg.getPacketId());
        var targetClientId = packet.getKey();

        ListenableFuture<Void> future = deviceMsgService.tryUpdatePacketReceived(targetClientId, packet.getPacketId());
        future.addListener(() -> {
            try {
                inFlightPacketIds.remove(msg.getPacketId());
                if (sessionCtx != null) {
                    publishMsgDeliveryService.sendPubRelMsgToClient(sessionCtx, msg.getPacketId());
                }
            } catch (Exception e) {
                log.warn("[{}] Failed to process packet received, packetId - {}, exception - {}, reason - {}",
                        targetClientId, msg.getPacketId(), e.getClass().getSimpleName(), e);
            }
        }, MoreExecutors.directExecutor());
    }

    public void processPacketComplete(PacketCompletedEventMsg msg) {
        SharedSubscriptionPublishPacket packet = getSharedSubscriptionPublishPacket(msg.getPacketId());
        var targetClientId = packet.getKey();

        ListenableFuture<Void> resultFuture = deviceMsgService.tryRemovePersistedMessage(targetClientId, packet.getPacketId());
        DonAsynchron.withCallback(
                resultFuture,
                unused -> {
                    if (log.isDebugEnabled()) {
                        log.debug("[{}] Removed persisted msg {} from the DB", targetClientId, msg.getPacketId());
                    }
                },
                throwable -> log.warn("[{}] Failed to remove persisted msg {} from the DB", targetClientId, msg.getPacketId(), throwable)
        );
    }

    private SharedSubscriptionPublishPacket getSharedSubscriptionPublishPacket(int packetId) {
        return sentPacketIdsFromSharedSubscription.getOrDefault(packetId, newSharedSubscriptionPublishPacket(clientId, packetId));
    }

    private SharedSubscriptionPublishPacket newSharedSubscriptionPublishPacket(String clientId, int packetId) {
        return new SharedSubscriptionPublishPacket(clientId, packetId);
    }

    public void processActorStop(TbActorCtx ctx, StopDeviceActorCommandMsg msg) {
        if (msg.getCommandUUID().equals(stopActorCommandUUID)) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] Stopping DEVICE actor.", clientId);
            }
            ctx.stop(ctx.getSelf());
        } else {
            if (log.isDebugEnabled()) {
                log.debug("[{}] Device was reconnected, ignoring actor stop command.", clientId);
            }
        }
    }
}
