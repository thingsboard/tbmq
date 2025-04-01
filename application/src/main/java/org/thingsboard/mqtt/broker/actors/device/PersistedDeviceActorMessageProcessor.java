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
package org.thingsboard.mqtt.broker.actors.device;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
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
import org.thingsboard.mqtt.broker.actors.device.messages.PacketReceivedNoDeliveryEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.SharedSubscriptionEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.StopDeviceActorCommandMsg;
import org.thingsboard.mqtt.broker.actors.shared.AbstractContextAwareMsgProcessor;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.mqtt.MsgExpiryResult;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.dto.PacketIdDto;
import org.thingsboard.mqtt.broker.dto.SharedSubscriptionPublishPacket;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionCacheService;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;
import org.thingsboard.mqtt.broker.util.MqttQosUtil;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Getter
class PersistedDeviceActorMessageProcessor extends AbstractContextAwareMsgProcessor {

    private final String clientId;
    private final DeviceMsgService deviceMsgService;
    private final PublishMsgDeliveryService publishMsgDeliveryService;
    private final ClientMqttActorManager clientMqttActorManager;
    private final ClientLogger clientLogger;
    private final DeviceActorConfiguration deviceActorConfig;
    private final SharedSubscriptionCacheService sharedSubscriptionCacheService;

    private final Set<Integer> inFlightPacketIds = Sets.newConcurrentHashSet();
    private final ConcurrentMap<Integer, SharedSubscriptionPublishPacket> sentPacketIdsFromSharedSubscription = Maps.newConcurrentMap();

    @Setter
    private volatile ClientSessionCtx sessionCtx;
    private volatile UUID stopActorCommandUUID;
    @Setter
    private volatile boolean channelWritable = true;

    PersistedDeviceActorMessageProcessor(ActorSystemContext systemContext, String clientId) {
        super(systemContext);
        this.clientId = clientId;
        this.deviceMsgService = systemContext.getDeviceMsgService();
        this.publishMsgDeliveryService = systemContext.getPublishMsgDeliveryService();
        this.clientMqttActorManager = systemContext.getClientMqttActorManager();
        this.clientLogger = systemContext.getClientLogger();
        this.deviceActorConfig = systemContext.getDeviceActorConfiguration();
        this.sharedSubscriptionCacheService = systemContext.getSharedSubscriptionCacheService();
    }

    public void processDeviceConnect(DeviceConnectedEventMsg msg) {
        log.trace("[{}] Start processing persisted messages on Device connect", msg.getSessionCtx().getClientId());
        this.sessionCtx = msg.getSessionCtx();
        this.stopActorCommandUUID = null;
        findAndDeliverPersistedMessages();
    }

    // TODO: refactor to not use .toCompletableFuture().get(); for deviceMsgService APIs
    @SneakyThrows
    public void processingSharedSubscriptions(SharedSubscriptionEventMsg msg) {
        log.trace("[{}] Start processing Device shared subscriptions", msg.getSubscriptions());
        if (CollectionUtils.isEmpty(msg.getSubscriptions())) {
            return;
        }

        int lastPacketId = deviceMsgService.getLastPacketId(clientId).toCompletableFuture().get();

        for (TopicSharedSubscription topicSharedSubscription : msg.getSubscriptions()) {
            boolean anyDeviceClientConnected = sharedSubscriptionCacheService.isAnyOtherDeviceClientConnected(clientId, topicSharedSubscription);
            if (anyDeviceClientConnected) {
                continue;
            }
            // It means there was at least one persistent offline subscriber and published message with QoS > 0.
            // If Subscriber QoS is 0 - publish messages QoS is downgraded to 0, so we can not acknowledge such messages from DB.
            // They can be received by another subscriber with QoS > 0 or will be deleted by TTL.
            if (topicSharedSubscription.getQos() == 0) {
                continue;
            }
            String key = topicSharedSubscription.getKey();
            List<DevicePublishMsg> persistedMessages = deviceMsgService.findPersistedMessages(key).toCompletableFuture().get();
            if (CollectionUtils.isEmpty(persistedMessages)) {
                continue;
            }

            lastPacketId = updateMessagesBeforePublishAndReturnLastPacketId(lastPacketId, topicSharedSubscription, persistedMessages);

            try {
                persistedMessages.forEach(this::deliverPersistedMsg);
            } catch (Exception e) {
                log.warn("[{}][{}] Failed to process shared subscription persisted messages.", clientId, sessionCtx.getSessionId(), e);
                disconnect("Failed to process shared subscription persisted messages");
            }
            deviceMsgService.removeLastPacketId(key).toCompletableFuture().get();
        }
        deviceMsgService.saveLastPacketId(clientId, lastPacketId).toCompletableFuture().get();
    }

