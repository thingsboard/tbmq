/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.actors.device.messages.ChannelWritableEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.DeliverPersistedMessagesEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.DeviceConnectedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.IncomingPublishMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketAcknowledgedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketCompletedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketReceivedEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.PacketReceivedNoDeliveryEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.QuotaDeferredRetryMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.SharedSubscriptionEventMsg;
import org.thingsboard.mqtt.broker.actors.device.messages.StopDeviceActorCommandMsg;
import org.thingsboard.mqtt.broker.actors.device.retry.ExponentialBackoffPolicy;
import org.thingsboard.mqtt.broker.actors.device.retry.RetryPolicy;
import org.thingsboard.mqtt.broker.actors.shared.AbstractContextAwareMsgProcessor;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.mqtt.MsgExpiryResult;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.dto.PacketIdDto;
import org.thingsboard.mqtt.broker.dto.SharedSubscriptionPublishPacket;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.limits.QuotaGrant;
import org.thingsboard.mqtt.broker.service.limits.ThroughputQuotaService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionCacheService;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;
import org.thingsboard.mqtt.broker.util.MqttQosUtil;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Getter
class PersistedDeviceActorMessageProcessor extends AbstractContextAwareMsgProcessor {

    private final String clientId;
    private final DeviceMsgService deviceMsgService;
    private final MqttMsgDeliveryService mqttMsgDeliveryService;
    private final ClientLogger clientLogger;
    private final DeviceActorConfiguration deviceActorConfig;
    private final SharedSubscriptionCacheService sharedSubscriptionCacheService;
    private final ThroughputQuotaService throughputQuotaService;
    private final TbMessageStatsReportClient tbMessageStatsReportClient;

    private final Set<Integer> inFlightPacketIds = Sets.newConcurrentHashSet();
    private final ConcurrentMap<Integer, SharedSubscriptionPublishPacket> sentPacketIdsFromSharedSubscription = Maps.newConcurrentMap();

    private final Deque<DevicePublishMsg> deliveryQueue = new LinkedList<>();
    private final AtomicInteger unacknowledgedMsgCounter = new AtomicInteger(0);
    private final RetryPolicy backoffPolicy = new ExponentialBackoffPolicy(1);

    @Setter
    private volatile ClientSessionCtx sessionCtx;
    private volatile UUID stopActorCommandUUID;
    @Setter
    private volatile boolean channelWritable = true;
    @Setter
    private volatile TbActorCtx actorCtx;
    // actor-thread confined: guards against N deferred messages scheduling N retries, and tells
    // processDeliveryQueue to stop draining so nothing overtakes the deferred message
    private boolean quotaRetryScheduled;

    PersistedDeviceActorMessageProcessor(ActorSystemContext systemContext, String clientId) {
        super(systemContext);
        this.clientId = clientId;
        this.deviceMsgService = systemContext.getDeviceMsgService();
        this.mqttMsgDeliveryService = systemContext.getMqttMsgDeliveryService();
        this.clientLogger = systemContext.getClientActorContext().getClientLogger();
        this.deviceActorConfig = systemContext.getDeviceActorConfiguration();
        this.sharedSubscriptionCacheService = systemContext.getSharedSubscriptionCacheService();
        this.throughputQuotaService = systemContext.getThroughputQuotaService();
        this.tbMessageStatsReportClient = systemContext.getTbMessageStatsReportClient();
    }

    public void processDeviceConnect(TbActorCtx actorCtx, DeviceConnectedEventMsg msg) {
        log.trace("[{}] Start processing Device connect", clientId);
        clientLogger.logEvent(clientId, this.getClass(), "Processing device connect");
        sessionCtx = msg.getSessionCtx();
        stopActorCommandUUID = null;
        this.actorCtx = actorCtx;
        findAndDeliverPersistedMessages(actorCtx);
    }

    public void processDeviceDisconnect(TbActorCtx actorCtx) {
        log.trace("[{}] Start processing Device disconnect", clientId);
        clientLogger.logEvent(clientId, this.getClass(), "Processing device disconnect");
        sessionCtx = null;
        stopActorCommandUUID = UUID.randomUUID();
        clearContext();
        long delayMs = TimeUnit.MINUTES.toMillis(deviceActorConfig.getWaitBeforeActorStopMinutes());
        systemContext.scheduleMsgWithDelay(actorCtx, new StopDeviceActorCommandMsg(stopActorCommandUUID), delayMs);
    }

