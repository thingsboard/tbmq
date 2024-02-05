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
package org.thingsboard.mqtt.broker.actors.client.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.TbActorRef;
import org.thingsboard.mqtt.broker.actors.TbActorSystem;
import org.thingsboard.mqtt.broker.actors.TbTypeActorId;
import org.thingsboard.mqtt.broker.actors.client.ClientActorCreator;
import org.thingsboard.mqtt.broker.actors.client.messages.SubscriptionChangedEventMsg;
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionService;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.actors.config.ActorSystemLifecycle;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.id.ActorType;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.queue.DeviceMsgQueueConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerService;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgConsumerService;
import org.thingsboard.mqtt.broker.service.processing.downlink.basic.BasicDownLinkConsumer;
import org.thingsboard.mqtt.broker.service.processing.downlink.persistent.PersistentDownLinkConsumer;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionConsumer;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrokerInitializer {

    private final ClientSubscriptionConsumer clientSubscriptionConsumer;
    private final ClientSessionConsumer clientSessionConsumer;
    private final RetainedMsgConsumer retainedMsgConsumer;

    private final ClientSubscriptionService clientSubscriptionService;
    private final ClientSessionService clientSessionService;
    private final RetainedMsgListenerService retainedMsgListenerService;

    private final ActorSystemContext actorSystemContext;
    private final TbActorSystem actorSystem;

    private final ClientSessionEventService clientSessionEventService;
    private final ServiceInfoProvider serviceInfoProvider;

    private final DisconnectClientCommandConsumer disconnectClientCommandConsumer;
    private final ClientSessionEventConsumer clientSessionEventConsumer;
    private final DeviceMsgQueueConsumer deviceMsgQueueConsumer;
    private final PublishMsgConsumerService publishMsgConsumerService;
    private final BasicDownLinkConsumer basicDownLinkConsumer;
    private final PersistentDownLinkConsumer persistentDownLinkConsumer;

    @EventListener(ApplicationReadyEvent.class)
    @Order(value = 1)
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("Initializing Client Sessions and Subscriptions.");
        try {
            Map<String, ClientSessionInfo> allClientSessions = initClientSessions();

            initClientSubscriptions(allClientSessions);

            clearNonPersistentClientsOnCurrentNode(allClientSessions);

            clientSessionService.startListening(clientSessionConsumer);

            startSubscriptionListening();

            initRetainedMessages();
            retainedMsgListenerService.startListening(retainedMsgConsumer);

            log.info("Starting Queue consumers that depend on Client Sessions or Subscriptions.");
            startConsuming();
        } catch (Exception e) {
            log.error("Failed to initialize broker", e);
            throw new RuntimeException(e);
        }
    }

    private void initRetainedMessages() throws QueuePersistenceException {
        Map<String, RetainedMsg> allRetainedMessages = retainedMsgConsumer.initLoad();
        log.info("Loaded {} stored retained messages from Kafka.", allRetainedMessages.size());
        retainedMsgListenerService.init(allRetainedMessages);
    }

    private void clearNonPersistentClientsOnCurrentNode(Map<String, ClientSessionInfo> allClientSessions) {
        Map<String, ClientSessionInfo> currentNodeSessions = filterSessions(allClientSessions);
        clearNonPersistentClients(currentNodeSessions);
    }

    private void startConsuming() {
        disconnectClientCommandConsumer.startConsuming();
        clientSessionEventConsumer.startConsuming();
        deviceMsgQueueConsumer.startConsuming();
        publishMsgConsumerService.startConsuming();
        basicDownLinkConsumer.startConsuming();
        persistentDownLinkConsumer.startConsuming();
    }

    private void initClientSubscriptions(Map<String, ClientSessionInfo> allClientSessions) throws QueuePersistenceException {
        Map<String, Set<TopicSubscription>> allClientSubscriptions = clientSubscriptionConsumer.initLoad();
        log.info("Loaded {} stored client subscriptions from Kafka.", allClientSubscriptions.size());

        removeSubscriptionIfSessionIsAbsent(allClientSessions, allClientSubscriptions);

        log.info("Initializing SubscriptionManager with {} client subscriptions.", allClientSubscriptions.size());
        clientSubscriptionService.init(allClientSubscriptions);
    }

    private void removeSubscriptionIfSessionIsAbsent(Map<String, ClientSessionInfo> allClientSessions,
                                                     Map<String, Set<TopicSubscription>> allClientSubscriptions) {
        Set<String> loadedClientIds = new HashSet<>(allClientSubscriptions.keySet());
        for (String clientId : loadedClientIds) {
            if (!allClientSessions.containsKey(clientId)) {
                allClientSubscriptions.remove(clientId);
            }
        }
    }

    Map<String, ClientSessionInfo> initClientSessions() throws QueuePersistenceException {
        Map<String, ClientSessionInfo> allClientSessions = clientSessionConsumer.initLoad();
        log.info("Loaded {} stored client sessions from Kafka.", allClientSessions.size());

        Map<String, ClientSessionInfo> currentNodeSessions = filterSessionsAndDisconnect(allClientSessions);
        log.info("{} client sessions were on {} node.", currentNodeSessions.size(), serviceInfoProvider.getServiceId());

        allClientSessions.putAll(currentNodeSessions);

        clientSessionService.init(allClientSessions);

        return allClientSessions;
    }

    private Map<String, ClientSessionInfo> filterSessions(Map<String, ClientSessionInfo> allClientSessions) {
        return filterCurrentNodeSessions(allClientSessions)
                .collect(Collectors.toMap(this::getClientId, Function.identity()));
    }

    private Map<String, ClientSessionInfo> filterSessionsAndDisconnect(Map<String, ClientSessionInfo> allClientSessions) {
        return filterCurrentNodeSessions(allClientSessions)
                .map(this::markDisconnected)
                .collect(Collectors.toMap(this::getClientId, Function.identity()));
    }

    private Stream<ClientSessionInfo> filterCurrentNodeSessions(Map<String, ClientSessionInfo> allClientSessions) {
        return allClientSessions.values().stream()
                .filter(this::sessionWasOnThisNode);
    }

    private void startSubscriptionListening() {
        clientSubscriptionConsumer.listen((clientId, serviceId, topicSubscriptions) -> {
            if (serviceInfoProvider.getServiceId().equals(serviceId)) {
                if (log.isTraceEnabled()) {
                    log.trace("[{}] Msg was already processed.", clientId);
                }
                return false;
            }

            TbActorRef clientActorRef = getActor(clientId);
            if (clientActorRef == null) {
                // TODO: get ClientInfo and check if clientId is generated
                clientActorRef = createRootActor(clientId);
            }

            clientActorRef.tellWithHighPriority(new SubscriptionChangedEventMsg(topicSubscriptions));
            return true;
        });
    }

    private TbActorRef createRootActor(String clientId) {
        return actorSystem.createRootActor(ActorSystemLifecycle.CLIENT_DISPATCHER_NAME,
                new ClientActorCreator(actorSystemContext, clientId, true));
    }

    private TbActorRef getActor(String clientId) {
        return actorSystem.getActor(new TbTypeActorId(ActorType.CLIENT, clientId));
    }

    void clearNonPersistentClients(Map<String, ClientSessionInfo> currentNodeSessions) {
        currentNodeSessions.values().stream()
                .map(ClientSessionInfoFactory::clientSessionInfoToSessionInfo)
                .filter(this::isCleanSession)
                .forEach(clientSessionEventService::requestSessionCleanup);
    }

    boolean isCleanSession(SessionInfo sessionInfo) {
        return sessionInfo.isCleanSession();
    }

    private boolean sessionWasOnThisNode(ClientSessionInfo clientSessionInfo) {
        return serviceInfoProvider.getServiceId()
                .equals(clientSessionInfo.getServiceId());
    }

    private ClientSessionInfo markDisconnected(ClientSessionInfo clientSessionInfo) {
        return clientSessionInfo.toBuilder().connected(false).build();
    }

    private String getClientId(ClientSessionInfo clientSessionInfo) {
        return clientSessionInfo.getClientId();
    }
}
