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
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.PublishMsgProto;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retransmission.RetransmissionService;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.stats.timer.DeliveryTimerStats;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.TopicAliasResult;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;
import org.thingsboard.mqtt.broker.util.MqttReasonCodeResolver;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.thingsboard.mqtt.broker.common.util.BrokerConstants.DROPPED_MSGS;
import static org.thingsboard.mqtt.broker.common.util.BrokerConstants.OUTGOING_MSGS;

@Slf4j
@Service
public class DefaultPublishMsgDeliveryService implements PublishMsgDeliveryService {

    private final MqttMessageGenerator mqttMessageGenerator;
    private final RetransmissionService retransmissionService;
    private final DeliveryTimerStats deliveryTimerStats;
    private final TbMessageStatsReportClient tbMessageStatsReportClient;

    private final boolean isTraceEnabled = log.isTraceEnabled();

    @Value("${mqtt.topic.min-length-for-alias-replacement:50}")
    private int minTopicNameLengthForAliasReplacement;
    @Value("${mqtt.write-and-flush:true}")
    private boolean writeAndFlush;
    @Value("${mqtt.buffered-msg-count:5}")
    private int bufferedMsgCount;

    public DefaultPublishMsgDeliveryService(MqttMessageGenerator mqttMessageGenerator,
                                            RetransmissionService retransmissionService,
                                            StatsManager statsManager,
                                            TbMessageStatsReportClient tbMessageStatsReportClient) {
        this.mqttMessageGenerator = mqttMessageGenerator;
        this.retransmissionService = retransmissionService;
        this.deliveryTimerStats = statsManager.getDeliveryTimerStats();
        this.tbMessageStatsReportClient = tbMessageStatsReportClient;
    }

    @Override
    public void sendPublishMsgToClient(ClientSessionCtx sessionCtx, PublishMsg pubMsg) {
        if (isTraceEnabled) {
            log.trace("[{}] Sending Pub msg to client {}", sessionCtx.getClientId(), pubMsg);
        }
        pubMsg = sessionCtx.getTopicAliasCtx().createPublishMsgUsingTopicAlias(pubMsg, minTopicNameLengthForAliasReplacement);
        MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubMsg(pubMsg);
        tbMessageStatsReportClient.reportStats(OUTGOING_MSGS);
        sendPublishMsgToClient(sessionCtx, mqttPubMsg);
    }

    @Override
    public void sendPublishMsgProtoToClient(ClientSessionCtx sessionCtx, PublishMsgProto msg, Subscription subscription) {
        if (isTraceEnabled) {
            log.trace("[{}] Sending Pub msg to client {}", sessionCtx.getClientId(), msg);
        }
        TopicAliasResult topicAliasResult = sessionCtx.getTopicAliasCtx().getTopicAliasResult(msg, minTopicNameLengthForAliasReplacement);
        MqttProperties properties = ProtoConverter.createMqttPropertiesWithUserPropsIfPresent(msg.getUserPropertiesList());
        if (topicAliasResult != null) {
            MqttPropertiesUtil.addTopicAliasToProps(properties, topicAliasResult.getTopicAlias());
        }
        if (msg.hasMqttProperties()) {
            ProtoConverter.addFromProtoToMqttProperties(msg.getMqttProperties(), properties);
        }

        String topicName = topicAliasResult == null ? msg.getTopicName() : topicAliasResult.getTopicName();
        int packetId = sessionCtx.getMsgIdSeq().nextMsgId();
        int qos = Math.min(subscription.getQos(), msg.getQos());
        boolean retain = subscription.getOptions().isRetain(msg.getRetain());
        MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubMsg(msg, qos, retain, topicName, packetId, properties);

        tbMessageStatsReportClient.reportStats(OUTGOING_MSGS);
        if (writeAndFlush) {
            sendPublishMsgToClient(sessionCtx, mqttPubMsg);
        } else {
            sendPublishMsgWithoutFlushToClient(sessionCtx, mqttPubMsg);
            if (isFlushNeeded(sessionCtx)) {
                sessionCtx.getChannel().flush();
            }
        }
    }

    @Override
    public void sendPublishMsgToClientWithoutFlush(ClientSessionCtx sessionCtx, PublishMsg pubMsg) {
        if (isTraceEnabled) {
            log.trace("[{}] Sending Pub msg to client without flushing {}", sessionCtx.getClientId(), pubMsg);
        }
        pubMsg = sessionCtx.getTopicAliasCtx().createPublishMsgUsingTopicAlias(pubMsg, minTopicNameLengthForAliasReplacement);
        MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubMsg(pubMsg);
        tbMessageStatsReportClient.reportStats(OUTGOING_MSGS);
        sendPublishMsgWithoutFlushToClient(sessionCtx, mqttPubMsg);
    }

    @Override
    public void sendPublishRetainedMsgToClient(ClientSessionCtx sessionCtx, RetainedMsg retainedMsg) {
        if (isTraceEnabled) {
            log.trace("[{}] Sending Retained msg to client {}", sessionCtx.getClientId(), retainedMsg);
        }
        int packetId = sessionCtx.getMsgIdSeq().nextMsgId();
        MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubRetainMsg(packetId, retainedMsg);
        sendPublishMsgToClient(sessionCtx, mqttPubMsg);
    }

    @Override
    public void sendPubRelMsgToClient(ClientSessionCtx sessionCtx, int packetId) {
        if (isTraceEnabled) {
            log.trace("[{}] Sending PubRel msg to client {}", sessionCtx.getClientId(), packetId);
        }
        processSendPubRel(sessionCtx, packetId, msg -> retransmissionService.onPubRecReceived(sessionCtx, msg));
    }

    @Override
    public void sendPubRelMsgToClientWithoutFlush(ClientSessionCtx sessionCtx, int packetId) {
        if (isTraceEnabled) {
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
            boolean added = sessionCtx.addInFlightMsg(mqttPubMsg);
            if (added) {
                processor.accept(mqttPubMsg);
            }
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to send PUBLISH msg to MQTT client.",
                    sessionCtx.getClientId(), sessionCtx.getSessionId(), e);
            if (!mqttPubMsg.fixedHeader().isRetain()) {
                tbMessageStatsReportClient.reportStats(DROPPED_MSGS);
            }
            throw e;
        }
        deliveryTimerStats.logDelivery(startTime, TimeUnit.NANOSECONDS);
    }

    private void processSendPubRel(ClientSessionCtx sessionCtx, int packetId, Consumer<MqttMessage> processor) {
        MqttReasonCodes.PubRel code = MqttReasonCodeResolver.pubRelSuccess(sessionCtx);
        MqttMessage mqttPubRelMsg = mqttMessageGenerator.createPubRelMsg(packetId, code);
        try {
            processor.accept(mqttPubRelMsg);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to send PUBREL msg to MQTT client.",
                    sessionCtx.getClientId(), sessionCtx.getSessionId(), e);
            throw e;
        }
    }

    private boolean isFlushNeeded(ClientSessionCtx sessionCtx) {
        return sessionCtx.getMsgIdSeq().getCurrentSeq() % bufferedMsgCount == 0;
    }
}
