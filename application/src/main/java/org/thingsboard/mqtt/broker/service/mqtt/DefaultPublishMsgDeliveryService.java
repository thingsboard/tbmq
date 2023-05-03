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
import org.thingsboard.mqtt.broker.util.MqttReasonCode;
import org.thingsboard.mqtt.broker.util.MqttReasonCodeResolver;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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
        if (log.isTraceEnabled()) {
            log.trace("[{}] Sending Pub msg to client {}", sessionCtx.getClientId(), pubMsg);
        }
        MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubMsg(pubMsg);
        sendPublishMsgToClient(sessionCtx, mqttPubMsg);
    }

    @Override
    public void sendPublishMsgToClientWithoutFlush(ClientSessionCtx sessionCtx, PublishMsg pubMsg) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Sending Pub msg to client without flushing {}", sessionCtx.getClientId(), pubMsg);
        }
        MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubMsg(pubMsg);
        sendPublishMsgWithoutFlushToClient(sessionCtx, mqttPubMsg);
    }

    @Override
    public void sendPublishRetainedMsgToClient(ClientSessionCtx sessionCtx, RetainedMsg retainedMsg) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Sending Retained msg to client {}", sessionCtx.getClientId(), retainedMsg);
        }
        int packetId = sessionCtx.getMsgIdSeq().nextMsgId();
        MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubRetainMsg(packetId, retainedMsg);
        sendPublishMsgToClient(sessionCtx, mqttPubMsg);
    }

    @Override
    public void sendPubRelMsgToClient(ClientSessionCtx sessionCtx, int packetId) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Sending PubRel msg to client {}", sessionCtx.getClientId(), packetId);
        }
        processSendPubRel(sessionCtx, packetId, msg -> retransmissionService.onPubRecReceived(sessionCtx, msg));
    }

    @Override
    public void sendPubRelMsgToClientWithoutFlush(ClientSessionCtx sessionCtx, int packetId) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Sending PubRel msg to client without flushing {}", sessionCtx.getClientId(), packetId);
        }
        processSendPubRel(sessionCtx, packetId, msg -> retransmissionService.onPubRecReceivedWithoutFlush(sessionCtx, msg));
    }

    private void sendPublishMsgToClient(ClientSessionCtx sessionCtx, MqttPublishMessage mqttPubMsg) {
        processSendPublish(sessionCtx, mqttPubMsg, msg -> retransmissionService.sendPublish(sessionCtx, msg));
    }

    private void sendPublishMsgWithoutFlushToClient(ClientSessionCtx sessionCtx, MqttPublishMessage mqttPubMsg) {
        processSendPublish(sessionCtx, mqttPubMsg, msg -> retransmissionService.sendPublishWithoutFlush(sessionCtx, msg));
    }

    private void processSendPublish(ClientSessionCtx sessionCtx, MqttPublishMessage mqttPubMsg, Consumer<MqttPublishMessage> processor) {
        long startTime = System.nanoTime();
        try {
            processor.accept(mqttPubMsg);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to send PUBLISH msg to MQTT client.",
                    sessionCtx.getClientId(), sessionCtx.getSessionId(), e);
            throw e;
        }
        deliveryTimerStats.logDelivery(startTime, TimeUnit.NANOSECONDS);
    }

    private void processSendPubRel(ClientSessionCtx sessionCtx, int packetId, Consumer<MqttMessage> processor) {
        MqttReasonCode code = MqttReasonCodeResolver.success(sessionCtx);
        MqttMessage mqttPubRelMsg = mqttMessageGenerator.createPubRelMsg(packetId, code);
        try {
            processor.accept(mqttPubRelMsg);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to send PUBREL msg to MQTT client.",
                    sessionCtx.getClientId(), sessionCtx.getSessionId(), e);
            throw e;
        }
    }
}
