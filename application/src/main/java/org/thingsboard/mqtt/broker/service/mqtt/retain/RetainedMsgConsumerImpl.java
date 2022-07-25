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
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.RetainedMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    // TODO: 25/07/2022 add stats

    public RetainedMsgConsumerImpl(RetainedMsgQueueFactory retainedMsgQueueFactory, ServiceInfoProvider serviceInfoProvider,
                                   RetainedMsgPersistenceService persistenceService, StatsManager statsManager) {
        String uniqueConsumerGroupId = serviceInfoProvider.getServiceId() + "-" + System.currentTimeMillis();
        this.retainedMsgConsumer = retainedMsgQueueFactory.createConsumer(serviceInfoProvider.getServiceId(), uniqueConsumerGroupId);
        this.persistenceService = persistenceService;
    }

    @Override
    public Map<String, RetainedMsg> initLoad() throws QueuePersistenceException {
        return null;
    }

    @Override
    public void listen(RetainedMsgChangesCallback callback) {

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

    @PreDestroy
    public void destroy() {
        stopped = true;
        if (retainedMsgConsumer != null) {
            retainedMsgConsumer.unsubscribeAndClose();
        }
        consumerExecutor.shutdownNow();
    }
}
