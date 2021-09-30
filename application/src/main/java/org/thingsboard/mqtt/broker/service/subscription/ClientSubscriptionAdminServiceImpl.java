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
package org.thingsboard.mqtt.broker.service.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.dto.SubscriptionInfoDto;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionReader;
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
    private final ClientSessionReader sessionReader;
    private final ClientSubscriptionReader subscriptionReader;
    private final ClientMqttActorManager clientMqttActorManager;

    @Override
    public void updateSubscriptions(String clientId, List<SubscriptionInfoDto> subscriptions) throws ThingsboardException {
        ClientSession clientSession = sessionReader.getClientSession(clientId);
        if (clientSession == null) {
            throw new ThingsboardException("No such client session", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
        Set<TopicSubscription> oldSubscriptions = subscriptionReader.getClientSubscriptions(clientId);
        Set<TopicSubscription> newSubscriptions = subscriptions.stream()
                .map(subscriptionInfoDto -> new TopicSubscription(subscriptionInfoDto.getTopic(), subscriptionInfoDto.getQos().value()))
                .collect(Collectors.toSet());
        log.debug("[{}] Updating subscriptions, old topic-subscriptions - {}, new topic-subscriptions - {}",
                clientId, oldSubscriptions, newSubscriptions);

        Set<String> unsubscribeTopics = CollectionsUtil.getRemovedValues(newSubscriptions, oldSubscriptions,
                        Comparator.comparing(TopicSubscription::getTopic).thenComparing(TopicSubscription::getQos))
                .stream()
                .map(TopicSubscription::getTopic)
                .collect(Collectors.toSet());
        clientMqttActorManager.unsubscribe(clientId, unsubscribeTopics);

        Set<TopicSubscription> subscribeTopicSubscriptions = CollectionsUtil.getAddedValues(newSubscriptions, oldSubscriptions,
                Comparator.comparing(TopicSubscription::getTopic).thenComparing(TopicSubscription::getQos));
        clientMqttActorManager.subscribe(clientId, subscribeTopicSubscriptions);
    }
}
