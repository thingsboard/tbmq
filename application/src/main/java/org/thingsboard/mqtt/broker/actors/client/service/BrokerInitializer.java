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
package org.thingsboard.mqtt.broker.actors.client.service;

import com.google.common.collect.Maps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionService;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.limits.RateLimitCacheService;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.BlockedClientService;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.consumer.BlockedClientConsumerService;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.queue.DeviceMsgQueueConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerService;
import org.thingsboard.mqtt.broker.service.notification.InternodeNotificationsConsumer;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgConsumerService;
import org.thingsboard.mqtt.broker.service.processing.downlink.basic.BasicDownLinkConsumer;
import org.thingsboard.mqtt.broker.service.processing.downlink.persistent.PersistentDownLinkConsumer;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionConsumer;
import org.thingsboard.mqtt.broker.service.subscription.data.SubscriptionsSourceKey;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.thingsboard.mqtt.broker.service.subscription.data.SubscriptionsSource.MQTT_CLIENT;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrokerInitializer {

    private final ClientSessionConsumer clientSessionConsumer;
    private final ClientSubscriptionConsumer clientSubscriptionConsumer;
    private final RetainedMsgConsumer retainedMsgConsumer;
    private final BlockedClientConsumerService blockedClientConsumer;

    private final ClientSessionService clientSessionService;
    private final ClientSubscriptionService clientSubscriptionService;
    private final RetainedMsgListenerService retainedMsgListenerService;
    private final BlockedClientService blockedClientService;

    private final ClientSessionEventService clientSessionEventService;
    private final ServiceInfoProvider serviceInfoProvider;
    private final RateLimitCacheService rateLimitCacheService;

    private final ClientSessionEventConsumer clientSessionEventConsumer;
    private final PublishMsgConsumerService publishMsgConsumerService;
    private final DisconnectClientCommandConsumer disconnectClientCommandConsumer;
    private final DeviceMsgQueueConsumer deviceMsgQueueConsumer;
    private final BasicDownLinkConsumer basicDownLinkConsumer;
    private final PersistentDownLinkConsumer persistentDownLinkConsumer;
    private final InternodeNotificationsConsumer internodeNotificationsConsumer;

    @EventListener(ApplicationReadyEvent.class)
    @Order(value = 1)
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("Initializing Client Sessions and Subscriptions.");
        try {
            Map<String, ClientSessionInfo> allClientSessions = initClientSessions();
            initClientSubscriptions(allClientSessions);

            clientSessionService.startListening(clientSessionConsumer);
            clientSubscriptionService.startListening(clientSubscriptionConsumer);

            initRetainedMessages();
            retainedMsgListenerService.startListening(retainedMsgConsumer);

            initBlockedClients();
            blockedClientService.startListening(blockedClientConsumer);

            log.info("Starting Queue consumers that depend on Client Sessions or Subscriptions.");
            startConsuming();
        } catch (Exception e) {
            log.error("Failed to initialize broker", e);
            throw new RuntimeException(e);
        }
    }

    Map<String, ClientSessionInfo> initClientSessions() throws QueuePersistenceException {
        Map<String, ClientSessionInfo> allClientSessions = clientSessionConsumer.initLoad();
        log.info("Loaded {} stored client sessions from Kafka.", allClientSessions.size());
        rateLimitCacheService.initSessionCount(allClientSessions.size());

        Map<String, ClientSessionInfo> currentNodeSessions = filterAndDisconnectCurrentNodeSessions(allClientSessions);
        allClientSessions.putAll(currentNodeSessions);

        clientSessionService.init(allClientSessions);
        return allClientSessions;
    }

    private Map<String, ClientSessionInfo> filterAndDisconnectCurrentNodeSessions(Map<String, ClientSessionInfo> allClientSessions) {
        Map<String, ClientSessionInfo> currentNodeSessions = Maps.newHashMapWithExpectedSize(allClientSessions.size());
        int applicationClientsCount = 0;
        for (Map.Entry<String, ClientSessionInfo> entry : allClientSessions.entrySet()) {
            ClientSessionInfo clientSessionInfo = entry.getValue();

            if (clientSessionInfo.isPersistentAppClient()) {
                applicationClientsCount++;
            }

            if (sessionWasOnThisNode(clientSessionInfo)) {
                if (isCleanSession(clientSessionInfo)) {
                    clientSessionEventService.requestClientSessionCleanup(clientSessionInfo);
                }
                ClientSessionInfo disconnectedClientSession = markDisconnected(clientSessionInfo);
                currentNodeSessions.put(entry.getKey(), disconnectedClientSession);
            }
        }
        rateLimitCacheService.initApplicationClientsCount(applicationClientsCount);
        log.info("{} client sessions were on {} node.", currentNodeSessions.size(), serviceInfoProvider.getServiceId());
        return currentNodeSessions;
    }

    void initRetainedMessages() throws QueuePersistenceException {
        Map<String, RetainedMsg> allRetainedMessages = retainedMsgConsumer.initLoad();
        log.info("Loaded {} stored retained messages from Kafka.", allRetainedMessages.size());
        retainedMsgListenerService.init(allRetainedMessages);
    }

    void initBlockedClients() throws QueuePersistenceException {
        Map<String, BlockedClient> allBlockedClients = blockedClientConsumer.initLoad();
        log.info("Loaded {} stored blocked clients from Kafka", allBlockedClients.size());
        blockedClientService.init(allBlockedClients);
    }

    private void startConsuming() {
        clientSessionEventConsumer.startConsuming();
        publishMsgConsumerService.startConsuming();
        disconnectClientCommandConsumer.startConsuming();
        deviceMsgQueueConsumer.startConsuming();
        basicDownLinkConsumer.startConsuming();
        persistentDownLinkConsumer.startConsuming();
        internodeNotificationsConsumer.startConsuming();
    }

    void initClientSubscriptions(Map<String, ClientSessionInfo> allClientSessions) throws QueuePersistenceException {
        Map<SubscriptionsSourceKey, Set<TopicSubscription>> allClientSubscriptions = clientSubscriptionConsumer.initLoad();
        log.info("Loaded {} stored client subscriptions from Kafka.", allClientSubscriptions.size());

        removeSubscriptionIfSessionIsAbsent(allClientSessions, allClientSubscriptions);

        log.info("Initializing SubscriptionManager with {} client subscriptions.", allClientSubscriptions.size());
        clientSubscriptionService.init(allClientSubscriptions);
    }

    private void removeSubscriptionIfSessionIsAbsent(Map<String, ClientSessionInfo> allClientSessions,
                                                     Map<SubscriptionsSourceKey, Set<TopicSubscription>> allClientSubscriptions) {
        for (SubscriptionsSourceKey sourceKey : new HashSet<>(allClientSubscriptions.keySet())) {
            if (MQTT_CLIENT.equals(sourceKey.getSource())) {
                if (!allClientSessions.containsKey(sourceKey.getId())) {
                    allClientSubscriptions.remove(sourceKey);
                }
            }
        }
    }

    boolean isCleanSession(ClientSessionInfo clientSessionInfo) {
        return clientSessionInfo.isCleanSession();
    }

    private boolean sessionWasOnThisNode(ClientSessionInfo clientSessionInfo) {
        return serviceInfoProvider.getServiceId().equals(clientSessionInfo.getServiceId());
    }

    private ClientSessionInfo markDisconnected(ClientSessionInfo clientSessionInfo) {
        return clientSessionInfo.toBuilder().connected(false).build();
    }
}
