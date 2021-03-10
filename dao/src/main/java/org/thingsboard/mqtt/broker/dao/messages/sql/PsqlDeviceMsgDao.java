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
package org.thingsboard.mqtt.broker.dao.messages.sql;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.dao.DaoUtil;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgDao;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgRepository;
import org.thingsboard.mqtt.broker.dao.messages.TopicFilterQuery;
import org.thingsboard.mqtt.broker.dao.model.sql.DevicePublishMsgEntity;
import org.thingsboard.mqtt.broker.dao.sql.TbSqlBlockingQueuePool;
import org.thingsboard.mqtt.broker.dao.sql.TbSqlQueue;
import org.thingsboard.mqtt.broker.dao.sql.TbSqlQueueParams;
import org.thingsboard.mqtt.broker.dao.util.PsqlDao;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@PsqlDao
@RequiredArgsConstructor
public class PsqlDeviceMsgDao implements DeviceMsgDao {
    @Value("${sql.device-publish-messages.batch-size:1000}")
    private int batchSize;
    @Value("${sql.device-publish-messages.max-delay-ms:100}")
    private long maxDelay;
    @Value("${sql.device-publish-messages.threads:4}")
    private int threadsCount;

    private final DeviceMsgRepository deviceMsgRepository;

    private TbSqlQueue<DevicePublishMsgEntity> tbSqlQueue;

    @PostConstruct
    public void init() {
        TbSqlQueueParams queueParams = TbSqlQueueParams.builder()
                .queueName("device-publish-messages")
                .batchSize(batchSize)
                .maxDelay(maxDelay)
                .build();
        this.tbSqlQueue = TbSqlBlockingQueuePool.<DevicePublishMsgEntity>builder()
                .params(queueParams)
                .maxThreads(threadsCount)
                .queueIndexHashFunction(entity -> entity.getTopic().hashCode())
                .insertFunction(deviceMsgRepository::insert)
                .build();
    }

    @Override
    public ListenableFuture<Void> save(DevicePublishMsg devicePublishMsg) {
        log.trace("Saving device publish msg: {}", devicePublishMsg);
        DevicePublishMsgEntity entity = new DevicePublishMsgEntity(devicePublishMsg);
        return tbSqlQueue.add(entity);
    }

    @Override
    public List<DevicePublishMsg> findPersistedMessages(List<TopicFilterQuery> topicFilterQueries, int messageLimit) {
        log.trace("Finding device publish messages, limit - {}, query - {}", messageLimit, topicFilterQueries);
        return DaoUtil.convertDataList(deviceMsgRepository.findByQuery(topicFilterQueries, messageLimit));
    }

    @Override
    public List<String> getAllTopics() {
        log.trace("Finding all distinct topics.");
        return deviceMsgRepository.findAllTopics();
    }

    @PreDestroy
    public void destroy() {
        if (tbSqlQueue != null) {
            tbSqlQueue.destroy();
        }
    }
}
