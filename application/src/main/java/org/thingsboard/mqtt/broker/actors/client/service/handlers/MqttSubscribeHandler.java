/**
 * Copyright © 2016-2024 The Thingsboard Authors
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

import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttSubscribeMsg;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.common.data.ApplicationSharedSubscription;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.StringUtils;
import org.thingsboard.mqtt.broker.common.data.mqtt.MsgExpiryResult;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.dao.client.application.ApplicationSharedSubscriptionService;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.dao.topic.TopicValidationService;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationPersistenceProcessor;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgService;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;
import org.thingsboard.mqtt.broker.util.MqttQosUtil;
import org.thingsboard.mqtt.broker.util.MqttReasonCodeResolver;
import org.thingsboard.mqtt.broker.util.MqttReasonCodeUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
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
    private final ApplicationPersistenceProcessor applicationPersistenceProcessor;
    private final RateLimitService rateLimitService;

    public void process(ClientSessionCtx ctx, MqttSubscribeMsg msg) {
        Set<TopicSharedSubscription> currentSharedSubscriptions = clientSubscriptionService.getClientSharedSubscriptions(ctx.getClientId());
        List<TopicSubscription> topicSubscriptions = msg.getTopicSubscriptions();

        if (log.isTraceEnabled()) {
            log.trace("[{}][{}] Processing subscribe, messageId - {}, subscriptions - {}",
                    ctx.getClientId(), ctx.getSessionId(), msg.getMessageId(), topicSubscriptions);
        }

        List<MqttReasonCodes.SubAck> codes = collectMqttReasonCodes(ctx, msg);
        if (CollectionUtils.isEmpty(codes)) {
            return;
        }
        List<TopicSubscription> validTopicSubscriptions = collectValidSubscriptions(topicSubscriptions, codes);

        MqttSubAckMessage subAckMessage = mqttMessageGenerator.createSubAckMessage(msg.getMessageId(), codes);
        subscribeAndPersist(ctx, validTopicSubscriptions, subAckMessage);

        startProcessingSharedSubscriptions(ctx, validTopicSubscriptions, currentSharedSubscriptions);
    }

    private List<TopicSubscription> collectValidSubscriptions(List<TopicSubscription> topicSubscriptions, List<MqttReasonCodes.SubAck> codes) {
        List<TopicSubscription> validTopicSubscriptions = new ArrayList<>();
        for (int i = 0; i < codes.size(); i++) {
            if (MqttReasonCodeUtil.getGrantedQosList().contains(codes.get(i))) {
                validTopicSubscriptions.add(topicSubscriptions.get(i));
            }
        }
        return validTopicSubscriptions;
    }

    List<MqttReasonCodes.SubAck> collectMqttReasonCodes(ClientSessionCtx ctx, MqttSubscribeMsg msg) {
        List<TopicSubscription> topicSubscriptions = msg.getTopicSubscriptions();

        List<MqttReasonCodes.SubAck> codes = new ArrayList<>(topicSubscriptions.size());

        for (TopicSubscription subscription : topicSubscriptions) {
            var topic = subscription.getTopicFilter();

            if (isSharedSubscriptionWithNoLocal(subscription)) {
                log.warn("[{}] It is a Protocol Error to set the NoLocal option to true on a Shared Subscription.", ctx.getClientId());
                disconnectClient(ctx, DisconnectReasonType.ON_PROTOCOL_ERROR);
                return Collections.emptyList();
            }

            try {
                topicValidationService.validateTopicFilter(topic);
                validateSharedSubscription(subscription);
            } catch (DataValidationException e) {
                log.warn("[{}][{}] Not valid topic", ctx.getClientId(), ctx.getSessionId(), e);
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

            if (subscription.isSharedSubscription() && ctx.getSessionInfo().isPersistent() && ClientType.APPLICATION == ctx.getClientType()) {
                if (findSharedSubscriptionByTopicFilter(subscription.getTopicFilter()) == null) {
                    log.warn("[{}] Failed to subscribe to a non-existent shared subscription topic filter!", subscription.getTopicFilter());
                    codes.add(MqttReasonCodeResolver.implementationSpecificError(ctx));
                    continue;
                }
            }

            codes.add(MqttReasonCodeUtil.qosValueToReasonCode(subscription.getQos()));
        }
        return codes;
    }

    private void subscribeAndPersist(ClientSessionCtx ctx, List<TopicSubscription> newSubscriptions, MqttSubAckMessage subAckMessage) {
        if (CollectionUtils.isEmpty(newSubscriptions)) {
            sendSubAck(ctx, subAckMessage);
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
                        t -> log.warn("[{}][{}] Failed to process client subscription.", clientId, ctx.getSessionId(), t))
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

    private void processRetainedMessages(ClientSessionCtx ctx,
                                         List<TopicSubscription> newSubscriptions,
                                         Set<TopicSubscription> currentSubscriptions) {
        Set<RetainedMsg> retainedMsgSet = getRetainedMessagesForTopicSubscriptions(newSubscriptions, currentSubscriptions);
        retainedMsgSet = applyRateLimits(retainedMsgSet);
        retainedMsgSet.forEach(retainedMsg -> publishMsgDeliveryService.sendPublishRetainedMsgToClient(ctx, retainedMsg));
    }

    Set<RetainedMsg> applyRateLimits(Set<RetainedMsg> retainedMsgSet) {
        if (rateLimitService.isTotalMsgsLimitEnabled()) {
            int availableTokens = (int) rateLimitService.tryConsumeAsMuchAsPossibleTotalMsgs(retainedMsgSet.size());
            if (availableTokens == 0) {
                log.debug("No available tokens left for total msgs bucket during retained msg processing. Skipping {} messages", retainedMsgSet.size());
                return Collections.emptySet();
            }
            if (availableTokens == retainedMsgSet.size()) {
                return retainedMsgSet;
            }
            if (log.isDebugEnabled() && availableTokens < retainedMsgSet.size()) {
                log.debug("Hitting total messages rate limits on retained msg processing. Skipping {} messages", retainedMsgSet.size() - availableTokens);
            }
            return retainedMsgSet.stream().limit(availableTokens).collect(Collectors.toSet());
        }
        return retainedMsgSet;
    }

    Set<RetainedMsg> getRetainedMessagesForTopicSubscriptions(List<TopicSubscription> newSubscriptions,
                                                              Set<TopicSubscription> currentSubscriptions) {
        return newSubscriptions
                .stream()
                .filter(TopicSubscription::isCommonSubscription)
                .filter(topicSubscription ->
                        topicSubscription.getOptions().needSendRetainedForTopicSubscription(
                                ts -> !currentSubscriptions.contains(ts), topicSubscription))
                .map(this::getRetainedMessagesForTopicSubscription)
                .flatMap(List::stream)
                .collect(Collectors.toSet());
    }

    List<RetainedMsg> getRetainedMessagesForTopicSubscription(TopicSubscription topicSubscription) {
        long currentTs = System.currentTimeMillis();
        List<RetainedMsg> retainedMessages = getRetainedMessages(topicSubscription);
        List<RetainedMsg> result = new ArrayList<>(retainedMessages.size());
        for (RetainedMsg retainedMsg : retainedMessages) {
            MsgExpiryResult msgExpiryResult = MqttPropertiesUtil.getMsgExpiryResult(retainedMsg, currentTs);
            if (msgExpiryResult.isExpired()) {
                continue;
            }
            int qos = MqttQosUtil.downgradeQos(topicSubscription, retainedMsg);
            RetainedMsg newRetainedMsg = newRetainedMsg(retainedMsg, qos);

            if (msgExpiryResult.isMsgExpiryIntervalPresent()) {
                MqttPropertiesUtil.addMsgExpiryIntervalToProps(newRetainedMsg.getProperties(), msgExpiryResult.getMsgExpiryInterval());
            }
            MqttPropertiesUtil.addSubscriptionIdToProps(newRetainedMsg.getProperties(), topicSubscription.getSubscriptionId());
            result.add(newRetainedMsg);
        }
        return result;
    }

    private List<RetainedMsg> getRetainedMessages(TopicSubscription topicSubscription) {
        return retainedMsgService.getRetainedMessages(topicSubscription.getTopicFilter());
    }

    private RetainedMsg newRetainedMsg(RetainedMsg retainedMsg, int qos) {
        return retainedMsg.withQosAndProps(qos, MqttPropertiesUtil.copyProps(retainedMsg.getProperties()));
    }

    private void startProcessingSharedSubscriptions(ClientSessionCtx ctx, List<TopicSubscription> topicSubscriptions,
                                                    Set<TopicSharedSubscription> currentSharedSubscriptions) {
        if (!ctx.getSessionInfo().isPersistent()) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] The client session is not persistent to process persisted messages for shared subscriptions!", ctx.getClientId());
            }
            return;
        }
        Set<TopicSharedSubscription> newSharedSubscriptions = collectUniqueSharedSubscriptions(topicSubscriptions);
        if (CollectionUtils.isEmpty(newSharedSubscriptions)) {
            if (log.isDebugEnabled()) {
                log.debug("[{}] No shared subscriptions found!", ctx.getClientId());
            }
            return;
        }
        Set<TopicSharedSubscription> newSubscriptions = filterNewUniqueSubscriptions(newSharedSubscriptions, currentSharedSubscriptions);
        newSubscriptions.addAll(findSameSubscriptionsWithDifferentQos(ctx, newSharedSubscriptions, currentSharedSubscriptions));
        msgPersistenceManager.startProcessingSharedSubscriptions(ctx, newSubscriptions);
    }

    Set<TopicSharedSubscription> filterNewUniqueSubscriptions(Set<TopicSharedSubscription> newSharedSubscriptions,
                                                              Set<TopicSharedSubscription> currentSharedSubscriptions) {
        return newSharedSubscriptions
                .stream()
                .filter(sub -> !currentSharedSubscriptions.contains(sub))
                .collect(Collectors.toSet());
    }

    Set<TopicSharedSubscription> findSameSubscriptionsWithDifferentQos(ClientSessionCtx ctx,
                                                                       Set<TopicSharedSubscription> newSharedSubscriptions,
                                                                       Set<TopicSharedSubscription> currentSharedSubscriptions) {
        Set<TopicSharedSubscription> differentQosSubscriptions;
        if (ClientType.APPLICATION == ctx.getClientType()) {
            differentQosSubscriptions = filterSameSubscriptionsWithDifferentQos(newSharedSubscriptions, currentSharedSubscriptions, this::isQosDifferent);
            applicationPersistenceProcessor.stopProcessingSharedSubscriptions(ctx, differentQosSubscriptions);
        } else {
            differentQosSubscriptions = filterSameSubscriptionsWithDifferentQos(newSharedSubscriptions, currentSharedSubscriptions, this::isQosDifferentAndCurrentIsZero);
        }
        return differentQosSubscriptions;
    }

    boolean isQosDifferentAndCurrentIsZero(TopicSharedSubscription currentSubscription, TopicSharedSubscription newSubscription) {
        return isQosDifferent(currentSubscription, newSubscription) && currentSubscription.getQos() == 0;
    }

    boolean isQosDifferent(TopicSharedSubscription currentSubscription, TopicSharedSubscription newSubscription) {
        return currentSubscription != null && currentSubscription.getQos() != newSubscription.getQos();
    }

    Set<TopicSharedSubscription> filterSameSubscriptionsWithDifferentQos(Set<TopicSharedSubscription> newSharedSubscriptions,
                                                                         Set<TopicSharedSubscription> currentSharedSubscriptions,
                                                                         BiFunction<TopicSharedSubscription, TopicSharedSubscription, Boolean> compareQos) {
        return newSharedSubscriptions
                .stream()
                .filter(currentSharedSubscriptions::contains)
                .filter(newSubscription -> {
                    TopicSharedSubscription currentSubscription = currentSharedSubscriptions
                            .stream()
                            .filter(currentSub -> currentSub.equals(newSubscription))
                            .findFirst()
                            .orElse(null);

                    return compareQos.apply(currentSubscription, newSubscription);
                })
                .collect(Collectors.toSet());
    }

    private ApplicationSharedSubscription findSharedSubscriptionByTopicFilter(String topicFilter) {
        return applicationSharedSubscriptionService.findSharedSubscriptionByTopic(topicFilter);
    }

    Set<TopicSharedSubscription> collectUniqueSharedSubscriptions(List<TopicSubscription> topicSubscriptions) {
        return topicSubscriptions
                .stream()
                .filter(TopicSubscription::isSharedSubscription)
                .collect(Collectors.groupingBy(TopicSharedSubscription::fromTopicSubscription))
                .keySet();
    }

    void validateSharedSubscription(TopicSubscription subscription) {
        String shareName = subscription.getShareName();
        if (shareName != null && shareName.isEmpty()) {
            throw new DataValidationException("Shared subscription 'shareName' must be at least one character long");
        }
        if (!StringUtils.isEmpty(shareName) && shareNameContainsWildcards(shareName)) {
            throw new DataValidationException("Shared subscription 'shareName' can not contain single lvl (+) or multi lvl (#) wildcards");
        }
    }

    private boolean shareNameContainsWildcards(String shareName) {
        return shareName.contains(BrokerConstants.SINGLE_LEVEL_WILDCARD) || shareName.contains(BrokerConstants.MULTI_LEVEL_WILDCARD);
    }

    boolean isSharedSubscriptionWithNoLocal(TopicSubscription subscription) {
        return subscription.isSharedSubscription() && subscription.getOptions().isNoLocal();
    }
}
