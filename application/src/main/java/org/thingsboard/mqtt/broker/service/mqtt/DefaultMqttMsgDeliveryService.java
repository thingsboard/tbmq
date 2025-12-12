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
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.mqtt.delivery.BufferedMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.delivery.MqttPublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.TopicAliasResult;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;
import org.thingsboard.mqtt.broker.util.MqttQosUtil;
import org.thingsboard.mqtt.broker.util.MqttReasonCodeResolver;

import java.util.List;
import java.util.function.Consumer;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DROPPED_MSGS;

@Slf4j
@Service
public class DefaultMqttMsgDeliveryService implements MqttMsgDeliveryService {

    private final MqttMessageGenerator mqttMessageGenerator;
    private final TbMessageStatsReportClient tbMessageStatsReportClient;
    private final BufferedMsgDeliveryService bufferedMsgDeliveryService;
    private final MqttPublishMsgDeliveryService mqttPublishMsgDeliveryService;

    private final boolean isTraceEnabled = log.isTraceEnabled();

    @Value("${mqtt.topic.min-length-for-alias-replacement:50}")
    private int minTopicNameLengthForAliasReplacement;

    public DefaultMqttMsgDeliveryService(MqttMessageGenerator mqttMessageGenerator,
                                         TbMessageStatsReportClient tbMessageStatsReportClient,
                                         BufferedMsgDeliveryService bufferedMsgDeliveryService,
                                         MqttPublishMsgDeliveryService mqttPublishMsgDeliveryService) {
        this.mqttMessageGenerator = mqttMessageGenerator;
        this.tbMessageStatsReportClient = tbMessageStatsReportClient;
        this.bufferedMsgDeliveryService = bufferedMsgDeliveryService;
        this.mqttPublishMsgDeliveryService = mqttPublishMsgDeliveryService;
    }

    @Override
    public void sendPublishMsgToClient(ClientSessionCtx sessionCtx, DevicePublishMsg pubMsg, boolean isDup) {
        if (isTraceEnabled) {
            log.trace("[{}] Executing sendPublishMsgToClient {}, isDup {}", sessionCtx.getClientId(), pubMsg, isDup);
        }
        pubMsg = sessionCtx.getTopicAliasCtx().createPublishMsgUsingTopicAlias(pubMsg, minTopicNameLengthForAliasReplacement);
        MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubMsg(pubMsg, isDup);
        bufferedMsgDeliveryService.sendPublishMsgToDeviceClient(sessionCtx, mqttPubMsg);
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
        if (!sessionCtx.isWritable()) {
            log.debug("[{}] Channel is not writable. Skip send Publish {}", sessionCtx.getClientId(), msg);
            tbMessageStatsReportClient.reportStats(DROPPED_MSGS);
            return;
        }
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

        bufferedMsgDeliveryService.sendPublishMsgToRegularClient(sessionCtx, mqttPubMsg);
    }

    @Override
    public void sendPublishMsgToClientWithoutFlush(ClientSessionCtx sessionCtx, PublishMsg pubMsg) {
        if (isTraceEnabled) {
            log.trace("[{}] Executing sendPublishMsgToClientWithoutFlush {}", sessionCtx.getClientId(), pubMsg);
        }
        pubMsg = sessionCtx.getTopicAliasCtx().createPublishMsgUsingTopicAlias(pubMsg, minTopicNameLengthForAliasReplacement);
        MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubMsg(pubMsg);
        mqttPublishMsgDeliveryService.sendPublishMsgToClientWithoutFlush(sessionCtx, mqttPubMsg);
    }

    @Override
    public void sendPublishRetainedMsgToClient(ClientSessionCtx sessionCtx, RetainedMsg retainedMsg) {
        if (isTraceEnabled) {
            log.trace("[{}] Executing sendPublishRetainedMsgToClient {}", sessionCtx.getClientId(), retainedMsg);
        }
        int packetId = sessionCtx.getMsgIdSeq().nextMsgId();
        MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubRetainMsg(packetId, retainedMsg);
        mqttPublishMsgDeliveryService.sendPublishMsgToClient(sessionCtx, mqttPubMsg);
    }

    @Override
    public void sendPubRelMsgToClient(ClientSessionCtx sessionCtx, int packetId) {
        if (isTraceEnabled) {
            log.trace("[{}] Executing sendPubRelMsgToClient {}", sessionCtx.getClientId(), packetId);
        }
        processSendPubRel(sessionCtx, packetId, msg -> sessionCtx.getChannel().writeAndFlush(msg));
    }

    @Override
    public void sendPubRelMsgToClientWithoutFlush(ClientSessionCtx sessionCtx, int packetId) {
        if (isTraceEnabled) {
            log.trace("[{}] Executing sendPubRelMsgToClientWithoutFlush {}", sessionCtx.getClientId(), packetId);
        }
        processSendPubRel(sessionCtx, packetId, msg -> sessionCtx.getChannel().write(msg));
    }

    private void processSendPubRel(ClientSessionCtx sessionCtx, int packetId, Consumer<MqttMessage> processor) {
        MqttReasonCodes.PubRel code = MqttReasonCodeResolver.pubRelSuccess(sessionCtx);
        MqttMessage mqttPubRelMsg = mqttMessageGenerator.createPubRelMsg(packetId, code);
        try {
            processor.accept(mqttPubRelMsg);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to send PUBREL msg to MQTT client", sessionCtx.getClientId(), sessionCtx.getSessionId(), e);
        }
    }

}