    int updateMessagesBeforePublishAndReturnLastPacketId(int lastPacketId, TopicSharedSubscription topicSharedSubscription,
                                                         List<DevicePublishMsg> persistedMessages) {
        var packetIdDto = new PacketIdDto(lastPacketId);
        for (DevicePublishMsg devicePublishMessage : persistedMessages) {
            int currentPacketId = packetIdDto.getNextPacketId();
            sentPacketIdsFromSharedSubscription.put(currentPacketId,
                    newSharedSubscriptionPublishPacket(topicSharedSubscription.getKey(), devicePublishMessage.getPacketId()));
            devicePublishMessage.setPacketId(currentPacketId);
            devicePublishMessage.setQos(MqttQosUtil.downgradeQos(topicSharedSubscription, devicePublishMessage));
            MqttPropertiesUtil.addSubscriptionIdToProps(devicePublishMessage.getProperties(), topicSharedSubscription.getSubscriptionId());
        }
        return packetIdDto.getCurrentPacketId();
    }

    void deliverPersistedMsg(DevicePublishMsg persistedMessage) {
        switch (persistedMessage.getPacketType()) {
            case PUBLISH:
                MsgExpiryResult msgExpiryResult = MqttPropertiesUtil.getMsgExpiryResult(persistedMessage, System.currentTimeMillis());
                if (msgExpiryResult.isExpired()) {
                    return;
                }
                // TODO: guaranty that DUP flag is correctly set even if Device Actor is dropped
                boolean isDup = inFlightPacketIds.contains(persistedMessage.getPacketId());
                if (!isDup) {
                    inFlightPacketIds.add(persistedMessage.getPacketId());
                }
                if (msgExpiryResult.isMsgExpiryIntervalPresent()) {
                    MqttPropertiesUtil.addMsgExpiryIntervalToProps(persistedMessage.getProperties(), msgExpiryResult.getMsgExpiryInterval());
                }
                publishMsgDeliveryService.sendPublishMsgToClient(sessionCtx, persistedMessage, isDup);
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
        long delayMs = TimeUnit.MINUTES.toMillis(deviceActorConfig.getWaitBeforeActorStopMinutes());
        this.stopActorCommandUUID = UUID.randomUUID();
        systemContext.scheduleMsgWithDelay(actorCtx, new StopDeviceActorCommandMsg(stopActorCommandUUID), delayMs);
    }

    public void process(IncomingPublishMsg msg) {
        if (!channelWritable) {
            log.debug("[{}] Channel non-writable, skipping message delivery {}", clientId, msg);
            return;
        }
        DevicePublishMsg publishMsg = msg.getPublishMsg();
        MsgExpiryResult msgExpiryResult = MqttPropertiesUtil.getMsgExpiryResult(publishMsg, System.currentTimeMillis());
        if (msgExpiryResult.isExpired()) {
            return;
        }

        inFlightPacketIds.add(publishMsg.getPacketId());
        try {
            if (msgExpiryResult.isMsgExpiryIntervalPresent()) {
                MqttPropertiesUtil.addMsgExpiryIntervalToProps(publishMsg.getProperties(), msgExpiryResult.getMsgExpiryInterval());
            }
            publishMsgDeliveryService.sendPublishMsgToClient(sessionCtx, publishMsg, false);
            clientLogger.logEvent(clientId, this.getClass(), "Delivered msg to device client");
        } catch (Exception e) {
            log.warn("[{}] Failed to send PUBLISH msg", clientId, e);
            if (sessionCtx != null) disconnect("Failed to send PUBLISH msg");
        }
    }

    private void disconnect(String message) {
        clientMqttActorManager.disconnect(clientId, new MqttDisconnectMsg(
                sessionCtx.getSessionId(),
                new DisconnectReason(
                        DisconnectReasonType.ON_ERROR, message)));
    }

    public void processPacketAcknowledge(PacketAcknowledgedEventMsg msg) {
        SharedSubscriptionPublishPacket packet = getSharedSubscriptionPublishPacket(msg.getPacketId());
        var targetClientId = getTargetClientId(packet);
        deviceMsgService.removePersistedMessage(targetClientId, getTargetPacketId(packet, msg.getPacketId()))
                .whenComplete((__, throwable) -> {
                    if (throwable != null) {
                        log.warn("[{}] Failed to process packet acknowledge, packetId - {}", targetClientId, msg.getPacketId(), throwable);
                        return;
                    }
                    inFlightPacketIds.remove(msg.getPacketId());
                });
    }

    public void processPacketReceived(PacketReceivedEventMsg msg) {
        SharedSubscriptionPublishPacket packet = getSharedSubscriptionPublishPacket(msg.getPacketId());
        var targetClientId = getTargetClientId(packet);
        deviceMsgService.updatePacketReceived(targetClientId, getTargetPacketId(packet, msg.getPacketId())).whenComplete((__, throwable) -> {
            if (throwable != null) {
                log.warn("[{}] Failed to process packet received, packetId - {}", targetClientId, msg.getPacketId(), throwable);
                return;
            }
            inFlightPacketIds.remove(msg.getPacketId());
            if (sessionCtx != null) {
                publishMsgDeliveryService.sendPubRelMsgToClient(sessionCtx, msg.getPacketId());
            }
        });
    }

    public void processPacketReceivedNoDelivery(PacketReceivedNoDeliveryEventMsg msg) {
        SharedSubscriptionPublishPacket packet = getSharedSubscriptionPublishPacket(msg.getPacketId());
        var targetClientId = getTargetClientId(packet);
        deviceMsgService.removePersistedMessage(targetClientId, getTargetPacketId(packet, msg.getPacketId()))
                .whenComplete((__, throwable) -> {
                    if (throwable != null) {
                        log.warn("[{}] Failed to process packet received no delivery, packetId - {}", targetClientId, msg.getPacketId(), throwable);
                        return;
                    }
                    inFlightPacketIds.remove(msg.getPacketId());
                });
    }

    public void processPacketComplete(PacketCompletedEventMsg msg) {
        SharedSubscriptionPublishPacket packet = getSharedSubscriptionPublishPacket(msg.getPacketId());
        var targetClientId = getTargetClientId(packet);
        deviceMsgService.removePersistedMessage(targetClientId, getTargetPacketId(packet, msg.getPacketId()))
                .whenComplete((__, throwable) -> {
                    if (throwable != null) {
                        log.warn("[{}] Failed to remove persisted msg {} from the DB", targetClientId, msg.getPacketId(), throwable);
                        return;
                    }
                    log.debug("[{}] Removed persisted msg {} from the DB", targetClientId, msg.getPacketId());
                });
    }

    private String getTargetClientId(SharedSubscriptionPublishPacket packet) {
        return packet == null ? clientId : packet.getKey();
    }

    private int getTargetPacketId(SharedSubscriptionPublishPacket packet, int receivedPacketId) {
        return packet == null ? receivedPacketId : packet.getPacketId();
    }

    private SharedSubscriptionPublishPacket getSharedSubscriptionPublishPacket(int packetId) {
        return sentPacketIdsFromSharedSubscription.get(packetId);
    }

    private SharedSubscriptionPublishPacket newSharedSubscriptionPublishPacket(String key, int packetId) {
        return new SharedSubscriptionPublishPacket(key, packetId);
    }

    public void processActorStop(TbActorCtx ctx, StopDeviceActorCommandMsg msg) {
        if (msg.getCommandUUID().equals(stopActorCommandUUID)) {
            log.debug("[{}] Stopping DEVICE actor", clientId);
            ctx.stop(ctx.getSelf());
        } else {
            log.debug("[{}] Device was reconnected, ignoring actor stop command", clientId);
        }
    }

    public void processRemovePersistedMessages() {
        deviceMsgService.removePersistedMessages(clientId).whenComplete((status, throwable) -> {
            if (throwable != null) {
                log.debug("Failed to remove persisted messages, clientId - {}", clientId, throwable);
            } else {
                log.debug("Removed persisted messages, clientId - {}", clientId);
            }
        });
    }

    public void processChannelWritable() {
        channelWritable = true;
        log.trace("[{}] Start processing persisted messages on channel writable", clientId);
        findAndDeliverPersistedMessages();
    }

    private void findAndDeliverPersistedMessages() {
        CompletionStage<List<DevicePublishMsg>> persistedMessagesFuture = deviceMsgService.findPersistedMessages(clientId);
        persistedMessagesFuture.whenComplete((persistedMessages, throwable) -> {
            if (throwable == null) {
                log.debug("[{}] Found {} persisted messages to deliver", clientId, persistedMessages.size());
                persistedMessages.forEach(this::deliverPersistedMsg);
                return;
            }
            log.warn("[{}][{}] Failed to process persisted messages.", clientId, sessionCtx.getSessionId(), throwable);
            disconnect("Failed to process persisted messages");
        });
    }

    public void processChannelNonWritable() {
        log.trace("[{}] Channel is not writable", clientId);
        channelWritable = false;
    }
}
