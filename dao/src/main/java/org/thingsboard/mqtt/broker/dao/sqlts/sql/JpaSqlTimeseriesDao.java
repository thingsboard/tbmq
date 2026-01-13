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
package org.thingsboard.mqtt.broker.dao.sqlts.sql;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.kv.CleanUpResult;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.dao.dictionary.KeyDictionaryDao;
import org.thingsboard.mqtt.broker.dao.model.sqlts.TsKvEntity;
import org.thingsboard.mqtt.broker.dao.sqlts.AbstractChunkedAggregationTimeseriesDao;
import org.thingsboard.mqtt.broker.dao.sqlts.insert.sql.SqlPartitioningRepository;
import org.thingsboard.mqtt.broker.dao.timeseries.SqlPartition;
import org.thingsboard.mqtt.broker.dao.timeseries.SqlTsPartitionDate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Slf4j
@RequiredArgsConstructor
public class JpaSqlTimeseriesDao extends AbstractChunkedAggregationTimeseriesDao {

    private static final ReentrantLock partitionCreationLock = new ReentrantLock();
    private final Map<Long, SqlPartition> partitions = new ConcurrentHashMap<>();

    private final SqlPartitioningRepository partitioningRepository;
    private final KeyDictionaryDao keyDictionaryDao;

    private SqlTsPartitionDate tsFormat;

    @Value("${sql.ts_key_value_partitioning:DAYS}")
    @Setter
    private String partitioning;

    @Override
    protected void init() {
        super.init();
        Optional<SqlTsPartitionDate> partition = SqlTsPartitionDate.parse(partitioning);
        if (partition.isPresent()) {
            tsFormat = partition.get();
        } else {
            log.warn("Incorrect configuration of partitioning {}", partitioning);
            throw new RuntimeException("Failed to parse partitioning property: " + partitioning + "!");
        }
    }

    @Override
    public ListenableFuture<Void> save(String entityId, TsKvEntry tsKvEntry) {
        savePartitionIfNotExist(tsKvEntry.getTs());
        String strKey = tsKvEntry.getKey();
        Integer keyId = keyDictionaryDao.getOrSaveKeyId(strKey);
        TsKvEntity entity = new TsKvEntity();
        entity.setEntityId(entityId);
        entity.setTs(tsKvEntry.getTs());
        entity.setKey(keyId);
        entity.setLongValue(tsKvEntry.getLongValue().orElse(null));
        if (log.isTraceEnabled()) {
            log.trace("Saving entity: {}", entity);
        }
        return Futures.transform(tsQueue.add(entity), v -> null, MoreExecutors.directExecutor());
    }

    @Override
    public CleanUpResult cleanUp(long systemTtl) {
        int deletedPartitions = cleanUpPartitions(systemTtl);
        long deletedRows = cleanUpData(systemTtl);
        return new CleanUpResult(deletedPartitions, deletedRows);
    }

    public long cleanUpData(long systemTtl) {
        log.info("Going to cleanup old timeseries data using ttl: {}s", systemTtl);
        try {
            Long deleted = tsKvRepository.cleanUp(getExpirationTime(systemTtl));
            log.info("Total telemetry removed stats by TTL {}!", deleted);
            return deleted;
        } catch (Exception e) {
            log.error("Failed to execute cleanup using ttl {}", systemTtl, e);
        }
        return 0;
    }

    private long getExpirationTime(long ttl) {
        return System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(ttl);
    }

    private int cleanUpPartitions(long systemTtl) {
        log.info("Going to cleanup old timeseries data partitions using partition type: {} and ttl: {}s", partitioning, systemTtl);

        LocalDateTime dateByTtlDate = getPartitionByTtlDate(systemTtl);
        log.info("Date by max ttl {}", dateByTtlDate);
        String partitionByTtlDate = getPartitionByDate(dateByTtlDate);
        log.info("Partition by max ttl {}", partitionByTtlDate);

        return cleanupPartition(dateByTtlDate, partitionByTtlDate);
    }

