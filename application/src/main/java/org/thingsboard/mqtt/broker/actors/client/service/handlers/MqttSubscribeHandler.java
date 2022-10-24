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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttSubscribeMsg;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.common.data.ApplicationSharedSubscription;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;
import org.thingsboard.mqtt.broker.dao.client.application.ApplicationSharedSubscriptionService;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgService;
import org.thingsboard.mqtt.broker.service.mqtt.validation.TopicValidationService;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.util.MqttReasonCode;
import org.thingsboard.mqtt.broker.util.MqttReasonCodeResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
    private final ApplicationSharedSubscriptionService applicationSharedSubscriptionService;
    private final MsgPersistenceManager msgPersistenceManager;

    public void process(ClientSessionCtx ctx, MqttSubscribeMsg msg) throws MqttException {
        List<TopicSubscription> topicSubscriptions = msg.getTopicSubscriptions();

        log.trace("[{}][{}] Processing subscribe, messageId - {}, subscriptions - {}",
                ctx.getClientId(), ctx.getSessionId(), msg.getMessageId(), topicSubscriptions);

        List<MqttReasonCode> codes = collectMqttReasonCodes(ctx, topicSubscriptions);
        List<TopicSubscription> validTopicSubscriptions = collectValidSubscriptions(topicSubscriptions, codes);

        MqttSubAckMessage subAckMessage = mqttMessageGenerator.createSubAckMessage(msg.getMessageId(), codes);
        subscribeAndPersist(ctx, validTopicSubscriptions, subAckMessage);

        startProcessingSharedSubscriptions(ctx, validTopicSubscriptions);
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

    List<MqttReasonCode> collectMqttReasonCodes(ClientSessionCtx ctx, List<TopicSubscription> topicSubscriptions) {
        List<MqttReasonCode> codes = new ArrayList<>(topicSubscriptions.size());
        for (TopicSubscription subscription : topicSubscriptions) {
            var topic = subscription.getTopic();

            try {
                topicValidationService.validateTopicFilter(topic);
                validateSharedSubscription(subscription);
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

    private void startProcessingSharedSubscriptions(ClientSessionCtx ctx, List<TopicSubscription> topicSubscriptions) {
        if (!ctx.getSessionInfo().isPersistent()) {
            log.debug("[{}] The client session is not persistent to process shared subscriptions!", ctx.getClientId());
            return;
        }
        Set<TopicSharedSubscription> topicSharedSubscriptions = collectUniqueSharedSubscriptions(topicSubscriptions);
        if (CollectionUtils.isEmpty(topicSharedSubscriptions)) {
            log.debug("[{}] No shared subscriptions found!", ctx.getClientId());
            return;
        }
        ListenableFuture<Boolean> validationSuccessFuture = validateSharedSubscriptions(ctx, topicSharedSubscriptions);
        DonAsynchron.withCallback(validationSuccessFuture, validationSuccess -> {
            if (validationSuccess) {
                msgPersistenceManager.startProcessingSharedSubscriptions(ctx, topicSharedSubscriptions);
            } else {
                log.warn("Validation of shared subscriptions failed: {}", topicSharedSubscriptions);
            }
        }, throwable -> log.error("Failed to validate shared subscriptions {}", topicSharedSubscriptions, throwable));
    }

    private ListenableFuture<Boolean> validateSharedSubscriptions(ClientSessionCtx ctx, Set<TopicSharedSubscription> topicSharedSubscriptions) {
        if (ClientType.APPLICATION == ctx.getSessionInfo().getClientInfo().getType()) {
            return validateSharedSubscriptions(topicSharedSubscriptions);
        }
        return Futures.immediateFuture(true);
    }

    private ListenableFuture<Boolean> validateSharedSubscriptions(Set<TopicSharedSubscription> topicSharedSubscriptions) {
        List<ListenableFuture<Boolean>> futures = new ArrayList<>(topicSharedSubscriptions.size());
        for (TopicSharedSubscription topicSharedSubscription : topicSharedSubscriptions) {
            futures.add(Futures.transform(findSharedSubscriptionByTopic(topicSharedSubscription), sharedSubscription -> {
                if (sharedSubscription == null) {
                    log.warn("[{}] Failed to subscribe to a non-existent shared subscription topic!", topicSharedSubscription.getTopic());
                    return false;
                }
                return true;
            }, MoreExecutors.directExecutor()));
        }
        return Futures.transform(Futures.allAsList(futures), list -> list.stream().allMatch(bool -> bool), MoreExecutors.directExecutor());
    }

    private ListenableFuture<ApplicationSharedSubscription> findSharedSubscriptionByTopic(TopicSharedSubscription topicSharedSubscription) {
        return applicationSharedSubscriptionService.findSharedSubscriptionByTopicAsync(topicSharedSubscription.getTopic());
    }

    Set<TopicSharedSubscription> collectUniqueSharedSubscriptions(List<TopicSubscription> topicSubscriptions) {
        return topicSubscriptions
                .stream()
                .filter(subscription -> !StringUtils.isEmpty(subscription.getShareName()))
                .collect(Collectors.groupingBy(subscription ->
                        new TopicSharedSubscription(subscription.getTopic(), subscription.getShareName(), subscription.getQos())))
                .keySet();
    }

    private void subscribeAndPersist(ClientSessionCtx ctx, List<TopicSubscription> topicSubscriptions, MqttSubAckMessage subAckMessage) {
        String clientId = ctx.getSessionInfo().getClientInfo().getClientId();
        clientSubscriptionService.subscribeAndPersist(clientId, topicSubscriptions,
                CallbackUtil.createCallback(
                        () -> {
                            ctx.getChannel().writeAndFlush(subAckMessage);
                            processRetainedMessages(ctx, topicSubscriptions);
                        },
                        t -> log.warn("[{}][{}] Fail to process client subscription. Exception - {}, reason - {}",
                                clientId, ctx.getSessionId(), t.getClass().getSimpleName(), t.getMessage()))
        );
    }

    private void processRetainedMessages(ClientSessionCtx ctx, List<TopicSubscription> topicSubscriptions) {
        Set<RetainedMsg> retainedMsgSet = getRetainedMessagesForTopicSubscriptions(topicSubscriptions);
        retainedMsgSet.forEach(retainedMsg -> publishMsgDeliveryService.sendPublishMsgToClient(ctx, retainedMsg));
    }

    Set<RetainedMsg> getRetainedMessagesForTopicSubscriptions(List<TopicSubscription> topicSubscriptions) {
        return topicSubscriptions
                .stream()
                .filter(topicSubscription -> StringUtils.isEmpty(topicSubscription.getShareName()))
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

    private void validateSharedSubscription(TopicSubscription subscription) {
        String shareName = subscription.getShareName();
        if (shareName != null && shareName.isEmpty()) {
            throw new DataValidationException("Shared subscription 'shareName' must be at least one character long");
        }
        if (!StringUtils.isEmpty(shareName) && (shareName.contains("+") || shareName.contains("#"))) {
            throw new DataValidationException("Shared subscription 'shareName' can not contain single lvl (+) or multi lvl (#) wildcards");
        }
    }
}
