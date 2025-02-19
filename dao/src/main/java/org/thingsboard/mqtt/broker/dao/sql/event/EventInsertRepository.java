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

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.event.ErrorEvent;
import org.thingsboard.mqtt.broker.common.data.event.Event;
import org.thingsboard.mqtt.broker.common.data.event.EventType;
import org.thingsboard.mqtt.broker.common.data.event.LifecycleEvent;
import org.thingsboard.mqtt.broker.common.data.event.StatisticsEvent;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Repository
@Transactional
@RequiredArgsConstructor
public class EventInsertRepository {

    private static final ThreadLocal<Pattern> PATTERN_THREAD_LOCAL = ThreadLocal.withInitial(() -> Pattern.compile(String.valueOf(Character.MIN_VALUE)));

    private final Map<EventType, String> insertStmtMap = new ConcurrentHashMap<>();

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Value("${sql.remove_null_chars:true}")
    private boolean removeNullChars;

    @PostConstruct
    public void init() {
        insertStmtMap.put(EventType.ERROR, "INSERT INTO " + EventType.ERROR.getTable() +
                " (id, ts, entity_id, service_id, e_method, e_error) " +
                "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING;");
        insertStmtMap.put(EventType.LC_EVENT, "INSERT INTO " + EventType.LC_EVENT.getTable() +
                " (id, ts, entity_id, service_id, e_type, e_success, e_error) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING;");
        insertStmtMap.put(EventType.STATS, "INSERT INTO " + EventType.STATS.getTable() +
                " (id, ts, entity_id, service_id, e_messages_processed, e_errors_occurred) " +
                "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING;");
    }

    public void save(List<Event> entities) {
        Map<EventType, List<Event>> eventsByType = entities.stream().collect(Collectors.groupingBy(Event::getType, Collectors.toList()));
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                for (var entry : eventsByType.entrySet()) {
                    jdbcTemplate.batchUpdate(insertStmtMap.get(entry.getKey()), getStatementSetter(entry.getKey(), entry.getValue()));
                }
            }
        });
    }

    private BatchPreparedStatementSetter getStatementSetter(EventType eventType, List<Event> events) {
        return switch (eventType) {
            case ERROR -> getErrorEventSetter(events);
            case LC_EVENT -> getLcEventSetter(events);
            case STATS -> getStatsEventSetter(events);
        };
    }

    private BatchPreparedStatementSetter getErrorEventSetter(List<Event> events) {
        return new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ErrorEvent event = (ErrorEvent) events.get(i);
                setCommonEventFields(ps, event);
                safePutString(ps, 5, event.getMethod());
                safePutString(ps, 6, event.getError());
            }

            @Override
            public int getBatchSize() {
                return events.size();
            }
        };
    }

    private BatchPreparedStatementSetter getLcEventSetter(List<Event> events) {
        return new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                LifecycleEvent event = (LifecycleEvent) events.get(i);
                setCommonEventFields(ps, event);
                safePutString(ps, 5, event.getLcEventType());
                ps.setBoolean(6, event.isSuccess());
                safePutString(ps, 7, event.getError());
            }

            @Override
            public int getBatchSize() {
                return events.size();
            }
        };
    }

    private BatchPreparedStatementSetter getStatsEventSetter(List<Event> events) {
        return new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                StatisticsEvent event = (StatisticsEvent) events.get(i);
                setCommonEventFields(ps, event);
                ps.setLong(5, event.getMessagesProcessed());
                ps.setLong(6, event.getErrorsOccurred());
            }

            @Override
            public int getBatchSize() {
                return events.size();
            }
        };
    }

    void safePutString(PreparedStatement ps, int parameterIdx, String value) throws SQLException {
        if (value != null) {
            ps.setString(parameterIdx, replaceNullChars(value));
        } else {
            ps.setNull(parameterIdx, Types.VARCHAR);
        }
    }

    void safePutUUID(PreparedStatement ps, int parameterIdx, UUID value) throws SQLException {
        if (value != null) {
            ps.setObject(parameterIdx, value);
        } else {
            ps.setNull(parameterIdx, Types.OTHER);
        }
    }

    private void setCommonEventFields(PreparedStatement ps, Event event) throws SQLException {
        ps.setObject(1, event.getId());
        ps.setLong(2, event.getCreatedTime());
        ps.setObject(3, event.getEntityId());
        ps.setString(4, event.getServiceId());
    }

    private String replaceNullChars(String strValue) {
        if (removeNullChars && strValue != null) {
            return PATTERN_THREAD_LOCAL.get().matcher(strValue).replaceAll(BrokerConstants.EMPTY_STR);
        }
        return strValue;
    }

}
