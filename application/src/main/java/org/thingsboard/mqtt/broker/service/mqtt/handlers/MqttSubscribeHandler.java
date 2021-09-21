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

import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttSubscribeMsg;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.validation.TopicValidationService;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.Collection;
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

    public void process(ClientSessionCtx ctx, MqttSubscribeMsg msg) throws MqttException {
        UUID sessionId = ctx.getSessionId();
        String clientId = ctx.getSessionInfo().getClientInfo().getClientId();
        List<TopicSubscription> topicSubscriptions = msg.getTopicSubscriptions();

        log.trace("[{}][{}] Processing subscribe, messageId - {}, subscriptions - {}", clientId, sessionId, msg.getMessageId(), topicSubscriptions);

        validateSubscriptions(clientId, sessionId, topicSubscriptions);

        validateClientAccess(ctx, topicSubscriptions);

        // TODO: it's possible that client will get messages for topic before SUB_ACK
        List<Integer> grantedQoSList = topicSubscriptions.stream().map(TopicSubscription::getQos).collect(Collectors.toList());
        MqttSubAckMessage subAckMessage = mqttMessageGenerator.createSubAckMessage(msg.getMessageId(), grantedQoSList);
        clientSubscriptionService.subscribeAndPersist(clientId, topicSubscriptions, CallbackUtil.createCallback(() -> ctx.getChannel().writeAndFlush(subAckMessage),
                t -> log.warn("[{}][{}] Fail to process client subscription. Exception - {}, reason - {}", clientId, sessionId, t.getClass().getSimpleName(), t.getMessage()))
        );
    }

    private void validateSubscriptions(String clientId, UUID sessionId, Collection<TopicSubscription> subscriptions) {
        try {
            for (TopicSubscription subscription : subscriptions) {
                topicValidationService.validateTopicFilter(subscription.getTopic());
            }
        } catch (DataValidationException e) {
            log.warn("[{}][{}] Not valid topic, reason - {}", clientId, sessionId, e.getMessage());
            throw new MqttException(e);
        }
    }

    private void validateClientAccess(ClientSessionCtx ctx, List<TopicSubscription> topicSubscriptions) {
        if (ctx.getAuthorizationRules() == null) {
            return;
        }
        List<String> topics = topicSubscriptions.stream()
                .map(TopicSubscription::getTopic)
                .collect(Collectors.toList());
        for (String topic : topics) {
            boolean isClientAuthorized = authorizationRuleService.isAuthorized(topic, ctx.getAuthorizationRules());
            if (!isClientAuthorized) {
                log.warn("[{}][{}] Client is not authorized to subscribe to the topic {}",
                        ctx.getClientId(), ctx.getSessionId(), topic);
                throw new MqttException("Client is not authorized to subscribe to the topic");
            }
        }
    }
}
