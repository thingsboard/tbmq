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
package org.thingsboard.mqtt.broker.service.mqtt.delivery;

import io.netty.handler.codec.mqtt.MqttPublishMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.stats.timer.DeliveryTimerStats;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.concurrent.TimeUnit;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DROPPED_MSGS;

@Service
@Slf4j
public class DefaultMqttMsgDeliveryService implements MqttMsgDeliveryService {

    private final TbMessageStatsReportClient tbMessageStatsReportClient;
    private final DeliveryTimerStats deliveryTimerStats;

    @Autowired
    public DefaultMqttMsgDeliveryService(TbMessageStatsReportClient tbMessageStatsReportClient,
                                         StatsManager statsManager) {
        this.tbMessageStatsReportClient = tbMessageStatsReportClient;
        this.deliveryTimerStats = statsManager.getDeliveryTimerStats();
    }

    @Override
    public void sendPublishMsgToClient(ClientSessionCtx ctx, MqttPublishMessage msg) {
        processSendPublish(ctx, msg, () -> ctx.getChannel().writeAndFlush(msg));
    }

    @Override
    public void sendPublishMsgToClientWithoutFlush(ClientSessionCtx ctx, MqttPublishMessage msg) {
        processSendPublish(ctx, msg, () -> ctx.getChannel().write(msg));
    }

    private void processSendPublish(ClientSessionCtx ctx, MqttPublishMessage msg, Runnable processor) {
        try {
            boolean added = ctx.addInFlightMsg(msg);
            if (added) {
                long startTime = System.nanoTime();
                processor.run();
                deliveryTimerStats.logDelivery(startTime, TimeUnit.NANOSECONDS);
            }
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to send PUBLISH msg to MQTT client", ctx.getClientId(), ctx.getSessionId(), e);
            if (!msg.fixedHeader().isRetain()) {
                tbMessageStatsReportClient.reportStats(DROPPED_MSGS);
            }
        }
    }

}
