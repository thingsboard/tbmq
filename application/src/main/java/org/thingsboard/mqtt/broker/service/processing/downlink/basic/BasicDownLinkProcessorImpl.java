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
package org.thingsboard.mqtt.broker.service.processing.downlink.basic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DROPPED_MSGS;

@Slf4j
@Service
@RequiredArgsConstructor
public class BasicDownLinkProcessorImpl implements BasicDownLinkProcessor {

    private final ClientSessionCtxService clientSessionCtxService;
    private final MqttMsgDeliveryService mqttMsgDeliveryService;
    private final ClientLogger clientLogger;
    private final RateLimitService rateLimitService;
    private final TbMessageStatsReportClient tbMessageStatsReportClient;

    @Override
    public void process(String clientId, PublishMsgProto msg) {
        ClientSessionCtx clientSessionCtx = clientSessionCtxService.getClientSessionCtx(clientId);
        if (clientSessionCtx == null) {
            log.trace("[{}] No client session on the node while processing basic downlink", clientId);
            return;
        }
        if (!rateLimitService.checkOutgoingLimits(clientId, msg)) {
            dropMessage();
            return;
        }
        mqttMsgDeliveryService.sendPublishMsgProtoToClient(clientSessionCtx, msg);
        logClientEvent(clientId);
    }

    @Override
    public void process(Subscription subscription, PublishMsgProto msg) {
        ClientSessionCtx clientSessionCtx = clientSessionCtxService.getClientSessionCtx(subscription.getClientId());
        if (clientSessionCtx == null) {
            log.trace("[{}] No client session on the node while processing basic downlink with subscription {}", subscription.getClientId(), subscription);
            return;
        }
        if (!rateLimitService.checkOutgoingLimits(subscription.getClientId(), msg)) {
            dropMessage();
            return;
        }
        mqttMsgDeliveryService.sendPublishMsgProtoToClient(clientSessionCtx, msg, subscription);
        logClientEvent(subscription.getClientId());
    }

    private void dropMessage() {
        tbMessageStatsReportClient.reportStats(DROPPED_MSGS);
    }

    private void logClientEvent(String clientId) {
        clientLogger.logEvent(clientId, this.getClass(), "Delivered msg to basic client");
    }

}
