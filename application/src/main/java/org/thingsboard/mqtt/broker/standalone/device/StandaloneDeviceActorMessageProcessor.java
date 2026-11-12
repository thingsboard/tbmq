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
package org.thingsboard.mqtt.broker.standalone.device;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.TbActorCtx;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.state.PublishedInFlightCtx;
import org.thingsboard.mqtt.broker.actors.device.DeviceActorMessageProcessor;
import org.thingsboard.mqtt.broker.actors.device.messages.DeliverPersistedMessagesEventMsg;
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
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgQueueStore;
import org.thingsboard.mqtt.broker.dao.messages.DeviceQueueInfo;
import org.thingsboard.mqtt.broker.dao.messages.StoredDeviceMsg;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionCacheService;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Delivers a persisted DEVICE client's messages from its store queue. The processor is a cursor over the queue: it
 * reads the entries after the last one it handed to the channel, a page at a time, and never holds more than one
 * page of entries that were handed over and not yet acknowledged. A connect starts the cursor over at the head of the
 * queue, so whatever a previous connection did not acknowledge comes back, marked DUP when the store had it sent.
 * <p>
 * Every field is touched on the actor thread only.
 */
@Slf4j
class StandaloneDeviceActorMessageProcessor extends AbstractContextAwareMsgProcessor implements DeviceActorMessageProcessor {

    private static final long REPLAY_PERMIT_RETRY_MS = 100;

    private enum Replay {
        /** The client replays nothing or its connect-time backlog is handed over. */
        NONE,
        /** The client has a backlog and waits for a permit; nothing is handed over meanwhile. */
        AWAITING_PERMIT,
        /** The client holds a permit and its backlog is being handed over. */
        RUNNING
    }

    private final String clientId;
    private final DeviceMsgQueueStore store;
    private final ReplayLimiter replayLimiter;
    private final int readPageSize;
    private final Clock clock;
    private final MqttMsgDeliveryService mqttMsgDeliveryService;
    private final SharedSubscriptionCacheService sharedSubscriptionCacheService;
    private final ClientLogger clientLogger;

    private ClientSessionCtx sessionCtx;
    private TbActorCtx actorCtx;
    // the sequence of the last entry handed to the channel
    private long cursor;
    // entries handed to the channel and not yet acknowledged
    private int outstanding;
    private boolean channelWritable = true;
    private Replay replay = Replay.NONE;
    // the last sequence of the queue at connect: the backlog a replay permit covers
    private long replayEnd;
    // a permit retry is on its way to this actor; one at most, whatever the number of reconnects
    private boolean replayRetryScheduled;
    private UUID stopActorCommandUUID;

    StandaloneDeviceActorMessageProcessor(ActorSystemContext systemContext, String clientId, DeviceMsgQueueStore store,
                                          ReplayLimiter replayLimiter, int readPageSize, Clock clock) {
        super(systemContext);
        this.clientId = clientId;
        this.store = store;
        this.replayLimiter = replayLimiter;
        this.readPageSize = readPageSize;
        this.clock = clock;
        this.mqttMsgDeliveryService = systemContext.getMqttMsgDeliveryService();
        this.sharedSubscriptionCacheService = systemContext.getSharedSubscriptionCacheService();
        this.clientLogger = systemContext.getClientActorContext().getClientLogger();
    }

    @Override
    public void processDeviceConnect(TbActorCtx actorCtx, DeviceConnectedEventMsg msg) {
        log.trace("[{}] Start processing Device connect", clientId);
        clientLogger.logEvent(clientId, this.getClass(), "Processing device connect");
        this.actorCtx = actorCtx;
        // a connect without a disconnect (takeover) must not inherit a permit or a wait for one; a retry already on
        // its way stays the only chain (replayRetryScheduled) and finds the reset state harmless
        releaseReplayPermit();
        sessionCtx = msg.getSessionCtx();
        stopActorCommandUUID = null;
        cursor = 0;
        outstanding = 0;
        channelWritable = true;
        handle("start the delivery of persisted messages", () -> {
            DeviceQueueInfo info = store.info(clientId);
            replayEnd = info.lastSequence();
            if (info.count() > 0 && !acquireReplayPermit()) {
                return;
            }
            pull();
        });
    }

