/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.handlers;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.constant.BrokerConstants;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.exception.AuthorizationException;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.validation.TopicValidationService;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class MqttSubscribeHandler {

    private final MqttMessageGenerator mqttMessageGenerator;
    private final ClientSubscriptionService clientSubscriptionService;
    private final TopicValidationService topicValidationService;
    private final AuthorizationRuleService authorizationRuleService;

    public void process(ClientSessionCtx ctx, MqttSubscribeMessage msg) throws MqttException {
        UUID sessionId = ctx.getSessionId();
        String clientId = ctx.getSessionInfo().getClientInfo().getClientId();
        List<MqttTopicSubscription> mqttTopicSubscriptions = msg.payload().topicSubscriptions();
        try {
            validateSubscriptions(mqttTopicSubscriptions);
        } catch (DataValidationException e) {
            log.info("[{}][{}] Not valid topic, reason - {}", clientId, sessionId, e.getMessage());
            throw new MqttException(e);
        }
        try {
            authorizationRuleService.validateAuthorizationRule(ctx.getAuthorizationRule(), mqttTopicSubscriptions.stream()
                    .map(MqttTopicSubscription::topicName)
                    .collect(Collectors.toList())
            );
        } catch (AuthorizationException e) {
            log.info("[{}][{}] Client doesn't have permission to subscribe to the topic {}",
                    clientId, sessionId, e.getDeniedTopic());
            throw new MqttException(e);
        }

        log.trace("[{}][{}] Processing subscribe [{}], subscriptions - {}", clientId, sessionId, msg.variableHeader().messageId(), mqttTopicSubscriptions);

        List<TopicSubscription> topicSubscriptions = mqttTopicSubscriptions.stream()
                .map(mqttTopicSubscription -> new TopicSubscription(mqttTopicSubscription.topicName(), mqttTopicSubscription.qualityOfService().value()))
                .collect(Collectors.toList());
        clientSubscriptionService.subscribe(clientId, topicSubscriptions);

        List<Integer> grantedQoSList = mqttTopicSubscriptions.stream().map(sub -> getMinSupportedQos(sub.qualityOfService())).collect(Collectors.toList());
        ctx.getChannel().writeAndFlush(mqttMessageGenerator.createSubAckMessage(msg.variableHeader().messageId(), grantedQoSList));
        log.trace("[{}] Client subscribed to {}", sessionId, mqttTopicSubscriptions);
    }

    private void validateSubscriptions(List<MqttTopicSubscription> subscriptions) {
        for (MqttTopicSubscription subscription : subscriptions) {
            topicValidationService.validateTopicFilter(subscription.topicName());
        }
    }

    private static int getMinSupportedQos(MqttQoS reqQoS) {
        return Math.min(reqQoS.value(), BrokerConstants.MAX_SUPPORTED_QOS_LVL.value());
    }

}
