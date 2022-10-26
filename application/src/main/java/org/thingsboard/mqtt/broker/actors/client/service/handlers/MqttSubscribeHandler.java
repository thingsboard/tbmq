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
package org.thingsboard.mqtt.broker.actors.client.service.handlers;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttSubscribeMsg;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgService;
import org.thingsboard.mqtt.broker.service.mqtt.validation.TopicValidationService;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;
import org.thingsboard.mqtt.broker.util.MqttReasonCode;
import org.thingsboard.mqtt.broker.util.MqttReasonCodeResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MqttSubscribeHandler {

    private final MqttMessageGenerator mqttMessageGenerator;
    private final ClientSubscriptionService clientSubscriptionService;
    private final TopicValidationService topicValidationService;
    private final AuthorizationRuleService authorizationRuleService;
    private final RetainedMsgService retainedMsgService;
    private final PublishMsgDeliveryService publishMsgDeliveryService;
    private final ClientMqttActorManager clientMqttActorManager;

    public void process(ClientSessionCtx ctx, MqttSubscribeMsg msg) {
        List<TopicSubscription> topicSubscriptions = msg.getTopicSubscriptions();

        log.trace("[{}][{}] Processing subscribe, messageId - {}, subscriptions - {}",
                ctx.getClientId(), ctx.getSessionId(), msg.getMessageId(), topicSubscriptions);

        List<MqttReasonCode> codes = collectMqttReasonCodes(ctx, msg);
        List<TopicSubscription> validTopicSubscriptions = collectValidSubscriptions(topicSubscriptions, codes);

        MqttSubAckMessage subAckMessage = mqttMessageGenerator.createSubAckMessage(msg.getMessageId(), codes);
        subscribeAndPersist(ctx, validTopicSubscriptions, subAckMessage);
    }

    private List<TopicSubscription> collectValidSubscriptions(List<TopicSubscription> topicSubscriptions, List<MqttReasonCode> codes) {
        List<TopicSubscription> validTopicSubscriptions = new ArrayList<>();
        for (int i = 0; i < codes.size(); i++) {
            if (MqttReasonCode.getGrantedQosList().contains(codes.get(i))) {
                validTopicSubscriptions.add(topicSubscriptions.get(i));
            }
        }
        return validTopicSubscriptions;
    }

    List<MqttReasonCode> collectMqttReasonCodes(ClientSessionCtx ctx, MqttSubscribeMsg msg) {
        List<TopicSubscription> topicSubscriptions = msg.getTopicSubscriptions();

        List<MqttReasonCode> codes = populateReasonCodesIfSubscriptionIdPresent(ctx, msg);
        if (!codes.isEmpty()) {
            return codes;
        }

        for (TopicSubscription subscription : topicSubscriptions) {
            var topic = subscription.getTopic();

            try {
                topicValidationService.validateTopicFilter(topic);
            } catch (DataValidationException e) {
                log.warn("[{}][{}] Not valid topic, reason - {}", ctx.getClientId(), ctx.getSessionId(), e);
                codes.add(MqttReasonCodeResolver.failure());
                continue;
            }

            if (!CollectionUtils.isEmpty(ctx.getAuthorizationRules())) {
                boolean isClientAuthorized = authorizationRuleService.isAuthorized(topic, ctx.getAuthorizationRules());
                if (!isClientAuthorized) {
                    log.warn("[{}][{}] Client is not authorized to subscribe to the topic {}",
                            ctx.getClientId(), ctx.getSessionId(), topic);
                    codes.add(MqttReasonCodeResolver.notAuthorizedSubscribe(ctx));
                    continue;
                }
            }
            codes.add(MqttReasonCode.valueOf(subscription.getQos()));
        }
        return codes;
    }

    private List<MqttReasonCode> populateReasonCodesIfSubscriptionIdPresent(ClientSessionCtx ctx, MqttSubscribeMsg msg) {
        var subscriptionsCount = msg.getTopicSubscriptions().size();
        List<MqttReasonCode> codes = new ArrayList<>(subscriptionsCount);
        if (MqttVersion.MQTT_5 == ctx.getMqttVersion()) {
            MqttProperties.MqttProperty subscriptionIdProperty = getSubscriptionIdProperty(msg.getProperties());
            if (subscriptionIdProperty != null) {
                for (int i = 0; i < subscriptionsCount; i++) {
                    codes.add(MqttReasonCode.SUBSCRIPTION_ID_NOT_SUPPORTED);
                }
            }
        }
        return codes;
    }

    private MqttProperties.MqttProperty getSubscriptionIdProperty(MqttProperties properties) {
        return properties.getProperty(MqttProperties.MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value());
    }

    private void subscribeAndPersist(ClientSessionCtx ctx, List<TopicSubscription> newSubscriptions, MqttSubAckMessage subAckMessage) {
        if (CollectionUtils.isEmpty(newSubscriptions)) {
            sendSubAck(ctx, subAckMessage);
            if (isSubscriptionIdNotSupportedCodePresent(subAckMessage)) {
                disconnectClient(ctx);
            }
            return;
        }

        String clientId = ctx.getClientId();
        Set<TopicSubscription> currentSubscriptions = clientSubscriptionService.getClientSubscriptions(clientId);
        clientSubscriptionService.subscribeAndPersist(clientId, newSubscriptions,
                CallbackUtil.createCallback(
                        () -> {
                            sendSubAck(ctx, subAckMessage);
                            processRetainedMessages(ctx, newSubscriptions, currentSubscriptions);
                        },
                        t -> log.warn("[{}][{}] Fail to process client subscription. Exception - {}, reason - {}",
                                clientId, ctx.getSessionId(), t.getClass().getSimpleName(), t.getMessage()))
        );
    }

    private void disconnectClient(ClientSessionCtx ctx) {
        clientMqttActorManager.disconnect(
                ctx.getClientId(),
                new MqttDisconnectMsg(
                        ctx.getSessionId(),
                        new DisconnectReason(DisconnectReasonType.ON_SUBSCRIPTION_ID_NOT_SUPPORTED))
        );
    }

    private void sendSubAck(ClientSessionCtx ctx, MqttSubAckMessage subAckMessage) {
        ctx.getChannel().writeAndFlush(subAckMessage);
    }

    private boolean isSubscriptionIdNotSupportedCodePresent(MqttSubAckMessage subAckMessage) {
        return subAckMessage.payload().reasonCodes().contains(MqttReasonCode.SUBSCRIPTION_ID_NOT_SUPPORTED.intValue());
    }

    private void processRetainedMessages(ClientSessionCtx ctx,
                                         List<TopicSubscription> newSubscriptions,
                                         Set<TopicSubscription> currentSubscriptions) {
        Set<RetainedMsg> retainedMsgSet = getRetainedMessagesForTopicSubscriptions(newSubscriptions, currentSubscriptions);
        retainedMsgSet.forEach(retainedMsg -> publishMsgDeliveryService.sendPublishMsgToClient(ctx, retainedMsg));
    }

    Set<RetainedMsg> getRetainedMessagesForTopicSubscriptions(List<TopicSubscription> newSubscriptions,
                                                              Set<TopicSubscription> currentSubscriptions) {
        return newSubscriptions
                .stream()
                .filter(topicSubscription ->
                        topicSubscription.getOptions().needSendRetainedForTopicSubscription(
                                ts -> !currentSubscriptions.contains(ts), topicSubscription))
                .map(this::getRetainedMessagesForTopicSubscription)
                .flatMap(List::stream)
                .collect(Collectors.toSet());
    }

    private List<RetainedMsg> getRetainedMessagesForTopicSubscription(TopicSubscription topicSubscription) {
        List<RetainedMsg> retainedMessages = getRetainedMessages(topicSubscription);
        return retainedMessages
                .stream()
                .map(retainedMsg -> {
                    int minQoSValue = getMinQoSValue(topicSubscription, retainedMsg);
                    return newRetainedMsg(retainedMsg, minQoSValue);
                }).collect(Collectors.toList());
    }

    private List<RetainedMsg> getRetainedMessages(TopicSubscription topicSubscription) {
        return retainedMsgService.getRetainedMessages(topicSubscription.getTopic());
    }

    private RetainedMsg newRetainedMsg(RetainedMsg retainedMsg, int minQoSValue) {
        return new RetainedMsg(retainedMsg.getTopic(), retainedMsg.getPayload(), minQoSValue, retainedMsg.getProperties());
    }

    private int getMinQoSValue(TopicSubscription topicSubscription, RetainedMsg retainedMsg) {
        return Math.min(topicSubscription.getQos(), retainedMsg.getQosLevel());
    }
}
