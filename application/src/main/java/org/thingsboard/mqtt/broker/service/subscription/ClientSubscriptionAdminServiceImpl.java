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
package org.thingsboard.mqtt.broker.service.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.messages.SubscribeCommandMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.UnsubscribeCommandMsg;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.dto.SubscriptionInfoDto;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.util.CollectionsUtil;

import java.util.Comparator;
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

    @Override
    public void updateSubscriptions(String clientId, List<SubscriptionInfoDto> subscriptions) throws ThingsboardException {
        ClientSessionInfo clientSession = clientSessionCache.getClientSessionInfo(clientId);
        if (clientSession == null) {
            throw new ThingsboardException("No such client session", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
        Set<TopicSubscription> oldSubscriptions = clientSubscriptionCache.getClientSubscriptions(clientId);
        oldSubscriptions = filterOutSharedSubscriptions(oldSubscriptions);
        Set<TopicSubscription> newSubscriptions = collectNewSubscriptions(subscriptions);
        if (log.isDebugEnabled()) {
            log.debug("[{}] Updating subscriptions, old topic-subscriptions - {}, new topic-subscriptions - {}",
                    clientId, oldSubscriptions, newSubscriptions);
        }

        Set<String> unsubscribeTopics = prepareTopicsForUnsubscribe(newSubscriptions, oldSubscriptions);
        clientMqttActorManager.unsubscribe(clientId, new UnsubscribeCommandMsg(unsubscribeTopics));

        Set<TopicSubscription> subscribeTopicSubscriptions = prepareTopicsForSubscribe(newSubscriptions, oldSubscriptions);
        clientMqttActorManager.subscribe(clientId, new SubscribeCommandMsg(subscribeTopicSubscriptions));
    }

    private Set<TopicSubscription> prepareTopicsForSubscribe(Set<TopicSubscription> newSubscriptions, Set<TopicSubscription> oldSubscriptions) {
        return CollectionsUtil.getAddedValues(newSubscriptions, oldSubscriptions,
                Comparator.comparing(TopicSubscription::getTopicFilter).thenComparing(TopicSubscription::getQos));
    }

    private Set<String> prepareTopicsForUnsubscribe(Set<TopicSubscription> newSubscriptions, Set<TopicSubscription> oldSubscriptions) {
        return CollectionsUtil.getRemovedValues(newSubscriptions, oldSubscriptions,
                        Comparator.comparing(TopicSubscription::getTopicFilter).thenComparing(TopicSubscription::getQos))
                .stream()
                .map(TopicSubscription::getTopicFilter)
                .collect(Collectors.toSet());
    }

    private Set<TopicSubscription> collectNewSubscriptions(List<SubscriptionInfoDto> subscriptions) {
        return subscriptions.stream()
                .map(subscriptionInfoDto -> new TopicSubscription(subscriptionInfoDto.getTopicFilter(), subscriptionInfoDto.getQos().value()))
                .collect(Collectors.toSet());
    }

    private Set<TopicSubscription> filterOutSharedSubscriptions(Set<TopicSubscription> subscriptions) {
        return subscriptions.stream().filter(subscription -> subscription.getShareName() == null).collect(Collectors.toSet());
    }
}