    // TODO: refactor to not use .toCompletableFuture().get(); for deviceMsgService APIs
    @SneakyThrows
    public void processSharedSubscriptions(TbActorCtx actorCtx, SharedSubscriptionEventMsg msg) {
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

            tellDeliverMessagesEvent(actorCtx, persistedMessages);
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

    public void processIncomingMsg(IncomingPublishMsg msg) {
        if (sessionCtx == null) {
            log.trace("[{}] Processing incoming msg when disconnected {}", clientId, msg);
            return;
        }
        if (!channelWritable) {
            log.trace("[{}] Processing incoming msg on Channel non-writable {}", clientId, msg);
            return;
        }
        processPublishMsg(msg.getPublishMsg(), false);
    }

    private void processPublishMsg(DevicePublishMsg publishMsg, boolean fromDeliveryQueue) {
        MsgExpiryResult msgExpiryResult = MqttPropertiesUtil.getMsgExpiryResult(publishMsg, System.currentTimeMillis());
        if (msgExpiryResult.isExpired()) {
            return;
        }
        if (!fromDeliveryQueue && quotaRetryScheduled) {
            // a retry is already in flight for an older deferred message: queue behind it instead
            // of consulting the quota out of turn, so a live message can never overtake a message
            // that was deferred before it arrived. The pending retry is guaranteed to drain it.
            deliveryQueue.addLast(publishMsg);
            return;
        }
        QuotaGrant grant = throughputQuotaService.tryConsumeOutgoingDeferrable(1);
        if (grant.granted() == 0) {
            if (grant.exhausted()) {
                settleQuotaDroppedMsg(publishMsg);  // bucket confirmed dry: terminal, as before
            } else {
                deferQuotaRefusedMsg(publishMsg, fromDeliveryQueue);
            }
            return;
        }
        // TODO: guaranty that DUP flag is correctly set even if Device Actor is dropped
        boolean isDup = inFlightPacketIds.contains(publishMsg.getPacketId());
        if (!isDup) {
            inFlightPacketIds.add(publishMsg.getPacketId());
        }
        try {
            if (msgExpiryResult.isMsgExpiryIntervalPresent()) {
                MqttPropertiesUtil.addMsgExpiryIntervalToProps(publishMsg.getProperties(), msgExpiryResult.getMsgExpiryInterval());
            }
            mqttMsgDeliveryService.sendPublishMsgToClient(sessionCtx, publishMsg, isDup);
            unacknowledgedMsgCounter.incrementAndGet();
            clientLogger.logEvent(clientId, this.getClass(), "Delivered msg to device client");
        } catch (Exception e) {
            log.warn("[{}] Failed to send PUBLISH msg", clientId, e);
            if (sessionCtx != null) disconnect("Failed to send PUBLISH msg");
        }
    }

    private void settleQuotaDroppedMsg(DevicePublishMsg publishMsg) {
        SharedSubscriptionPublishPacket packet = getSharedSubscriptionPublishPacket(publishMsg.getPacketId());
        String targetClientId = getTargetClientId(packet);
        int targetPacketId = getTargetPacketId(packet, publishMsg.getPacketId());
        log.debug("[{}] Dropping persisted msg {} on total throughput quota", targetClientId, targetPacketId);
        tbMessageStatsReportClient.reportDroppedMsgs();
        deviceMsgService.removePersistedMessage(targetClientId, targetPacketId)
                .whenComplete((__, throwable) -> {
                    if (throwable != null) {
                        log.warn("[{}] Failed to remove quota-dropped persisted msg {}", targetClientId, targetPacketId, throwable);
                    }
                });
    }

    private void deferQuotaRefusedMsg(DevicePublishMsg publishMsg, boolean fromDeliveryQueue) {
        // NOT removed from the store and NOT counted as dropped: the cluster has budget, this node
        // simply has not drawn it yet. The message is persisted before the actor ever sees it, so
        // re-queueing is enough to make the refusal non-terminal.
        if (fromDeliveryQueue) {
            deliveryQueue.addFirst(publishMsg);   // it was just polled from the head; put it back
        } else {
            deliveryQueue.addLast(publishMsg);    // a live message is the newest: it belongs last
        }
        if (quotaRetryScheduled) {
            return; // one retry drains the whole queue
        }
        log.debug("[{}] Total throughput quota shortfall, deferring persisted msg {} for {} ms",
                clientId, publishMsg.getPacketId(), ThroughputQuotaService.DEFER_RETRY_MS);
        try {
            systemContext.scheduleMsgWithDelay(actorCtx, new QuotaDeferredRetryMsg(),
                    ThroughputQuotaService.DEFER_RETRY_MS);
            quotaRetryScheduled = true;  // latch only once a retry is really pending
        } catch (Exception e) {
            // the scheduler rejects once the actor system is shutting down. Latching anyway would gate the
            // drain for the rest of the session, and keeping the backlog queued with no retry behind it would
            // spin processDeliveryQueue, so let it go: nothing was removed from the store, and it replays on
            // the next session just like the disconnected case below.
            log.warn("[{}] Failed to schedule the quota-deferred retry, dropping {} queued msg(s) for replay",
                    clientId, deliveryQueue.size(), e);
            deliveryQueue.clear();
        }
    }

    void processQuotaDeferredRetry() {
        if (sessionCtx == null) {
            // Disconnected while the retry was in flight. The queue must not survive: it would hold messages
            // with no retry pending, so a live message of the next session could overtake them and the
            // connect-time replay would duplicate them. Nothing is lost - a deferral never removes the
            // message from the store, so the replay re-finds it.
            clearContext();
            return;
        }
        quotaRetryScheduled = false;
        processDeliveryQueue();
    }

    public void processPacketAcknowledge(PacketAcknowledgedEventMsg msg) {
        SharedSubscriptionPublishPacket packet = getSharedSubscriptionPublishPacket(msg.getPacketId());
        var targetClientId = getTargetClientId(packet);
        deviceMsgService.removePersistedMessage(targetClientId, getTargetPacketId(packet, msg.getPacketId()))
                .whenComplete((__, throwable) -> {
                    decrementUnacknowledgedMsgCounter();
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
                processPubRelMsg(msg.getPacketId());
            }
        });
    }

