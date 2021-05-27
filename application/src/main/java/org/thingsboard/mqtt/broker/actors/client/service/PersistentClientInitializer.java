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
package org.thingsboard.mqtt.broker.actors.client.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.TbActorRef;
import org.thingsboard.mqtt.broker.actors.TbActorSystem;
import org.thingsboard.mqtt.broker.actors.TbTypeActorId;
import org.thingsboard.mqtt.broker.actors.config.ActorSystemLifecycle;
import org.thingsboard.mqtt.broker.actors.client.ClientActorCreator;
import org.thingsboard.mqtt.broker.actors.client.messages.SubscriptionChangedEventMsg;
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionListener;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionManager;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.id.ActorType;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionInfo;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionConsumer;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PersistentClientInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final ClientSubscriptionConsumer clientSubscriptionConsumer;
    private final ClientSessionConsumer clientSessionConsumer;

    private final SubscriptionManager subscriptionManager;
    private final ClientSessionListener clientSessionListener;

    private final ActorSystemContext actorSystemContext;
    private final TbActorSystem actorSystem;

    private final ClientSessionEventService clientSessionEventService;
    private final ServiceInfoProvider serviceInfoProvider;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Map<String, ClientSessionInfo> allClientSessions = initClientSessions();

        initClientSubscriptions(allClientSessions);


        Map<String, ClientSessionInfo> currentNodeSessions = allClientSessions.values().stream()
                .filter(this::sessionWasOnThisNode)
                .collect(Collectors.toMap(clientSessionInfo -> clientSessionInfo.getClientSession().getSessionInfo().getClientInfo().getClientId(),
                        Function.identity()));
        clearNonPersistentClients(currentNodeSessions);

        clientSessionListener.startListening(clientSessionConsumer);

        startSubscriptionListening();
    }

    private void initClientSubscriptions(Map<String, ClientSessionInfo> allClientSessions) {
        Map<String, Set<TopicSubscription>> allClientSubscriptions = clientSubscriptionConsumer.initLoad();
        log.info("Loaded {} persisted client subscriptions.", allClientSubscriptions.size());
        Set<String> loadedClientIds = new HashSet<>(allClientSubscriptions.keySet());
        for (String clientId : loadedClientIds) {
            if (!allClientSessions.containsKey(clientId)) {
                allClientSubscriptions.remove(clientId);
            }
        }
        log.info("Initializing SubscriptionManager with {} client subscriptions.", allClientSubscriptions.size());
        subscriptionManager.init(allClientSubscriptions);
    }

    private Map<String, ClientSessionInfo> initClientSessions() {
        Map<String, ClientSessionInfo> allClientSessions = clientSessionConsumer.initLoad();
        log.info("Loaded {} persisted client sessions.", allClientSessions.size());

        Map<String, ClientSessionInfo> currentNodeSessions = allClientSessions.values().stream()
                .filter(this::sessionWasOnThisNode)
                .map(this::markDisconnected)
                .collect(Collectors.toMap(clientSessionInfo -> clientSessionInfo.getClientSession().getSessionInfo().getClientInfo().getClientId(),
                        Function.identity()));
        log.info("{} client sessions were on {} node.", currentNodeSessions.size(), serviceInfoProvider.getServiceId());

        allClientSessions.putAll(currentNodeSessions);

        clientSessionListener.init(allClientSessions);

        return allClientSessions;
    }

    private void startSubscriptionListening() {
        clientSubscriptionConsumer.listen((clientId, serviceId, topicSubscriptions) -> {
            if (serviceInfoProvider.getServiceId().equals(serviceId)) {
                log.trace("[{}] Msg was already processed.", clientId);
                return;
            }

            TbActorRef clientActorRef = actorSystem.getActor(new TbTypeActorId(ActorType.CLIENT, clientId));
            if (clientActorRef == null) {
                // TODO: get ClientInfo and check if clientId is generated
                clientActorRef = actorSystem.createRootActor(ActorSystemLifecycle.CLIENT_DISPATCHER_NAME,
                        new ClientActorCreator(actorSystemContext, clientId, true));
            }

            clientActorRef.tellWithHighPriority(new SubscriptionChangedEventMsg(topicSubscriptions));
        });
    }

    public void clearNonPersistentClients(Map<String, ClientSessionInfo> thisNodeClientSessions) {
        thisNodeClientSessions.values().stream()
                .filter(clientSessionInfo -> !isPersistent(clientSessionInfo))
                .map(clientSessionInfo -> clientSessionInfo.getClientSession().getSessionInfo())
                .forEach(clientSessionEventService::tryClear);
    }

    private boolean isPersistent(ClientSessionInfo clientSessionInfo) {
        return clientSessionInfo.getClientSession().getSessionInfo().isPersistent();
    }

    private boolean sessionWasOnThisNode(ClientSessionInfo clientSessionInfo) {
        return serviceInfoProvider.getServiceId()
                .equals(clientSessionInfo.getClientSession().getSessionInfo().getServiceId());
    }

    private ClientSessionInfo markDisconnected(ClientSessionInfo clientSessionInfo) {
        return clientSessionInfo.toBuilder()
                .clientSession(
                        clientSessionInfo.getClientSession().toBuilder()
                                .connected(false)
                                .build()
                )
                .build();
    }
}
