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
package org.thingsboard.mqtt.broker.dao.sqlts.insert.sql;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.mqtt.broker.dao.model.sqlts.TsKvEntity;
import org.thingsboard.mqtt.broker.dao.sqlts.insert.AbstractInsertRepository;
import org.thingsboard.mqtt.broker.dao.sqlts.insert.InsertTsRepository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
@Transactional
public class SqlInsertTsRepository extends AbstractInsertRepository implements InsertTsRepository<TsKvEntity> {

    private static final String INSERT_ON_CONFLICT_DO_UPDATE = "INSERT INTO ts_kv (entity_id, key, ts, long_v) VALUES (?, ?, ?, ?) " +
            "ON CONFLICT (entity_id, key, ts) DO UPDATE SET long_v = ?;";

    @Override
    public void saveOrUpdate(List<TsKvEntity> entities) {
        jdbcTemplate.batchUpdate(INSERT_ON_CONFLICT_DO_UPDATE, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                TsKvEntity tsKvEntity = entities.get(i);
                ps.setObject(1, tsKvEntity.getEntityId());
                ps.setInt(2, tsKvEntity.getKey());
                ps.setLong(3, tsKvEntity.getTs());

                ps.setLong(4, tsKvEntity.getLongValue());
                ps.setLong(5, tsKvEntity.getLongValue());
            }

            @Override
            public int getBatchSize() {
                return entities.size();
            }
        });
    }

}