    public void processPacketReceivedNoDelivery(PacketReceivedNoDeliveryEventMsg msg) {
        SharedSubscriptionPublishPacket packet = getSharedSubscriptionPublishPacket(msg.getPacketId());
        var targetClientId = getTargetClientId(packet);
        deviceMsgService.removePersistedMessage(targetClientId, getTargetPacketId(packet, msg.getPacketId()))
                .whenComplete((__, throwable) -> {
                    decrementUnacknowledgedMsgCounter();
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
                    decrementUnacknowledgedMsgCounter();
                    if (throwable != null) {
                        log.warn("[{}] Failed to remove persisted msg {} from the DB", targetClientId, msg.getPacketId(), throwable);
                        return;
                    }
                    log.trace("[{}] Removed persisted msg {} from the DB", targetClientId, msg.getPacketId());
                });
    }

    private void decrementUnacknowledgedMsgCounter() {
        unacknowledgedMsgCounter.updateAndGet(current -> current == 0 ? 0 : current - 1);
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
                log.warn("Failed to remove persisted messages, clientId - {}", clientId, throwable);
            } else {
                log.trace("Removed persisted messages, clientId - {}", clientId);
            }
        });
    }

    public void processChannelWritable(TbActorCtx actorCtx) {
        int count = unacknowledgedMsgCounter.get();
        log.trace("[{}] Handle channel writable, unacknowledged msg count: {}", clientId, count);

        if (count > 0 && backoffPolicy.canRetry()) {
            systemContext.scheduleHighPriorityMsgWithDelay(actorCtx, new ChannelWritableEventMsg(), backoffPolicy.nextDelay());
            return;
        }
        backoffPolicy.reset();
        findAndDeliverPersistedMessages(actorCtx);
    }

    public void processChannelNonWritable() {
        log.trace("[{}] Channel is not writable", clientId);
        channelWritable = false;
    }

    public void processDeliverPersistedMessages(DeliverPersistedMessagesEventMsg msg) {
        log.trace("[{}] Handle process delivery of persisted messages {}", clientId, msg.getPersistedMessages().size());

        channelWritable = true;
        if (!msg.getPersistedMessages().isEmpty()) {
            deliveryQueue.addAll(msg.getPersistedMessages());
            processDeliveryQueue();
        }
    }

    private void findAndDeliverPersistedMessages(TbActorCtx actorCtx) {
        CompletionStage<List<DevicePublishMsg>> persistedMessagesFuture = deviceMsgService.findPersistedMessages(clientId);
        persistedMessagesFuture.whenComplete((persistedMessages, throwable) -> {
            if (throwable == null) {
                log.debug("[{}] Found {} persisted messages to deliver", clientId, persistedMessages.size());
                tellDeliverMessagesEvent(actorCtx, persistedMessages);
            } else {
                disconnect("Failed to find persisted messages");
            }
        });
    }

    void processDeliveryQueue() {
        log.debug("[{}] Start delivery queue processing", clientId);
        try {
            while (!deliveryQueue.isEmpty() && !quotaRetryScheduled) {
                DevicePublishMsg msg = deliveryQueue.poll();
                if (msg == null) {
                    break;
                }
                try {
                    deliverPersistedMsg(msg);
                } catch (Throwable t) {
                    log.warn("[{}] Failed to deliver message: {}", clientId, msg, t);
                }
            }
        } finally {
            log.debug("[{}] Finish delivery queue processing", clientId);
        }
    }

    void deliverPersistedMsg(DevicePublishMsg persistedMessage) {
        switch (persistedMessage.getPacketType()) {
            case PUBLISH -> processPublishMsg(persistedMessage, true);
            case PUBREL -> processPubRelMsg(persistedMessage.getPacketId());
        }
    }

    private void processPubRelMsg(int packetId) {
        mqttMsgDeliveryService.sendPubRelMsgToClient(sessionCtx, packetId);
    }

    private void tellDeliverMessagesEvent(TbActorCtx actorCtx, List<DevicePublishMsg> persistedMessages) {
        actorCtx.tellWithHighPriority(new DeliverPersistedMessagesEventMsg(persistedMessages));
    }

    private void clearContext() {
        deliveryQueue.clear();
        unacknowledgedMsgCounter.set(0);
        quotaRetryScheduled = false;
    }

    private void disconnect(String message) {
        systemContext.getClientMqttActorManager().disconnect(clientId, new MqttDisconnectMsg(
                sessionCtx.getSessionId(),
                new DisconnectReason(
                        DisconnectReasonType.ON_ERROR, message)));
    }
}
