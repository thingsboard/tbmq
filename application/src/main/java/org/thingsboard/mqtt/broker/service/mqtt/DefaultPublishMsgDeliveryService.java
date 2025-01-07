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
package org.thingsboard.mqtt.broker.service.mqtt;

import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
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
import org.thingsboard.mqtt.broker.util.MqttQosUtil;
import org.thingsboard.mqtt.broker.util.MqttReasonCodeResolver;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DROPPED_MSGS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.OUTGOING_MSGS;

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
    @Value("${mqtt.persistent-session.device.persisted-messages.write-and-flush:true}")
    private boolean persistentWriteAndFlush;
    @Value("${mqtt.persistent-session.device.persisted-messages.buffered-msg-count:5}")
    private int persistentBufferedMsgCount;

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
    public void sendPublishMsgToClient(ClientSessionCtx sessionCtx, DevicePublishMsg pubMsg, boolean isDup) {
        if (isTraceEnabled) {
            log.trace("[{}] Executing sendPublishMsgToClient {}, isDup {}", sessionCtx.getClientId(), pubMsg, isDup);
        }
        pubMsg = sessionCtx.getTopicAliasCtx().createPublishMsgUsingTopicAlias(pubMsg, minTopicNameLengthForAliasReplacement);
        MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubMsg(pubMsg, isDup);
        tbMessageStatsReportClient.reportStats(OUTGOING_MSGS);
        tbMessageStatsReportClient.reportClientReceiveStats(sessionCtx.getClientId(), pubMsg.getQos());
        sendPublishMsgToClient(sessionCtx, mqttPubMsg, persistentWriteAndFlush, persistentBufferedMsgCount);
    }

    @Override
    public void sendPublishMsgProtoToClient(ClientSessionCtx sessionCtx, PublishMsgProto msg) {
        sendPublishMsgProtoToClient(sessionCtx, msg, msg.getQos(), msg.getRetain(), null);
    }

    @Override
    public void sendPublishMsgProtoToClient(ClientSessionCtx sessionCtx, PublishMsgProto msg, Subscription subscription) {
        int qos = MqttQosUtil.downgradeQos(subscription, msg);
        boolean retain = subscription.getOptions().isRetain(msg.getRetain());
        sendPublishMsgProtoToClient(sessionCtx, msg, qos, retain, subscription.getSubscriptionIds());
    }

    private void sendPublishMsgProtoToClient(ClientSessionCtx sessionCtx, PublishMsgProto msg, int qos, boolean retain,
                                             List<Integer> subscriptionIds) {
        if (isTraceEnabled) {
            log.trace("[{}] Executing sendPublishMsgProtoToClient [{}][{}][{}]", sessionCtx.getClientId(), msg, qos, retain);
        }
        TopicAliasResult topicAliasResult = sessionCtx.getTopicAliasCtx().getTopicAliasResult(msg, minTopicNameLengthForAliasReplacement);
        MqttProperties properties = ProtoConverter.createMqttPropertiesWithUserPropsIfPresent(msg.getUserPropertiesList());
        if (topicAliasResult != null) {
            MqttPropertiesUtil.addTopicAliasToProps(properties, topicAliasResult.getTopicAlias());
        }
        if (msg.hasMqttProperties()) {
            ProtoConverter.addFromProtoToMqttProperties(msg.getMqttProperties(), properties);
        }
        if (!CollectionUtils.isEmpty(subscriptionIds)) {
            subscriptionIds.forEach(id -> MqttPropertiesUtil.addSubscriptionIdToProps(properties, id));
        }

        String topicName = topicAliasResult == null ? msg.getTopicName() : topicAliasResult.getTopicName();
        int packetId = sessionCtx.getMsgIdSeq().nextMsgId();
        MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubMsg(msg, qos, retain, topicName, packetId, properties);

        tbMessageStatsReportClient.reportStats(OUTGOING_MSGS);
        tbMessageStatsReportClient.reportClientReceiveStats(sessionCtx.getClientId(), qos);
        sendPublishMsgToClient(sessionCtx, mqttPubMsg, writeAndFlush, bufferedMsgCount);
    }

    @Override
    public void sendPublishMsgToClientWithoutFlush(ClientSessionCtx sessionCtx, PublishMsg pubMsg) {
        if (isTraceEnabled) {
            log.trace("[{}] Executing sendPublishMsgToClientWithoutFlush {}", sessionCtx.getClientId(), pubMsg);
        }
        pubMsg = sessionCtx.getTopicAliasCtx().createPublishMsgUsingTopicAlias(pubMsg, minTopicNameLengthForAliasReplacement);
        MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubMsg(pubMsg);
        tbMessageStatsReportClient.reportStats(OUTGOING_MSGS);
        tbMessageStatsReportClient.reportClientReceiveStats(sessionCtx.getClientId(), pubMsg.getQos());
        sendPublishMsgWithoutFlushToClient(sessionCtx, mqttPubMsg);
    }

    @Override
    public void sendPublishRetainedMsgToClient(ClientSessionCtx sessionCtx, RetainedMsg retainedMsg) {
        if (isTraceEnabled) {
            log.trace("[{}] Executing sendPublishRetainedMsgToClient {}", sessionCtx.getClientId(), retainedMsg);
        }
        int packetId = sessionCtx.getMsgIdSeq().nextMsgId();
        MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubRetainMsg(packetId, retainedMsg);
        tbMessageStatsReportClient.reportStats(OUTGOING_MSGS);
        tbMessageStatsReportClient.reportClientReceiveStats(sessionCtx.getClientId(), retainedMsg.getQos());
        sendPublishMsgToClient(sessionCtx, mqttPubMsg);
    }

    @Override
    public void sendPubRelMsgToClient(ClientSessionCtx sessionCtx, int packetId) {
        if (isTraceEnabled) {
            log.trace("[{}] Executing sendPubRelMsgToClient {}", sessionCtx.getClientId(), packetId);
        }
        processSendPubRel(sessionCtx, packetId, msg -> retransmissionService.onPubRecReceived(sessionCtx, msg));
    }

    @Override
    public void sendPubRelMsgToClientWithoutFlush(ClientSessionCtx sessionCtx, int packetId) {
        if (isTraceEnabled) {
            log.trace("[{}] Executing sendPubRelMsgToClientWithoutFlush {}", sessionCtx.getClientId(), packetId);
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

    private void sendPublishMsgToClient(ClientSessionCtx sessionCtx, MqttPublishMessage mqttPubMsg, boolean writeAndFlush, int bufferedMsgCount) {
        if (writeAndFlush) {
            sendPublishMsgToClient(sessionCtx, mqttPubMsg);
            return;
        }
        sendPublishMsgWithoutFlushToClient(sessionCtx, mqttPubMsg);
        if (isFlushNeeded(mqttPubMsg.variableHeader().packetId(), bufferedMsgCount)) {
            sessionCtx.getChannel().flush();
        }
    }

    private boolean isFlushNeeded(int packetId, int bufferedMsgCount) {
        return packetId % bufferedMsgCount == 0;
    }
}