    @Override
    public void processDeviceDisconnect(TbActorCtx actorCtx) {
        log.trace("[{}] Start processing Device disconnect", clientId);
        clientLogger.logEvent(clientId, this.getClass(), "Processing device disconnect");
        sessionCtx = null;
        releaseReplayPermit();
        stopActorCommandUUID = UUID.randomUUID();
        long delayMs = TimeUnit.MINUTES.toMillis(systemContext.getDeviceActorConfiguration().getWaitBeforeActorStopMinutes());
        systemContext.scheduleMsgWithDelay(actorCtx, new StopDeviceActorCommandMsg(stopActorCommandUUID), delayMs);
    }

    /**
     * Moves the backlog of every shared group this client may take over into the client's own queue, then delivers
     * it from there. The store keeps the packet ids consistent; a moved entry is acknowledged in the client's queue.
     */
    @Override
    public void processSharedSubscriptions(TbActorCtx actorCtx, SharedSubscriptionEventMsg msg) {
        log.trace("[{}] Start processing Device shared subscriptions {}", clientId, msg.getSubscriptions());
        if (CollectionUtils.isEmpty(msg.getSubscriptions())) {
            return;
        }
        handle("move the shared subscription messages", () -> {
            for (TopicSharedSubscription subscription : msg.getSubscriptions()) {
                // QoS 0 group messages are delivered downgraded to QoS 0, so they cannot be acknowledged from the store
                if (subscription.getQos() == 0
                        || sharedSubscriptionCacheService.isAnyOtherDeviceClientConnected(clientId, subscription)) {
                    continue;
                }
                int moved = store.moveQueue(subscription.getKey(), clientId, body ->
                        DeviceMsgBodyCodec.withQosAndSubscriptionId(body, subscription.getQos(), subscription.getSubscriptionId()));
                log.debug("[{}] Moved {} messages of shared subscription {}", clientId, moved, subscription.getKey());
            }
            pull();
        });
    }

    /**
     * A wake-up only: the message is already in the store and is delivered in its turn.
     */
    @Override
    public void processIncomingMsg(IncomingPublishMsg msg) {
        handle("deliver persisted messages", this::pull);
    }

    @Override
    public void processPacketAcknowledge(PacketAcknowledgedEventMsg msg) {
        complete(msg.getPacketId());
    }

    @Override
    public void processPacketReceived(PacketReceivedEventMsg msg) {
        handle("mark a message received", () -> {
            store.markPubRel(clientId, msg.getPacketId());
            if (sessionCtx != null) {
                mqttMsgDeliveryService.sendPubRelMsgToClient(sessionCtx, msg.getPacketId());
            }
        });
    }

    @Override
    public void processPacketReceivedNoDelivery(PacketReceivedNoDeliveryEventMsg msg) {
        complete(msg.getPacketId());
    }

    @Override
    public void processPacketComplete(PacketCompletedEventMsg msg) {
        complete(msg.getPacketId());
    }

    @Override
    public void processActorStop(TbActorCtx ctx, StopDeviceActorCommandMsg msg) {
        if (msg.getCommandUUID().equals(stopActorCommandUUID)) {
            log.debug("[{}] Stopping DEVICE actor", clientId);
            ctx.stop(ctx.getSelf());
        } else {
            log.debug("[{}] Device was reconnected, ignoring actor stop command", clientId);
        }
    }

    @Override
    public void processRemovePersistedMessages() {
        handle("remove persisted messages", () -> {
            store.removeQueue(clientId);
            cursor = 0;
            outstanding = 0;
            releaseReplayPermit();
        });
    }

    @Override
    public void processChannelWritable(TbActorCtx actorCtx) {
        log.trace("[{}] Channel is writable", clientId);
        channelWritable = true;
        handle("deliver persisted messages", this::pull);
    }

    @Override
    public void processChannelNonWritable() {
        log.trace("[{}] Channel is not writable", clientId);
        channelWritable = false;
    }

    /**
     * Arrives when a client that waits for a replay permit tries again.
     */
    @Override
    public void processDeliverPersistedMessages(DeliverPersistedMessagesEventMsg msg) {
        replayRetryScheduled = false;
        handle("deliver persisted messages", () -> {
            if (sessionCtx == null || replay == Replay.AWAITING_PERMIT && !acquireReplayPermit()) {
                return;
            }
            pull();
        });
    }

