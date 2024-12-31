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
package org.thingsboard.mqtt.broker.service.mqtt.retain;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.util.BytesUtil;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.constants.QueueConstants;
import org.thingsboard.mqtt.broker.queue.provider.RetainedMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.stats.RetainedMsgConsumerStats;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class RetainedMsgConsumerImpl implements RetainedMsgConsumer {

    private static final String DUMMY_TOPIC_PREFIX = "dummy/topic/";

    private final ExecutorService consumerExecutor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("retained-msg-listener"));

    private final RetainedMsgQueueFactory retainedMsgQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final RetainedMsgPersistenceService persistenceService;
    private final TbQueueAdmin queueAdmin;
    private final RetainedMsgConsumerStats stats;

    public RetainedMsgConsumerImpl(RetainedMsgQueueFactory retainedMsgQueueFactory, ServiceInfoProvider serviceInfoProvider,
                                   RetainedMsgPersistenceService persistenceService, TbQueueAdmin queueAdmin, StatsManager statsManager) {
        this.retainedMsgQueueFactory = retainedMsgQueueFactory;
        this.serviceInfoProvider = serviceInfoProvider;
        this.persistenceService = persistenceService;
        this.queueAdmin = queueAdmin;
        this.stats = statsManager.getRetainedMsgConsumerStats();
    }

    @Value("${queue.retained-msg.poll-interval}")
    private long pollDuration;

    private volatile boolean initializing = true;
    private volatile boolean stopped = false;

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.RetainedMsgProto>> retainedMsgConsumer;

    @PostConstruct
    public void init() {
        long currentCgSuffix = System.currentTimeMillis();
        String uniqueConsumerGroupId = serviceInfoProvider.getServiceId() + "-" + currentCgSuffix;
        this.retainedMsgConsumer = retainedMsgQueueFactory.createConsumer(serviceInfoProvider.getServiceId(), uniqueConsumerGroupId);
        queueAdmin.deleteOldConsumerGroups(BrokerConstants.RETAINED_MSG_CG_PREFIX, serviceInfoProvider.getServiceId(), currentCgSuffix);
    }

    @Override
    public Map<String, RetainedMsg> initLoad() throws QueuePersistenceException {
        log.debug("Starting retained messages initLoad");
        long startTime = System.nanoTime();
        long totalMessageCount = 0L;

        String dummyTopic = persistDummyRetainedMsg();
        retainedMsgConsumer.assignOrSubscribe();

        List<TbProtoQueueMsg<QueueProtos.RetainedMsgProto>> messages;
        boolean encounteredDummyTopic = false;
        Map<String, RetainedMsg> allRetainedMsgs = new HashMap<>();
        do {
            try {
                messages = retainedMsgConsumer.poll(pollDuration);
                int packSize = messages.size();
                log.debug("Read {} retained messages from single poll", packSize);
                totalMessageCount += packSize;
                for (TbProtoQueueMsg<QueueProtos.RetainedMsgProto> msg : messages) {
                    String topic = msg.getKey();
                    if (topic.startsWith(BrokerConstants.SYSTEMS_TOPIC_PREFIX)) {
                        continue;
                    }
                    if (isRetainedMsgProtoEmpty(msg.getValue())) {
                        // this means Kafka log compaction service haven't cleared empty message yet
                        log.trace("[{}] Encountered empty RetainedMsg.", topic);
                        allRetainedMsgs.remove(topic);
                    } else {
                        RetainedMsg retainedMsg = convertToRetainedMsg(msg);
                        if (dummyTopic.equals(topic)) {
                            encounteredDummyTopic = true;
                        } else {
                            allRetainedMsgs.put(topic, retainedMsg);
                        }
                    }
                }
                retainedMsgConsumer.commitSync();
            } catch (Exception e) {
                log.error("Failed to load retained messages.", e);
                throw e;
            }
        } while (!stopped && !encounteredDummyTopic);

        clearDummyRetainedMsg(dummyTopic);

        initializing = false;

        if (log.isDebugEnabled()) {
            long endTime = System.nanoTime();
            log.debug("Finished retained messages initLoad for {} messages within time: {} nanos", totalMessageCount, endTime - startTime);
        }

        return allRetainedMsgs;
    }

    @Override
    public void listen(RetainedMsgChangesCallback callback) {
        if (initializing) {
            throw new RuntimeException("Cannot start listening before retained messages initialization is finished.");
        }
        consumerExecutor.execute(() -> {
            while (!stopped) {
                try {
                    List<TbProtoQueueMsg<QueueProtos.RetainedMsgProto>> messages = retainedMsgConsumer.poll(pollDuration);
                    if (messages.isEmpty()) {
                        continue;
                    }
                    stats.logTotal(messages.size());
                    int newRetainedMsgCount = 0;
                    int clearedRetainedMsgCount = 0;
                    for (TbProtoQueueMsg<QueueProtos.RetainedMsgProto> msg : messages) {
                        String topic = msg.getKey();
                        String serviceId = BytesUtil.bytesToString(msg.getHeaders().get(BrokerConstants.SERVICE_ID_HEADER));

                        if (isRetainedMsgProtoEmpty(msg.getValue())) {
                            callback.accept(topic, serviceId, null);
                            clearedRetainedMsgCount++;
                        } else {
                            RetainedMsg retainedMsg = convertToRetainedMsg(msg);
                            callback.accept(topic, serviceId, retainedMsg);
                            newRetainedMsgCount++;
                        }
                    }
                    stats.log(newRetainedMsgCount, clearedRetainedMsgCount);
                    retainedMsgConsumer.commitSync();
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

    private static RetainedMsg convertToRetainedMsg(TbProtoQueueMsg<QueueProtos.RetainedMsgProto> msg) {
        RetainedMsg retainedMsg = ProtoConverter.convertProtoToRetainedMsg(msg.getValue());
        MqttPropertiesUtil.addMsgExpiryIntervalToProps(retainedMsg.getProperties(), msg.getHeaders());
        return retainedMsg;
    }

    private String persistDummyRetainedMsg() throws QueuePersistenceException {
        String dummyTopic = DUMMY_TOPIC_PREFIX + RandomStringUtils.randomAlphanumeric(8);
        RetainedMsg retainedMsg = new RetainedMsg(dummyTopic, BrokerConstants.DUMMY_PAYLOAD, 0);
        persistenceService.persistRetainedMsgSync(dummyTopic, ProtoConverter.convertToRetainedMsgProto(retainedMsg));
        return dummyTopic;
    }

    private void clearDummyRetainedMsg(String topic) throws QueuePersistenceException {
        persistenceService.persistRetainedMsgSync(topic, QueueConstants.EMPTY_RETAINED_MSG_PROTO);
    }

    private boolean isRetainedMsgProtoEmpty(QueueProtos.RetainedMsgProto retainedMsgProto) {
        return retainedMsgProto.getPayload().isEmpty() && retainedMsgProto.getQos() == 0;
    }

    @PreDestroy
    public void destroy() {
        stopped = true;
        if (retainedMsgConsumer != null) {
            retainedMsgConsumer.unsubscribeAndClose();
            if (this.retainedMsgConsumer.getConsumerGroupId() != null) {
                queueAdmin.deleteConsumerGroups(Collections.singleton(this.retainedMsgConsumer.getConsumerGroupId()));
            }
        }
        ThingsBoardExecutors.shutdownAndAwaitTermination(consumerExecutor, "Retained msg consumer");
    }
}
