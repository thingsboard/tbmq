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
package org.thingsboard.mqtt.broker.dao.messages.sql;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.dao.DaoUtil;
import org.thingsboard.mqtt.broker.dao.messages.DeletePacketInfo;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgDao;
import org.thingsboard.mqtt.broker.dao.messages.LowLevelDeviceMsgRepository;
import org.thingsboard.mqtt.broker.dao.messages.UpdatePacketTypeInfo;
import org.thingsboard.mqtt.broker.dao.model.sql.DevicePublishMsgEntity;
import org.thingsboard.mqtt.broker.dao.sql.SqlQueueStatsManager;
import org.thingsboard.mqtt.broker.dao.sql.TbSqlBlockingQueuePool;
import org.thingsboard.mqtt.broker.dao.sql.TbSqlQueue;
import org.thingsboard.mqtt.broker.dao.sql.TbSqlQueueParams;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqlDeviceMsgDao implements DeviceMsgDao {

    private final LowLevelDeviceMsgRepository lowLevelDeviceMsgRepository;
    private final DeviceMsgRepository deviceMsgRepository;
    private final DeletePacketQueueConfiguration deletePacketQueueConfiguration;
    private final UpdatePacketQueueConfiguration updatePacketQueueConfiguration;

    @Autowired(required = false)
    private SqlQueueStatsManager sqlQueueStatsManager;

    private TbSqlQueue<UpdatePacketTypeInfo> updatePacketTypeQueue;
    private TbSqlQueue<DeletePacketInfo> deletePacketQueue;

    @PostConstruct
    public void init() {
        initUpdatePacketTypeQueue();
        initDeletePacketQueue();
    }

    @Override
    public void save(List<DevicePublishMsg> devicePublishMessages, boolean failOnConflict) {
        if (log.isTraceEnabled()) {
            log.trace("Saving device publish messages: failOnConflict - {}, msgs - {}", failOnConflict, devicePublishMessages);
        }
        List<DevicePublishMsgEntity> entities = devicePublishMessages.stream().map(DevicePublishMsgEntity::new).collect(Collectors.toList());
        if (failOnConflict) {
            lowLevelDeviceMsgRepository.insert(entities);
        } else {
            lowLevelDeviceMsgRepository.insertOrUpdate(entities);
        }
    }

    @Override
    public List<DevicePublishMsg> findPersistedMessages(String clientId, int messageLimit) {
        if (log.isTraceEnabled()) {
            log.trace("Finding device publish messages, clientId - {}, limit - {}", clientId, messageLimit);
        }
        List<DevicePublishMsgEntity> devicePublishMsgs = deviceMsgRepository.findByClientIdReversed(clientId, messageLimit).stream()
                .sorted(Comparator.comparingLong(DevicePublishMsgEntity::getSerialNumber))
                .collect(Collectors.toList());
        return DaoUtil.convertDataList(devicePublishMsgs);
    }

    @Override
    public List<DevicePublishMsg> findPersistedMessagesBySerialNumber(String clientId, long fromSerialNumber, long toSerialNumber) {
        if (log.isTraceEnabled()) {
            log.trace("Finding device publish messages, clientId - {}, fromSerialNumber - {}, toSerialNumber - {}", clientId, fromSerialNumber, toSerialNumber);
        }
        List<DevicePublishMsgEntity> devicePublishMsgs = deviceMsgRepository.findByClientIdAndSerialNumberInRange(clientId, fromSerialNumber, toSerialNumber);
        return DaoUtil.convertDataList(devicePublishMsgs);
    }

    @Override
    public void removePersistedMessages(String clientId) {
        if (log.isTraceEnabled()) {
            log.trace("Removing device publish messages, clientId - {}", clientId);
        }
        lowLevelDeviceMsgRepository.removePacketsByClientId(clientId);
    }

    @Override
    public ListenableFuture<Void> removePersistedMessage(String clientId, int packetId) {
        if (log.isTraceEnabled()) {
            log.trace("Removing device publish message, clientId - {}, packetId - {}", clientId, packetId);
        }
        return deletePacketQueue.add(new DeletePacketInfo(clientId, packetId));
    }

    @Override
    public ListenableFuture<Void> updatePacketType(String clientId, int packetId, PersistedPacketType packetType) {
        if (log.isTraceEnabled()) {
            log.trace("Updating packet type for device publish message, clientId - {}, packetId - {}, packetType - {}.", clientId, packetId, packetType);
        }
        return updatePacketTypeQueue.add(new UpdatePacketTypeInfo(clientId, packetType, packetId));
    }

    private void initDeletePacketQueue() {
        Function<DeletePacketInfo, Integer> deleteQueueIndexHashFunction = deletePacketInfo -> deletePacketInfo.getClientId().hashCode();
        TbSqlQueueParams deletePacketQueueParams = TbSqlQueueParams.builder()
                .queueName("DeletePacketQueue")
                .batchSize(deletePacketQueueConfiguration.getBatchSize())
                .maxDelay(deletePacketQueueConfiguration.getMaxDelay())
                .build();
        this.deletePacketQueue = TbSqlBlockingQueuePool.<DeletePacketInfo>builder()
                .queueIndexHashFunction(deleteQueueIndexHashFunction)
                .maxThreads(deletePacketQueueConfiguration.getBatchThreads())
                .params(deletePacketQueueParams)
                .statsManager(sqlQueueStatsManager)
                .processingFunction(lowLevelDeviceMsgRepository::removePackets)
                .build();
        deletePacketQueue.init();
    }

    private void initUpdatePacketTypeQueue() {
        Function<UpdatePacketTypeInfo, Integer> updateQueueIndexHashFunction = updatePacketTypeInfo -> updatePacketTypeInfo.getClientId().hashCode();
        TbSqlQueueParams updatePacketTypeQueueParams = TbSqlQueueParams.builder()
                .queueName("UpdatePacketTypeQueue")
                .batchSize(updatePacketQueueConfiguration.getBatchSize())
                .maxDelay(updatePacketQueueConfiguration.getMaxDelay())
                .build();
        this.updatePacketTypeQueue = TbSqlBlockingQueuePool.<UpdatePacketTypeInfo>builder()
                .queueIndexHashFunction(updateQueueIndexHashFunction)
                .maxThreads(updatePacketQueueConfiguration.getBatchThreads())
                .params(updatePacketTypeQueueParams)
                .statsManager(sqlQueueStatsManager)
                .processingFunction(lowLevelDeviceMsgRepository::updatePacketTypes)
                .build();
        updatePacketTypeQueue.init();
    }

    @PreDestroy
    private void destroy() {
        if (updatePacketTypeQueue != null) {
            updatePacketTypeQueue.destroy();
        }
        if (deletePacketQueue != null) {
            deletePacketQueue.destroy();
        }
    }
}
