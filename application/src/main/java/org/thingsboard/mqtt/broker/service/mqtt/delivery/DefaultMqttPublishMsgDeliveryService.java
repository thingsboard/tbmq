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
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.config.HistoricalDataReportProperties;
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
    private final boolean deliveryOutcomeTracked;

    @Autowired
    public DefaultMqttPublishMsgDeliveryService(TbMessageStatsReportClient tbMessageStatsReportClient,
                                                StatsManager statsManager,
                                                HistoricalDataReportProperties historicalDataReportProperties) {
        this.tbMessageStatsReportClient = tbMessageStatsReportClient;
        this.deliveryTimerStats = statsManager.getDeliveryTimerStats();
        // Both flags are read once here: they are independent switches, and HistoricalDataReportProperties is
        // @Validated, so repeated getter calls on the delivery path would go through a CGLIB proxy.
        this.deliveryOutcomeTracked = statsManager.isEnabled() || historicalDataReportProperties.isEnabled();
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
            recordDeliveryOutcome(ctx, msg, future, startTime);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to send PUBLISH msg to MQTT client", ctx.getClientId(), ctx.getSessionId(), e);
            if (isCountableDrop(ctx, msg)) {
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
                recordDeliveryOutcome(ctx, msg, future, startTime);
            }
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to send PUBLISH msg to MQTT client", ctx.getClientId(), ctx.getSessionId(), e);
            if (isCountableDrop(ctx, msg)) {
                tbMessageStatsReportClient.reportDroppedMsgs();
            }
        }
    }

    /**
     * Handles the outcome of a write once its {@link ChannelFuture} completes.
     * <p>
     * On success it records the delivery latency, i.e. the time until the PUBLISH bytes have been written to the
     * socket — not the synchronous cost of handing the write off to the Netty event loop, which the surrounding
     * {@code writeAndFlush}/{@code write} calls return before completing.
     * <p>
     * On failure it reports a dropped message when the drop is countable (see {@link #isCountableDrop}):
     * non-retained, and either non-persistent or QoS0, since a persistent-session copy of a QoS&gt;0 message
     * remains recoverable from the store (APPLICATION: redelivered from Kafka, counted once at give-up in
     * {@code ApplicationPersistenceProcessorImpl}; DEVICE: redelivered from Redis) whereas QoS0 is never stored
     * and so is always a permanent loss.
     * <p>
     * The listener is attached only when {@code stats.enabled} or {@code historical-data-report.enabled} is on, so
     * that a per-message listener allocation and an event-loop callback are not paid on the hot delivery path when
     * both metric systems are turned off. Both flags matter: the two systems are independent switches, and
     * {@link TbMessageStatsReportClient#reportDroppedMsgs()} feeds each of them (the Micrometer counter and the
     * historical stat), so gating on {@code stats.enabled} alone would drop these failures from the historical
     * timeseries whenever Prometheus meters are off.
     */
    private void recordDeliveryOutcome(ClientSessionCtx ctx, MqttPublishMessage msg, ChannelFuture future, long startTime) {
        if (!deliveryOutcomeTracked) {
            return;
        }
        future.addListener(f -> {
            if (f.isSuccess()) {
                deliveryTimerStats.logDelivery(startTime, TimeUnit.NANOSECONDS);
            } else if (isCountableDrop(ctx, msg)) {
                tbMessageStatsReportClient.reportDroppedMsgs();
            }
        });
    }

    // A drop is countable only when non-retained, and either non-persistent or QoS0. QoS0 is never stored, so
    // it is a permanent loss even for a persistent session; QoS>0 to a persistent session is recoverable from
    // the store (APPLICATION: from Kafka, counted once at give-up in ApplicationPersistenceProcessorImpl;
    // DEVICE: redelivered from Redis) so must not be counted here too, or it would be double counted.
    private boolean isCountableDrop(ClientSessionCtx ctx, MqttPublishMessage msg) {
        return !msg.fixedHeader().isRetain()
                && (!ctx.getSessionInfo().isPersistent() || msg.fixedHeader().qosLevel() == MqttQoS.AT_MOST_ONCE);
    }

}