    private void complete(int packetId) {
        handle("remove an acknowledged message", () -> {
            store.remove(clientId, packetId);
            outstanding = Math.max(0, outstanding - 1);
            pull();
        });
    }

    /**
     * Hands the next page of the queue to the channel. The page is no larger than the free part of the read page
     * size and of the client's Receive Maximum, and it is marked sent before it is handed over, so that neither a
     * sweep nor a trim deletes a handed-over entry as expired.
     */
    private void pull() {
        if (sessionCtx == null || !channelWritable || replay == Replay.AWAITING_PERMIT) {
            return;
        }
        int budget = Math.min(readPageSize - outstanding, freeSlots());
        if (budget <= 0) {
            return;
        }
        List<StoredDeviceMsg> page = store.read(clientId, cursor, budget);
        if (!page.isEmpty()) {
            store.markSent(clientId, page.get(page.size() - 1).sequence());
            long now = clock.millis();
            for (StoredDeviceMsg entry : page) {
                handOver(entry, now);
                cursor = entry.sequence();
                outstanding++;
            }
        }
        if (cursor >= replayEnd || page.size() < budget) {
            // the connect-time backlog is handed over, or the page reached the end of the queue
            releaseReplayPermit();
        }
    }

    private int freeSlots() {
        PublishedInFlightCtx inFlightCtx = sessionCtx.getPublishedInFlightCtx();
        // no flow control: the broker does not enforce the client's Receive Maximum
        return inFlightCtx == null ? Integer.MAX_VALUE : inFlightCtx.freeSlots();
    }

    private void handOver(StoredDeviceMsg entry, long now) {
        switch (entry.packetType()) {
            case PUBREL -> mqttMsgDeliveryService.sendPubRelMsgToClient(sessionCtx, entry.packetId());
            case PUBLISH -> {
                DevicePublishMsg msg = DeviceMsgBodyCodec.toMsg(clientId, entry);
                Integer msgExpiryInterval = msg.getMsgExpiryInterval();
                if (msgExpiryInterval != null) {
                    // [MQTT-3.3.2-6] the interval left: the original one less the whole seconds the message waited, never below 0
                    long waitedSec = Math.max(0, now - entry.createdTimeMs()) / 1000;
                    MqttPropertiesUtil.addMsgExpiryIntervalToProps(msg.getProperties(), (int) Math.max(0, msgExpiryInterval - waitedSec));
                }
                // [MQTT-4.4.0-1] [MQTT-3.3.1-1] a resend keeps the original packet id and carries DUP=1
                mqttMsgDeliveryService.sendPublishMsgToClient(sessionCtx, msg, entry.sent());
            }
        }
    }

    /**
     * @return {@code true} when the client holds a permit; otherwise a retry is scheduled unless one is already on its way
     */
    private boolean acquireReplayPermit() {
        if (replayLimiter.tryAcquire(clientId)) {
            replay = Replay.RUNNING;
            return true;
        }
        log.debug("[{}] No replay permit is free, retrying in {} ms", clientId, REPLAY_PERMIT_RETRY_MS);
        replay = Replay.AWAITING_PERMIT;
        if (!replayRetryScheduled) {
            replayRetryScheduled = true;
            systemContext.scheduleMsgWithDelay(actorCtx, new DeliverPersistedMessagesEventMsg(List.of()), REPLAY_PERMIT_RETRY_MS);
        }
        return false;
    }

    private void releaseReplayPermit() {
        if (replay == Replay.RUNNING) {
            replayLimiter.release(clientId);
        }
        replay = Replay.NONE;
    }

    private void handle(String action, Runnable handler) {
        try {
            handler.run();
        } catch (Exception e) {
            log.warn("[{}] Failed to {}", clientId, action, e);
            if (sessionCtx != null) {
                systemContext.getClientMqttActorManager().disconnect(clientId, new MqttDisconnectMsg(sessionCtx.getSessionId(),
                        new DisconnectReason(DisconnectReasonType.ON_ERROR, "Failed to " + action)));
            }
        }
    }

}
