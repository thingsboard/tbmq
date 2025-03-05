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
package org.thingsboard.mqtt.broker.dao.sql.event;

import com.google.common.util.concurrent.ListenableFuture;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.event.ErrorEventFilter;
import org.thingsboard.mqtt.broker.common.data.event.Event;
import org.thingsboard.mqtt.broker.common.data.event.EventFilter;
import org.thingsboard.mqtt.broker.common.data.event.EventType;
import org.thingsboard.mqtt.broker.common.data.event.LifeCycleEventFilter;
import org.thingsboard.mqtt.broker.common.data.event.StatisticsEventFilter;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.dao.DaoUtil;
import org.thingsboard.mqtt.broker.dao.event.EventDao;
import org.thingsboard.mqtt.broker.dao.model.sql.event.EventEntity;
import org.thingsboard.mqtt.broker.dao.sql.SqlQueueStatsManager;
import org.thingsboard.mqtt.broker.dao.sql.TbSqlBlockingQueuePool;
import org.thingsboard.mqtt.broker.dao.sql.TbSqlQueueParams;
import org.thingsboard.mqtt.broker.dao.sqlts.insert.sql.SqlPartitioningRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
@Slf4j
public class JpaBaseEventDao implements EventDao {

    private final EventPartitionConfiguration partitionConfiguration;
    private final SqlPartitioningRepository partitioningRepository;
    private final LifecycleEventRepository lcEventRepository;
    private final StatisticsEventRepository statsEventRepository;
    private final ErrorEventRepository errorEventRepository;
    private final EventInsertRepository eventInsertRepository;
    private final Optional<SqlQueueStatsManager> statsManager;

    @Value("${sql.events.batch_size:10000}")
    private int batchSize;

    @Value("${sql.events.batch_max_delay:100}")
    private long maxDelay;

    @Value("${sql.events.batch_threads:3}")
    private int batchThreads;

    @Value("${sql.batch_sort:true}")
    private boolean batchSortEnabled;

    private TbSqlBlockingQueuePool<Event> queue;

    private final Map<EventType, EventRepository<?, ?>> repositories = new ConcurrentHashMap<>();

    @PostConstruct
    private void init() {
        TbSqlQueueParams params = TbSqlQueueParams.builder()
                .queueName("Events")
                .batchSize(batchSize)
                .maxDelay(maxDelay)
                .batchSortEnabled(batchSortEnabled)
                .build();

        Function<Event, Integer> hashcodeFunction = entity -> Objects.hash(super.hashCode(), entity.getEntityId());

        queue = TbSqlBlockingQueuePool.<Event>builder()
                .params(params)
                .maxThreads(batchThreads)
                .queueIndexHashFunction(hashcodeFunction)
                .processingFunction(eventInsertRepository::save)
                .statsManager(statsManager.orElse(null))
                .batchUpdateComparator(Comparator.comparing(Event::getCreatedTime))
                .build();
        queue.init();

        repositories.put(EventType.LC_EVENT, lcEventRepository);
        repositories.put(EventType.STATS, statsEventRepository);
        repositories.put(EventType.ERROR, errorEventRepository);
    }

    @PreDestroy
    private void destroy() {
        if (queue != null) {
            queue.destroy("Events queue ");
        }
    }

    @Override
    public ListenableFuture<Void> saveAsync(Event event) {
        log.debug("Save event [{}] ", event);
        if (event.getId() == null) {
            event.setId(UUID.randomUUID());
            event.setCreatedTime(System.currentTimeMillis());
        } else if (event.getCreatedTime() == 0L) {
            event.setCreatedTime(System.currentTimeMillis());
        }
        partitioningRepository.createPartitionIfNotExists(event.getType().getTable(), event.getCreatedTime(),
                partitionConfiguration.getPartitionSizeInMs());
        return queue.add(event);
    }

    @Override
    public PageData<? extends Event> findEvents(UUID entityId, EventType eventType, TimePageLink pageLink) {
        return DaoUtil.toPageData(getEventRepository(eventType).findEvents(entityId, pageLink.getStartTime(), pageLink.getEndTime(), DaoUtil.toPageable(pageLink, EventEntity.eventColumnMap)));
    }

    @Override
    public PageData<? extends Event> findEventByFilter(UUID entityId, EventFilter eventFilter, TimePageLink pageLink) {
        if (eventFilter.isNotEmpty()) {
            return switch (eventFilter.getEventType()) {
                case LC_EVENT -> findEventByFilter(entityId, (LifeCycleEventFilter) eventFilter, pageLink);
                case ERROR -> findEventByFilter(entityId, (ErrorEventFilter) eventFilter, pageLink);
                case STATS -> findEventByFilter(entityId, (StatisticsEventFilter) eventFilter, pageLink);
            };
        } else {
            return findEvents(entityId, eventFilter.getEventType(), pageLink);
        }
    }

    @Override
    public void removeEvents(UUID entityId, Long startTime, Long endTime) {
        log.debug("[{}] Remove events [{}-{}] ", entityId, startTime, endTime);
        for (EventType eventType : EventType.values()) {
            getEventRepository(eventType).removeEvents(entityId, startTime, endTime);
        }
    }

