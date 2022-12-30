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
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttSubscribeMsg;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.common.data.ApplicationSharedSubscription;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;
import org.thingsboard.mqtt.broker.dao.client.application.ApplicationSharedSubscriptionService;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgService;
import org.thingsboard.mqtt.broker.service.mqtt.validation.TopicValidationService;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;
import org.thingsboard.mqtt.broker.util.MqttReasonCode;
import org.thingsboard.mqtt.broker.util.MqttReasonCodeResolver;

import java.util.ArrayList;
import java.util.Collections;
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
    private final ApplicationSharedSubscriptionService applicationSharedSubscriptionService;
    private final MsgPersistenceManager msgPersistenceManager;

    public void process(ClientSessionCtx ctx, MqttSubscribeMsg msg) {
        List<TopicSubscription> topicSubscriptions = msg.getTopicSubscriptions();

        if (log.isTraceEnabled()) {
            log.trace("[{}][{}] Processing subscribe, messageId - {}, subscriptions - {}",
                    ctx.getClientId(), ctx.getSessionId(), msg.getMessageId(), topicSubscriptions);
        }

        List<MqttReasonCode> codes = collectMqttReasonCodes(ctx, msg);
        if (CollectionUtils.isEmpty(codes)) {
            return;
        }
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

    List<MqttReasonCode> collectMqttReasonCodes(ClientSessionCtx ctx, MqttSubscribeMsg msg) {
        List<TopicSubscription> topicSubscriptions = msg.getTopicSubscriptions();

        List<MqttReasonCode> codes = populateReasonCodesIfSubscriptionIdPresent(ctx, msg);
        if (!codes.isEmpty()) {
            return codes;
        }

        for (TopicSubscription subscription : topicSubscriptions) {
            var topic = subscription.getTopic();

            if (isSharedSubscriptionWithNoLocal(subscription)) {
                log.warn("[{}] It is a Protocol Error to set the NoLocal option to true on a Shared Subscription.", ctx.getClientId());
                disconnectClient(ctx, DisconnectReasonType.ON_PROTOCOL_ERROR);
                return Collections.emptyList();
            }

            try {
                topicValidationService.validateTopicFilter(topic);
                validateSharedSubscription(subscription);
            } catch (DataValidationException e) {
                log.warn("[{}][{}] Not valid topic, reason - {}", ctx.getClientId(), ctx.getSessionId(), e);
                codes.add(MqttReasonCodeResolver.failure());
                continue;
            }

            if (!CollectionUtils.isEmpty(ctx.getAuthRulePatterns())) {
                boolean isClientAuthorized = authorizationRuleService.isSubAuthorized(topic, ctx.getAuthRulePatterns());
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
                log.warn("[{}] Subscription id MQTT property present, server not support this!", ctx.getClientId());
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
                disconnectClient(ctx, DisconnectReasonType.ON_SUBSCRIPTION_ID_NOT_SUPPORTED);
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

    private void disconnectClient(ClientSessionCtx ctx, DisconnectReasonType disconnectReasonType) {
        clientMqttActorManager.disconnect(
                ctx.getClientId(),
                new MqttDisconnectMsg(
                        ctx.getSessionId(),
                        new DisconnectReason(disconnectReasonType))
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
                .filter(topicSubscription -> StringUtils.isEmpty(topicSubscription.getShareName()))
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

    private void startProcessingSharedSubscriptions(ClientSessionCtx ctx, List<TopicSubscription> topicSubscriptions) {
        if (!ctx.getSessionInfo().isPersistent()) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] The client session is not persistent to process shared subscriptions!", ctx.getClientId());
            }
            return;
        }
        Set<TopicSharedSubscription> topicSharedSubscriptions = collectUniqueSharedSubscriptions(topicSubscriptions);
        if (CollectionUtils.isEmpty(topicSharedSubscriptions)) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] No shared subscriptions found!", ctx.getClientId());
            }
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

    private void validateSharedSubscription(TopicSubscription subscription) {
        String shareName = subscription.getShareName();
        if (shareName != null && shareName.isEmpty()) {
            throw new DataValidationException("Shared subscription 'shareName' must be at least one character long");
        }
        if (!StringUtils.isEmpty(shareName) && (shareName.contains("+") || shareName.contains("#"))) {
            throw new DataValidationException("Shared subscription 'shareName' can not contain single lvl (+) or multi lvl (#) wildcards");
        }
    }

    private boolean isSharedSubscriptionWithNoLocal(TopicSubscription subscription) {
        return subscription.getShareName() != null && subscription.getOptions().isNoLocal();
    }
}
