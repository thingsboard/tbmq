/**
 * Copyright © 2016-2022 The Thingsboard Authors
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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.constant.BrokerConstants;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.RetainedMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.stats.RetainedMsgConsumerStats;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.thingsboard.mqtt.broker.util.BytesUtil.bytesToString;

@Slf4j
@Component
public class RetainedMsgConsumerImpl implements RetainedMsgConsumer {

    private static final String DUMMY_TOPIC_PREFIX = "dummy/topic/";
    private static final byte[] DUMMY_PAYLOAD = "test".getBytes(StandardCharsets.UTF_8);

    private volatile boolean initializing = true;
    private volatile boolean stopped = false;

    private final ExecutorService consumerExecutor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("retained-msg-listener"));

    @Value("${queue.retained-msg.poll-interval}")
    private long pollDuration;

    private final RetainedMsgPersistenceService persistenceService;
    private final TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.RetainedMsgProto>> retainedMsgConsumer;
    private final TbQueueAdmin queueAdmin;
    private final RetainedMsgConsumerStats stats;

    public RetainedMsgConsumerImpl(RetainedMsgQueueFactory retainedMsgQueueFactory, ServiceInfoProvider serviceInfoProvider,
                                   RetainedMsgPersistenceService persistenceService, TbQueueAdmin queueAdmin, StatsManager statsManager) {
        String uniqueConsumerGroupId = serviceInfoProvider.getServiceId() + "-" + System.currentTimeMillis();
        this.retainedMsgConsumer = retainedMsgQueueFactory.createConsumer(serviceInfoProvider.getServiceId(), uniqueConsumerGroupId);
        this.queueAdmin = queueAdmin;
        this.persistenceService = persistenceService;
        this.stats = statsManager.getRetainedMsgConsumerStats();
    }

    @Override
    public Map<String, RetainedMsg> initLoad() throws QueuePersistenceException {
        String dummyTopic = persistDummyRetainedMsg();

        retainedMsgConsumer.subscribe();

        List<TbProtoQueueMsg<QueueProtos.RetainedMsgProto>> messages;
        boolean encounteredDummyTopic = false;
        Map<String, RetainedMsg> allRetainedMsgs = new HashMap<>();
        do {
            try {
                messages = retainedMsgConsumer.poll(pollDuration);
                for (TbProtoQueueMsg<QueueProtos.RetainedMsgProto> msg : messages) {
                    String topic = msg.getKey();
                    if (isRetainedMsgProtoEmpty(msg.getValue())) {
                        // this means Kafka log compaction service haven't cleared empty message yet
                        log.debug("[{}] Encountered empty RetainedMsg.", topic);
                        allRetainedMsgs.remove(topic);
                    } else {
                        RetainedMsg retainedMsg = ProtoConverter.convertToRetainedMsg(msg.getValue());
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
                        String serviceId = bytesToString(msg.getHeaders().get(BrokerConstants.SERVICE_ID_HEADER));

                        if (isRetainedMsgProtoEmpty(msg.getValue())) {
                            callback.accept(topic, serviceId, null);
                            clearedRetainedMsgCount++;
                        } else {
                            RetainedMsg retainedMsg = ProtoConverter.convertToRetainedMsg(msg.getValue());
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

    private String persistDummyRetainedMsg() throws QueuePersistenceException {
        String dummyTopic = DUMMY_TOPIC_PREFIX + RandomStringUtils.randomAlphanumeric(8);
        RetainedMsg retainedMsg = new RetainedMsg(dummyTopic, DUMMY_PAYLOAD, 0);
        persistenceService.persistRetainedMsgSync(dummyTopic, ProtoConverter.convertToRetainedMsgProto(retainedMsg));
        return dummyTopic;
    }

    private void clearDummyRetainedMsg(String topic) throws QueuePersistenceException {
        persistenceService.persistRetainedMsgSync(topic, BrokerConstants.EMPTY_RETAINED_MSG_PROTO);
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
        consumerExecutor.shutdownNow();
    }
}
