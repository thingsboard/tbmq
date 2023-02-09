/**
 * Copyright © 2016-2023 The Thingsboard Authors
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

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientSubscriptionsQueueFactory;
import org.thingsboard.mqtt.broker.service.stats.ClientSubscriptionConsumerStats;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import javax.annotation.PreDestroy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.thingsboard.mqtt.broker.util.BytesUtil.bytesToString;

@Slf4j
@Component
public class ClientSubscriptionConsumerImpl implements ClientSubscriptionConsumer {
    private static final String DUMMY_TOPIC = "dummy_topic";

    private volatile boolean initializing = true;
    private volatile boolean stopped = false;

    private final ExecutorService consumerExecutor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("client-subscriptions-listener"));

    @Value("${queue.client-subscriptions.poll-interval}")
    private long pollDuration;

    private final SubscriptionPersistenceService persistenceService;
    private final TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSubscriptionsProto>> clientSubscriptionsConsumer;
    private final TbQueueAdmin queueAdmin;
    private final ClientSubscriptionConsumerStats stats;

    public ClientSubscriptionConsumerImpl(ClientSubscriptionsQueueFactory clientSubscriptionsQueueFactory, ServiceInfoProvider serviceInfoProvider,
                                          SubscriptionPersistenceService persistenceService, TbQueueAdmin queueAdmin, StatsManager statsManager) {
        String uniqueConsumerGroupId = serviceInfoProvider.getServiceId() + "-" + System.currentTimeMillis();
        this.clientSubscriptionsConsumer = clientSubscriptionsQueueFactory.createConsumer(serviceInfoProvider.getServiceId(), uniqueConsumerGroupId);
        this.persistenceService = persistenceService;
        this.queueAdmin = queueAdmin;
        this.stats = statsManager.getClientSubscriptionConsumerStats();
    }

    @Override
    public Map<String, Set<TopicSubscription>> initLoad() throws QueuePersistenceException {
        String dummyClientId = persistDummyClientSubscriptions();

        clientSubscriptionsConsumer.subscribe();

        List<TbProtoQueueMsg<QueueProtos.ClientSubscriptionsProto>> messages;
        boolean encounteredDummyClient = false;
        Map<String, Set<TopicSubscription>> allSubscriptions = new HashMap<>();
        do {
            try {
                // TODO: think how to migrate data inside of the Kafka (in case of any changes to the protocol)
                messages = clientSubscriptionsConsumer.poll(pollDuration);
                for (TbProtoQueueMsg<QueueProtos.ClientSubscriptionsProto> msg : messages) {
                    String clientId = msg.getKey();
                    // TODO: replace with events (instead of storing the whole state) - but need to think about the logic when and how to make snapshots (so that we don't need to store all event log)
                    //          also think about order of messages (sub A -> unsub A is different than unsub A -> sub A)
                    Set<TopicSubscription> clientSubscriptions = ProtoConverter.convertToClientSubscriptions(msg.getValue());
                    if (dummyClientId.equals(clientId)) {
                        encounteredDummyClient = true;
                    } else if (clientSubscriptions.isEmpty()) {
                        // this means Kafka log compaction service haven't cleared empty message yet
                        log.debug("[{}] Encountered empty ClientSubscriptions.", clientId);
                        allSubscriptions.remove(clientId);
                    } else {
                        allSubscriptions.put(clientId, clientSubscriptions);
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

        return allSubscriptions;
    }

    @Override
    public void listen(ClientSubscriptionChangesCallback callback) {
        if (initializing) {
            throw new RuntimeException("Cannot start listening before initialization is finished.");
        }
        // TODO: add concurrent consumers for multiple partitions (in the same consumer-group as InitLoader)
        consumerExecutor.execute(() -> {
            while (!stopped) {
                try {
                    List<TbProtoQueueMsg<QueueProtos.ClientSubscriptionsProto>> messages = clientSubscriptionsConsumer.poll(pollDuration);
                    if (messages.isEmpty()) {
                        continue;
                    }
                    stats.logTotal(messages.size());
                    int acceptedSubscriptions = 0;
                    int ignoredSubscriptions = 0;
                    for (TbProtoQueueMsg<QueueProtos.ClientSubscriptionsProto> msg : messages) {
                        String clientId = msg.getKey();
                        String serviceId = bytesToString(msg.getHeaders().get(BrokerConstants.SERVICE_ID_HEADER));
                        Set<TopicSubscription> clientSubscriptions = ProtoConverter.convertToClientSubscriptions(msg.getValue());
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
                            log.trace("Failed to wait until the server has capacity to handle new requests", e2);
                        }
                    }
                }
            }
        });
    }

    private String persistDummyClientSubscriptions() throws QueuePersistenceException {
        String dummyClientId = UUID.randomUUID().toString();
        persistenceService.persistClientSubscriptionsSync(dummyClientId, Collections.singleton(new TopicSubscription(DUMMY_TOPIC, 0)));
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
        consumerExecutor.shutdownNow();
    }
}
