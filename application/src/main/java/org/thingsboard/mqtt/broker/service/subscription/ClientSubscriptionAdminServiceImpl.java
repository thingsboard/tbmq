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
package org.thingsboard.mqtt.broker.service.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.actors.client.messages.SubscribeCommandMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.UnsubscribeCommandMsg;
import org.thingsboard.mqtt.broker.adaptor.NettyMqttConverter;
import org.thingsboard.mqtt.broker.common.data.ApplicationSharedSubscription;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.dao.client.application.ApplicationSharedSubscriptionService;
import org.thingsboard.mqtt.broker.dao.topic.TopicValidationService;
import org.thingsboard.mqtt.broker.dto.SubscriptionInfoDto;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.util.TopicSubscriptionsUtil;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientSubscriptionAdminServiceImpl implements ClientSubscriptionAdminService {

    private final ClientSessionCache clientSessionCache;
    private final ClientSubscriptionCache clientSubscriptionCache;
    private final ClientMqttActorManager clientMqttActorManager;
    private final TopicValidationService topicValidationService;
    private final ApplicationSharedSubscriptionService applicationSharedSubscriptionService;

    @Override
    public void updateSubscriptions(String clientId, List<SubscriptionInfoDto> subscriptions) throws ThingsboardException {
        ClientSessionInfo clientSession = clientSessionCache.getClientSessionInfo(clientId);
        if (clientSession == null) {
            throw new ThingsboardException("No such client session", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }

        for (var subscriptionInfoDto : subscriptions) {
            topicValidationService.validateTopicFilter(subscriptionInfoDto.getTopicFilter());

            if (subscriptionInfoDto.isSharedSubscription() && clientSession.isPersistentAppClient()) {
                if (findSharedSubscriptionByTopicFilter(subscriptionInfoDto) == null) {
                    throw new ThingsboardException("Failed to subscribe to a non-existent Application shared subscription topic filter!", ThingsboardErrorCode.INVALID_ARGUMENTS);
                }
            }
        }

        Set<TopicSubscription> currentSubscriptions = clientSubscriptionCache.getClientSubscriptions(clientId);

        Set<TopicSubscription> newSubscriptions = collectNewSubscriptions(subscriptions);
        if (log.isDebugEnabled()) {
            log.debug("[{}] Updating subscriptions, old topic-subscriptions - {}, new topic-subscriptions - {}",
                    clientId, currentSubscriptions, newSubscriptions);
        }

        TopicSubscriptionsUtil.SubscriptionsUpdate subscriptionsUpdate = TopicSubscriptionsUtil.getSubscriptionsUpdate(currentSubscriptions, newSubscriptions);
        processUnsubscribe(clientId, subscriptionsUpdate);
        processSubscribe(clientId, subscriptionsUpdate);
    }

    private void processUnsubscribe(String clientId, TopicSubscriptionsUtil.SubscriptionsUpdate subscriptionsUpdate) {
        Set<TopicSubscription> removedSubscriptions = subscriptionsUpdate.getToUnsubscribe();
        if (CollectionUtils.isEmpty(removedSubscriptions)) {
            return;
        }
        Set<String> unsubscribeTopics = TopicSubscriptionsUtil.getUnsubscribeTopics(removedSubscriptions);
        clientMqttActorManager.unsubscribe(clientId, new UnsubscribeCommandMsg(unsubscribeTopics));
    }

    private void processSubscribe(String clientId, TopicSubscriptionsUtil.SubscriptionsUpdate subscriptionsUpdate) {
        Set<TopicSubscription> addedSubscriptions = subscriptionsUpdate.getToSubscribe();
        if (CollectionUtils.isEmpty(addedSubscriptions)) {
            return;
        }
        clientMqttActorManager.subscribe(clientId, new SubscribeCommandMsg(addedSubscriptions));
    }

    private Set<TopicSubscription> collectNewSubscriptions(List<SubscriptionInfoDto> subscriptions) {
        return subscriptions.stream()
                .map(SubscriptionInfoDto::toTopicSubscription)
                .collect(Collectors.toSet());
    }

    private ApplicationSharedSubscription findSharedSubscriptionByTopicFilter(SubscriptionInfoDto subscriptionInfoDto) {
        String topicFilter = NettyMqttConverter.getTopicFilter(subscriptionInfoDto.getTopicFilter());
        return applicationSharedSubscriptionService.findSharedSubscriptionByTopic(topicFilter);
    }
}
