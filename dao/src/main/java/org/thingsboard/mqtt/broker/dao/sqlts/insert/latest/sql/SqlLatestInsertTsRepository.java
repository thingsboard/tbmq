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
package org.thingsboard.mqtt.broker.dao.sqlts.insert.latest.sql;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.thingsboard.mqtt.broker.dao.model.sqlts.latest.TsKvLatestEntity;
import org.thingsboard.mqtt.broker.dao.sqlts.insert.AbstractInsertDeleteRepository;
import org.thingsboard.mqtt.broker.dao.sqlts.insert.latest.InsertLatestTsRepository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

@Repository
@Transactional
public class SqlLatestInsertTsRepository extends AbstractInsertDeleteRepository implements InsertLatestTsRepository {

    private static final String BATCH_UPDATE =
            "UPDATE ts_kv_latest SET ts = ?, long_v = ? WHERE entity_id = ? AND key = ?";

    private static final String INSERT_OR_UPDATE =
            "INSERT INTO ts_kv_latest (entity_id, key, ts, long_v) VALUES(?, ?, ?, ?) " +
                    "ON CONFLICT (entity_id, key) DO UPDATE SET ts = ?, long_v = ?";

    @Override
    public void saveOrUpdate(List<TsKvLatestEntity> entities) {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                int[] result = jdbcTemplate.batchUpdate(BATCH_UPDATE, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        TsKvLatestEntity tsKvLatestEntity = entities.get(i);
                        ps.setLong(1, tsKvLatestEntity.getTs());

                        if (tsKvLatestEntity.getLongValue() != null) {
                            ps.setLong(2, tsKvLatestEntity.getLongValue());
                        } else {
                            ps.setNull(2, Types.BIGINT);
                        }

                        ps.setObject(3, tsKvLatestEntity.getEntityId());
                        ps.setInt(4, tsKvLatestEntity.getKey());
                    }

                    @Override
                    public int getBatchSize() {
                        return entities.size();
                    }
                });

                int toInsertCount = 0;
                for (int i = 0; i < result.length; i++) {
                    if (result[i] == 0) {
                        toInsertCount++;
                    }
                }

                List<TsKvLatestEntity> insertEntities = new ArrayList<>(toInsertCount);
                for (int i = 0; i < result.length; i++) {
                    if (result[i] == 0) {
                        insertEntities.add(entities.get(i));
                    }
                }

                jdbcTemplate.batchUpdate(INSERT_OR_UPDATE, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        TsKvLatestEntity tsKvLatestEntity = insertEntities.get(i);
                        ps.setObject(1, tsKvLatestEntity.getEntityId());
                        ps.setInt(2, tsKvLatestEntity.getKey());

                        ps.setLong(3, tsKvLatestEntity.getTs());
                        ps.setLong(5, tsKvLatestEntity.getTs());

                        if (tsKvLatestEntity.getLongValue() != null) {
                            ps.setLong(4, tsKvLatestEntity.getLongValue());
                            ps.setLong(6, tsKvLatestEntity.getLongValue());
                        } else {
                            ps.setNull(4, Types.BIGINT);
                            ps.setNull(6, Types.BIGINT);
                        }
                    }

                    @Override
                    public int getBatchSize() {
                        return insertEntities.size();
                    }
                });
            }
        });
    }
}