    @Override
    public void removeEvents(UUID entityId, EventFilter eventFilter, Long startTime, Long endTime) {
        if (eventFilter.isNotEmpty()) {
            switch (eventFilter.getEventType()) {
                case LC_EVENT:
                    removeEventsByFilter(entityId, (LifeCycleEventFilter) eventFilter, startTime, endTime);
                    break;
                case ERROR:
                    removeEventsByFilter(entityId, (ErrorEventFilter) eventFilter, startTime, endTime);
                    break;
                case STATS:
                    removeEventsByFilter(entityId, (StatisticsEventFilter) eventFilter, startTime, endTime);
                    break;
                default:
                    throw new RuntimeException("Not supported event type: " + eventFilter.getEventType());
            }
        } else {
            getEventRepository(eventFilter.getEventType()).removeEvents(entityId, startTime, endTime);
        }
    }

    private PageData<? extends Event> findEventByFilter(UUID entityId, ErrorEventFilter eventFilter, TimePageLink pageLink) {
        return DaoUtil.toPageData(
                errorEventRepository.findEvents(
                        entityId,
                        pageLink.getStartTime(),
                        pageLink.getEndTime(),
                        eventFilter.getServer(),
                        eventFilter.getMethod(),
                        eventFilter.getErrorStr(),
                        DaoUtil.toPageable(pageLink, EventEntity.eventColumnMap))
        );
    }

    private PageData<? extends Event> findEventByFilter(UUID entityId, LifeCycleEventFilter eventFilter, TimePageLink pageLink) {
        boolean statusFilterEnabled = !StringUtils.isEmpty(eventFilter.getStatus());
        boolean statusFilter = statusFilterEnabled && eventFilter.getStatus().equalsIgnoreCase("Success");
        return DaoUtil.toPageData(
                lcEventRepository.findEvents(
                        entityId,
                        pageLink.getStartTime(),
                        pageLink.getEndTime(),
                        eventFilter.getServer(),
                        eventFilter.getEvent(),
                        statusFilterEnabled,
                        statusFilter,
                        eventFilter.getErrorStr(),
                        DaoUtil.toPageable(pageLink, EventEntity.eventColumnMap))
        );
    }

    private PageData<? extends Event> findEventByFilter(UUID entityId, StatisticsEventFilter eventFilter, TimePageLink pageLink) {
        return DaoUtil.toPageData(
                statsEventRepository.findEvents(
                        entityId,
                        pageLink.getStartTime(),
                        pageLink.getEndTime(),
                        eventFilter.getServer(),
                        eventFilter.getMinMessagesProcessed(),
                        eventFilter.getMaxMessagesProcessed(),
                        eventFilter.getMinErrorsOccurred(),
                        eventFilter.getMaxErrorsOccurred(),
                        DaoUtil.toPageable(pageLink, EventEntity.eventColumnMap))
        );
    }

    private void removeEventsByFilter(UUID entityId, ErrorEventFilter eventFilter, Long startTime, Long endTime) {
        errorEventRepository.removeEvents(
                entityId,
                startTime,
                endTime,
                eventFilter.getServer(),
                eventFilter.getMethod(),
                eventFilter.getErrorStr());

    }

    private void removeEventsByFilter(UUID entityId, LifeCycleEventFilter eventFilter, Long startTime, Long endTime) {
        boolean statusFilterEnabled = !StringUtils.isEmpty(eventFilter.getStatus());
        boolean statusFilter = statusFilterEnabled && eventFilter.getStatus().equalsIgnoreCase("Success");
        lcEventRepository.removeEvents(
                entityId,
                startTime,
                endTime,
                eventFilter.getServer(),
                eventFilter.getEvent(),
                statusFilterEnabled,
                statusFilter,
                eventFilter.getErrorStr());
    }

    private void removeEventsByFilter(UUID entityId, StatisticsEventFilter eventFilter, Long startTime, Long endTime) {
        statsEventRepository.removeEvents(
                entityId,
                startTime,
                endTime,
                eventFilter.getServer(),
                eventFilter.getMinMessagesProcessed(),
                eventFilter.getMaxMessagesProcessed(),
                eventFilter.getMinErrorsOccurred(),
                eventFilter.getMaxErrorsOccurred()
        );
    }

    @Override
    public List<? extends Event> findLatestEvents(UUID entityId, EventType eventType, int limit) {
        return DaoUtil.convertDataList(getEventRepository(eventType).findLatestEvents(entityId, limit));
    }

    @Override
    public void cleanupEvents(long eventExpTs, boolean cleanupDb) {
        if (eventExpTs > 0) {
            log.info("Going to cleanup events with exp time: {}", eventExpTs);
            if (cleanupDb) {
                cleanupEvents(eventExpTs);
            } else {
                cleanupPartitionsCache(eventExpTs);
            }
        }
    }

    private void cleanupEvents(long eventExpTime) {
        for (EventType eventType : EventType.values()) {
            cleanupPartitions(eventType, eventExpTime);
        }
    }

    private void cleanupPartitions(EventType eventType, long eventExpTime) {
        partitioningRepository.dropPartitionsBefore(eventType.getTable(), eventExpTime, partitionConfiguration.getPartitionSizeInMs());
    }

    private void cleanupPartitionsCache(long expTime) {
        for (EventType eventType : EventType.values()) {
            partitioningRepository.cleanupPartitionsCache(eventType.getTable(), expTime, partitionConfiguration.getPartitionSizeInMs());
        }
    }

    private void parseUUID(String src, String paramName) {
        if (!StringUtils.isEmpty(src)) {
            try {
                UUID.fromString(src);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Failed to convert " + paramName + " to UUID!");
            }
        }
    }

    private EventRepository<? extends EventEntity<?>, ?> getEventRepository(EventType eventType) {
        var repository = repositories.get(eventType);
        if (repository == null) {
            throw new RuntimeException("Event type: " + eventType + " is not supported!");
        }
        return repository;
    }


}
