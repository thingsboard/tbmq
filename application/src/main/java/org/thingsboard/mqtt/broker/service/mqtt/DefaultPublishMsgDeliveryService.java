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
package org.thingsboard.mqtt.broker.service.mqtt;

import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retransmission.RetransmissionService;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.stats.timer.DeliveryTimerStats;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DefaultPublishMsgDeliveryService implements PublishMsgDeliveryService {
    private final MqttMessageGenerator mqttMessageGenerator;
    private final RetransmissionService retransmissionService;
    private final DeliveryTimerStats deliveryTimerStats;

    public DefaultPublishMsgDeliveryService(MqttMessageGenerator mqttMessageGenerator,
                                            RetransmissionService retransmissionService,
                                            StatsManager statsManager) {
        this.mqttMessageGenerator = mqttMessageGenerator;
        this.retransmissionService = retransmissionService;
        this.deliveryTimerStats = statsManager.getDeliveryTimerStats();
    }

    @Override
    public void sendPublishMsgToClient(ClientSessionCtx sessionCtx, PublishMsg pubMsg) {
        log.trace("[{}] Sending Pub msg to client {}", sessionCtx.getClientId(), pubMsg);
        MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubMsg(pubMsg);
        sendPublishMsgToClient(sessionCtx, mqttPubMsg);
    }

    @Override
    public void sendPublishMsgToClient(ClientSessionCtx sessionCtx, RetainedMsg retainedMsg) {
        log.trace("[{}] Sending Retained msg to client {}", sessionCtx.getClientId(), retainedMsg);
        int packetId = sessionCtx.getMsgIdSeq().nextMsgId();
        MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubRetainMsg(packetId, retainedMsg);
        sendPublishMsgToClient(sessionCtx, mqttPubMsg);
    }

    private void sendPublishMsgToClient(ClientSessionCtx sessionCtx, MqttPublishMessage mqttPubMsg) {
        long startTime = System.nanoTime();
        try {
            retransmissionService.sendPublishWithRetransmission(sessionCtx, mqttPubMsg);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to send PUBLISH msg to MQTT client. Reason - {}.",
                    sessionCtx.getClientId(), sessionCtx.getSessionId(), e.getMessage());
            log.trace("Detailed error:", e);
            throw e;
        }
        deliveryTimerStats.logDelivery(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
    }

    @Override
    public void sendPubRelMsgToClient(ClientSessionCtx sessionCtx, int packetId) {
        log.trace("[{}] Sending PubRel msg to client {}", sessionCtx.getClientId(), packetId);
        MqttMessage mqttPubRelMsg = mqttMessageGenerator.createPubRelMsg(packetId);
        try {
            retransmissionService.onPubRecReceived(sessionCtx, mqttPubRelMsg);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to send PUBREL msg to MQTT client. Reason - {}.",
                    sessionCtx.getClientId(), sessionCtx.getSessionId(), e.getMessage());
            log.trace("Detailed error:", e);
            throw e;
        }
    }
}
