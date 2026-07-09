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
package org.thingsboard.mqtt.broker.service.mqtt.delivery;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.stats.timer.DeliveryTimerStats;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@Slf4j
public class DefaultMqttPublishMsgDeliveryService implements MqttPublishMsgDeliveryService {

    private final TbMessageStatsReportClient tbMessageStatsReportClient;
    private final DeliveryTimerStats deliveryTimerStats;
    private final boolean statsEnabled;

    @Autowired
    public DefaultMqttPublishMsgDeliveryService(TbMessageStatsReportClient tbMessageStatsReportClient,
                                                StatsManager statsManager) {
        this.tbMessageStatsReportClient = tbMessageStatsReportClient;
        this.deliveryTimerStats = statsManager.getDeliveryTimerStats();
        this.statsEnabled = statsManager.isEnabled();
    }

    @Override
    public void sendPublishMsgToClient(ClientSessionCtx ctx, MqttPublishMessage msg) {
        processSendPublish(ctx, msg, () -> ctx.getChannel().writeAndFlush(msg));
    }

    @Override
    public void sendPublishMsgToClientWithoutFlush(ClientSessionCtx ctx, MqttPublishMessage msg) {
        processSendPublish(ctx, msg, () -> ctx.getChannel().write(msg));
    }

    @Override
    public void sendAlreadyTrackedPublishMsgToClient(ClientSessionCtx ctx, MqttPublishMessage msg) {
        try {
            long startTime = System.nanoTime();
            ChannelFuture future = ctx.getChannel().writeAndFlush(msg);
            recordDeliveryOnSuccess(ctx, msg, future, startTime);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to send PUBLISH msg to MQTT client", ctx.getClientId(), ctx.getSessionId(), e);
            if (!msg.fixedHeader().isRetain() && !ctx.getSessionInfo().isPersistent()) {
                tbMessageStatsReportClient.reportDroppedMsgs();
            }
        }
    }

    private void processSendPublish(ClientSessionCtx ctx, MqttPublishMessage msg, Supplier<ChannelFuture> processor) {
        try {
            boolean added = ctx.addInFlightMsg(msg);
            if (added) {
                long startTime = System.nanoTime();
                ChannelFuture future = processor.get();
                recordDeliveryOnSuccess(ctx, msg, future, startTime);
            }
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to send PUBLISH msg to MQTT client", ctx.getClientId(), ctx.getSessionId(), e);
            if (!msg.fixedHeader().isRetain() && !ctx.getSessionInfo().isPersistent()) {
                tbMessageStatsReportClient.reportDroppedMsgs();
            }
        }
    }

    /**
     * Records the delivery latency when the write {@link ChannelFuture} completes successfully, i.e. once the
     * PUBLISH bytes have been written to the socket — not the synchronous cost of handing the write off to the
     * Netty event loop, which the surrounding {@code writeAndFlush}/{@code write} calls return before completing.
     * The listener is attached only when stats are enabled, so that a per-message listener allocation and an
     * event-loop callback are not paid on the hot delivery path when metrics are turned off.
     */
    private void recordDeliveryOnSuccess(ClientSessionCtx ctx, MqttPublishMessage msg, ChannelFuture future, long startTime) {
        if (!statsEnabled) {
            return;
        }
        future.addListener(f -> {
            if (f.isSuccess()) {
                deliveryTimerStats.logDelivery(startTime, TimeUnit.NANOSECONDS);
            } else if (!msg.fixedHeader().isRetain() && !ctx.getSessionInfo().isPersistent()) {
                tbMessageStatsReportClient.reportDroppedMsgs();
            }
        });
    }

}
