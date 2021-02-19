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

import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.TopicSubscription;
import org.thingsboard.mqtt.broker.service.mqtt.client.ClientSessionService;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultSubscriptionManager implements SubscriptionManager {
    private final ClientSessionService clientSessionService;
    private final SubscriptionService subscriptionService;

    @PostConstruct
    public void init() {
        Collection<ClientSession> persistedClientSessions = clientSessionService.getPersistedClientSessions();
        log.info("Restoring persisted subscriptions for {} clients.", persistedClientSessions.size());
        for (ClientSession persistedClientSession : persistedClientSessions) {
            String clientId = persistedClientSession.getClientInfo().getClientId();
            log.trace("[{}] Restoring subscriptions - {}.", clientId, persistedClientSession.getTopicSubscriptions());
            subscriptionService.subscribe(clientId, persistedClientSession.getTopicSubscriptions());
        }
    }

    @Override
    public void subscribe(String clientId, List<MqttTopicSubscription> mqttTopicSubscriptions) {
        List<TopicSubscription> topicSubscriptions = mqttTopicSubscriptions.stream()
                .map(mqttTopicSubscription -> new TopicSubscription(mqttTopicSubscription.topicName(), mqttTopicSubscription.qualityOfService().value()))
                .collect(Collectors.toList());
        subscriptionService.subscribe(clientId, topicSubscriptions);

        ClientSession prevClientSession = clientSessionService.getClientSession(clientId);
        ClientSession clientSession = prevClientSession.toBuilder().build();
        Set<TopicSubscription> persistedTopicSubscriptions = clientSession.getTopicSubscriptions();
        for (MqttTopicSubscription topicSubscription : mqttTopicSubscriptions) {
            persistedTopicSubscriptions.add(new TopicSubscription(topicSubscription.topicName(), topicSubscription.qualityOfService().value()));
        }
        clientSessionService.replaceClientSession(clientId, prevClientSession, clientSession);
    }

    @Override
    public void unsubscribe(String clientId, List<String> topicFilters) {
        subscriptionService.unsubscribe(clientId, topicFilters);

        ClientSession prevClientSession = clientSessionService.getClientSession(clientId);
        ClientSession clientSession = prevClientSession.toBuilder().build();
        Set<TopicSubscription> persistedTopicSubscriptions = clientSession.getTopicSubscriptions();
        persistedTopicSubscriptions.removeIf(topicSubscription -> topicFilters.contains(topicSubscription.getTopic()));
        clientSessionService.replaceClientSession(clientId, prevClientSession, clientSession);
    }

    @Override
    public void clearSubscriptions(String clientId) {
        log.trace("[{}] Clearing all subscriptions.", clientId);
        ClientSession prevClientSession = clientSessionService.getClientSession(clientId);
        List<String> unsubscribeTopics = prevClientSession.getTopicSubscriptions().stream()
                .map(TopicSubscription::getTopic)
                .collect(Collectors.toList());
        subscriptionService.unsubscribe(clientId, unsubscribeTopics);
        ClientSession clientSession = prevClientSession.toBuilder().topicSubscriptions(new HashSet<>()).build();
        clientSessionService.replaceClientSession(clientId, prevClientSession, clientSession);
    }
}
