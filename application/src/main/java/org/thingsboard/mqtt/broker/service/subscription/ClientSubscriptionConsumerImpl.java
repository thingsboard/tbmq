/**
 * Copyright © 2016-2026 The Thingsboard Authors
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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.subscription.ClientTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.ClientSubscriptionsProto;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientSubscriptionsQueueFactory;
import org.thingsboard.mqtt.broker.service.stats.ClientSubscriptionConsumerStats;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.subscription.data.SourcedSubscriptions;
import org.thingsboard.mqtt.broker.service.subscription.data.SubscriptionsSourceKey;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.thingsboard.mqtt.broker.common.data.util.BytesUtil.bytesToString;

@Slf4j
@Component
public class ClientSubscriptionConsumerImpl implements ClientSubscriptionConsumer {

    private final ExecutorService consumerExecutor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("client-subscriptions-listener"));

    private final ClientSubscriptionsQueueFactory clientSubscriptionsQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final SubscriptionPersistenceService persistenceService;
    private final TbQueueAdmin queueAdmin;
    private final ClientSubscriptionConsumerStats stats;

    public ClientSubscriptionConsumerImpl(ClientSubscriptionsQueueFactory clientSubscriptionsQueueFactory, ServiceInfoProvider serviceInfoProvider,
                                          SubscriptionPersistenceService persistenceService, TbQueueAdmin queueAdmin, StatsManager statsManager) {
        this.clientSubscriptionsQueueFactory = clientSubscriptionsQueueFactory;
        this.serviceInfoProvider = serviceInfoProvider;
        this.persistenceService = persistenceService;
        this.queueAdmin = queueAdmin;
        this.stats = statsManager.getClientSubscriptionConsumerStats();
    }

    @Value("${queue.client-subscriptions.poll-interval}")
    private long pollDuration;

    private volatile boolean initializing = true;
    private volatile boolean stopped = false;

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<ClientSubscriptionsProto>> clientSubscriptionsConsumer;

    @PostConstruct
    public void init() {
        long currentCgSuffix = System.currentTimeMillis();
        String uniqueConsumerGroupId = serviceInfoProvider.getServiceId() + "-" + currentCgSuffix;
        this.clientSubscriptionsConsumer = clientSubscriptionsQueueFactory.createConsumer(serviceInfoProvider.getServiceId(), uniqueConsumerGroupId);
        queueAdmin.deleteOldConsumerGroups(BrokerConstants.CLIENT_SUBSCRIPTIONS_CG_PREFIX, serviceInfoProvider.getServiceId(), currentCgSuffix);
    }

    @Override
    public Map<SubscriptionsSourceKey, Set<TopicSubscription>> initLoad() throws QueuePersistenceException {
        log.debug("Starting subscriptions initLoad");
        long startTime = System.nanoTime();
        long totalMessageCount = 0L;

        String dummyClientId = persistDummyClientSubscriptions();
        clientSubscriptionsConsumer.assignOrSubscribe();

        List<TbProtoQueueMsg<ClientSubscriptionsProto>> messages;
        boolean encounteredDummyClient = false;
        Map<SubscriptionsSourceKey, Set<TopicSubscription>> allSubscriptions = new HashMap<>();
        do {
            try {
                messages = clientSubscriptionsConsumer.poll(pollDuration);
                int packSize = messages.size();
                log.debug("Read {} subscription messages from single poll", packSize);
                totalMessageCount += packSize;
                for (TbProtoQueueMsg<ClientSubscriptionsProto> msg : messages) {
                    String clientId = msg.getKey();
                    SourcedSubscriptions sourcedSubscriptions = ProtoConverter.convertProtoToClientSubscriptions(msg.getValue());
                    if (dummyClientId.equals(clientId)) {
                        encounteredDummyClient = true;
                    } else if (sourcedSubscriptions.getSubscriptions().isEmpty()) {
                        // this means Kafka log compaction service haven't cleared empty message yet
                        log.trace("[{}] Encountered empty ClientSubscriptions.", clientId);
                        allSubscriptions.remove(SubscriptionsSourceKey.newInstance(clientId));
                    } else {
                        allSubscriptions.put(new SubscriptionsSourceKey(clientId, sourcedSubscriptions.getSource()), sourcedSubscriptions.getSubscriptions());
                    }
                }
                clientSubscriptionsConsumer.commitSync();
            } catch (Exception e) {
                log.error("Failed to load client sessions.", e);
                throw e;
            }
        } while (!stopped && !encounteredDummyClient);

        clearDummyClientSubscriptions(dummyClientId);

        initializing = false;

        if (log.isDebugEnabled()) {
            long endTime = System.nanoTime();
            log.debug("Finished subscriptions initLoad for {} messages within time: {} nanos", totalMessageCount, endTime - startTime);
        }

        return allSubscriptions;
    }

    @Override
    public void listen(ClientSubscriptionChangesCallback callback) {
        if (initializing) {
            throw new RuntimeException("Cannot start listening before initialization is finished.");
        }
        consumerExecutor.execute(() -> {
            while (!stopped) {
                try {
                    List<TbProtoQueueMsg<ClientSubscriptionsProto>> messages = clientSubscriptionsConsumer.poll(pollDuration);
                    if (messages.isEmpty()) {
                        continue;
                    }
                    stats.logTotal(messages.size());
                    int acceptedSubscriptions = 0;
                    int ignoredSubscriptions = 0;
                    for (TbProtoQueueMsg<ClientSubscriptionsProto> msg : messages) {
                        String clientId = msg.getKey();
                        if (clientId.startsWith(BrokerConstants.SYSTEM_DUMMY_CLIENT_ID_PREFIX)) {
                            ignoredSubscriptions++;
                            continue;
                        }
                        String serviceId = bytesToString(msg.getHeaders().get(BrokerConstants.SERVICE_ID_HEADER));
                        Set<TopicSubscription> clientSubscriptions = ProtoConverter.convertProtoToClientSubscriptions(msg.getValue()).getSubscriptions();
                        boolean accepted = callback.accept(clientId, serviceId, clientSubscriptions);
                        if (accepted) {
                            acceptedSubscriptions++;
                        } else {
                            ignoredSubscriptions++;
                        }
                    }
                    stats.log(acceptedSubscriptions, ignoredSubscriptions);
                    clientSubscriptionsConsumer.commitSync();
                } catch (Exception e) {
                    if (!stopped) {
                        log.error("Failed to process messages from queue.", e);
                        try {
                            Thread.sleep(pollDuration);
                        } catch (InterruptedException e2) {
                            if (log.isTraceEnabled()) {
                                log.trace("Failed to wait until the server has capacity to handle new requests", e2);
                            }
                        }
                    }
                }
            }
        });
    }

    private String persistDummyClientSubscriptions() throws QueuePersistenceException {
        String dummyClientId = BrokerConstants.SYSTEM_DUMMY_CLIENT_ID_PREFIX + UUID.randomUUID();
        persistenceService.persistClientSubscriptionsSync(dummyClientId, Collections.singleton(new ClientTopicSubscription(BrokerConstants.SYSTEM_DUMMY_TOPIC_FILTER, 0)));
        return dummyClientId;
    }

    private void clearDummyClientSubscriptions(String clientId) throws QueuePersistenceException {
        persistenceService.persistClientSubscriptionsSync(clientId, Collections.emptySet());
    }

    @PreDestroy
    public void destroy() {
        stopped = true;
        if (clientSubscriptionsConsumer != null) {
            clientSubscriptionsConsumer.unsubscribeAndClose();
            if (this.clientSubscriptionsConsumer.getConsumerGroupId() != null) {
                queueAdmin.deleteConsumerGroups(Collections.singleton(this.clientSubscriptionsConsumer.getConsumerGroupId()));
            }
        }
        ThingsBoardExecutors.shutdownAndAwaitTermination(consumerExecutor, "Client subscriptions consumer");
    }
}