    private int cleanupPartition(LocalDateTime dateByTtlDate, String partitionByTtlDate) {
        int deleted = 0;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement("SELECT tablename " +
                     "FROM pg_tables " +
                     "WHERE schemaname = 'public'" +
                     "AND tablename like 'ts_kv_' || '%' " +
                     "AND tablename != 'ts_kv_dictionary' " +
                     "AND tablename != 'ts_kv_latest' " +
                     "AND tablename != 'ts_kv_indefinite' " +
                     "AND tablename != ?")) {
            stmt.setString(1, partitionByTtlDate);
            stmt.setQueryTimeout((int) TimeUnit.MINUTES.toSeconds(1));
            stmt.execute();
            printWarnings(stmt);
            try (ResultSet resultSet = stmt.getResultSet()) {
                while (resultSet.next()) {
                    String tableName = resultSet.getString(1);
                    if (tableName != null && checkNeedDropTable(dateByTtlDate, tableName)) {
                        log.info("Dropping {} table", tableName);
                        dropTable(tableName);
                        deleted++;
                    }
                }
                log.info("Cleanup {} partitions finished!", deleted);
            }
        } catch (SQLException e) {
            log.error("SQLException occurred during TTL cleanupPartition task execution", e);
        }
        return deleted;
    }

    protected boolean checkNeedDropTable(LocalDateTime date, String tableName) {
        List<String> tableNameSplitList = Arrays.asList(tableName.split("_"));
        //zero position is 'ts', first is 'kv' and after years, months, days
        if (tableNameSplitList.size() > 2 && tableNameSplitList.get(0).equals("ts") && tableNameSplitList.get(1).equals("kv")) {
            switch (partitioning) {
                case "YEARS":
                    return (tableNameSplitList.size() == 3 && date.getYear() > Integer.parseInt(tableNameSplitList.get(2)));
                case "MONTHS":
                    return (
                            tableNameSplitList.size() == 4 && (date.getYear() > Integer.parseInt(tableNameSplitList.get(2))
                                    || (date.getYear() == Integer.parseInt(tableNameSplitList.get(2))
                                    && date.getMonth().getValue() > Integer.parseInt(tableNameSplitList.get(3)))
                            ));
                case "DAYS":
                    return (
                            tableNameSplitList.size() == 5 && (date.getYear() > Integer.parseInt(tableNameSplitList.get(2))
                                    || (date.getYear() == Integer.parseInt(tableNameSplitList.get(2))
                                    && date.getMonth().getValue() > Integer.parseInt(tableNameSplitList.get(3)))
                                    || (date.getYear() == Integer.parseInt(tableNameSplitList.get(2))
                                    && date.getMonth().getValue() == Integer.parseInt(tableNameSplitList.get(3))
                                    && date.getDayOfMonth() > Integer.parseInt(tableNameSplitList.get(4)))
                            ));
            }
        }
        return false;
    }

    private void dropTable(String tableName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(String.format("DROP TABLE IF EXISTS %s", tableName))) {
            statement.execute();
        } catch (SQLException e) {
            log.error("SQLException occurred during drop table task execution", e);
        }
    }

    protected String getPartitionByDate(LocalDateTime date) {
        String result = "";
        switch (partitioning) {
            case "DAYS":
                result = "_" + ((date.getDayOfMonth() < 10) ? "0" + date.getDayOfMonth() : date.getDayOfMonth()) + result;
            case "MONTHS":
                result = "_" + ((date.getMonth().getValue() < 10) ? "0" + date.getMonth().getValue() : date.getMonth().getValue()) + result;
            case "YEARS":
                result = date.getYear() + result;
        }
        return "ts_kv_" + result;
    }

    private LocalDateTime getPartitionByTtlDate(long ttl) {
        return Instant.ofEpochMilli(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(ttl)).atZone(ZoneOffset.UTC).toLocalDateTime();
    }

    private void savePartitionIfNotExist(long ts) {
        if (!tsFormat.equals(SqlTsPartitionDate.INDEFINITE) && ts >= 0) {
            LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneOffset.UTC);
            LocalDateTime localDateTimeStart = tsFormat.truncateTo(time);
            long partitionStartTs = toMills(localDateTimeStart);
            if (partitions.get(partitionStartTs) == null) {
                LocalDateTime localDateTimeEnd = tsFormat.plusTo(localDateTimeStart);
                long partitionEndTs = toMills(localDateTimeEnd);
                ZonedDateTime zonedDateTime = localDateTimeStart.atZone(ZoneOffset.UTC);
                String partitionDate = zonedDateTime.format(DateTimeFormatter.ofPattern(tsFormat.getPattern()));
                savePartition(new SqlPartition(SqlPartition.TS_KV, partitionStartTs, partitionEndTs, partitionDate));
            }
        }
    }

    private void savePartition(SqlPartition sqlPartition) {
        if (!partitions.containsKey(sqlPartition.getStart())) {
            partitionCreationLock.lock();
            try {
                if (log.isTraceEnabled()) {
                    log.trace("Saving partition: {}", sqlPartition);
                }
                partitioningRepository.save(sqlPartition);
                if (log.isTraceEnabled()) {
                    log.trace("Adding partition to Set: {}", sqlPartition);
                }
                partitions.put(sqlPartition.getStart(), sqlPartition);
            } catch (DataIntegrityViolationException ex) {
                if (log.isTraceEnabled()) {
                    log.trace("Error occurred during partition save:", ex);
                }
                if (ex.getCause() instanceof ConstraintViolationException) {
                    log.warn("Saving partition [{}] rejected. Timeseries data will save to the ts_kv_indefinite (DEFAULT) partition.", sqlPartition.getPartitionDate());
                    partitions.put(sqlPartition.getStart(), sqlPartition);
                } else {
                    throw new RuntimeException(ex);
                }
            } finally {
                partitionCreationLock.unlock();
            }
        }
    }

    private static long toMills(LocalDateTime time) {
        return time.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
