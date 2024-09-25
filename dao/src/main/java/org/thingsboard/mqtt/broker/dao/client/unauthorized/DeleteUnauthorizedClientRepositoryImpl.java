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
package org.thingsboard.mqtt.broker.dao.client.unauthorized;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.thingsboard.mqtt.broker.dao.model.UnauthorizedClientEntity;
import org.thingsboard.mqtt.broker.dao.sqlts.insert.AbstractInsertDeleteRepository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.IntStream;

@Repository
@Transactional
@Slf4j
public class DeleteUnauthorizedClientRepositoryImpl extends AbstractInsertDeleteRepository implements DeleteUnauthorizedClientRepository {

    private static final String DELETE_RECORD = "DELETE FROM unauthorized_client WHERE client_id = ?;";

    @Override
    public void remove(List<UnauthorizedClientEntity> records) {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                int[] result = jdbcTemplate.batchUpdate(DELETE_RECORD, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        UnauthorizedClientEntity unauthorizedClient = records.get(i);
                        ps.setString(1, unauthorizedClient.getClientId());
                    }

                    @Override
                    public int getBatchSize() {
                        return records.size();
                    }
                });
                int deletedRecords = IntStream.of(result).sum();
                if (deletedRecords != records.size()) {
                    if (log.isDebugEnabled()) {
                        log.debug("Expected to delete {} records, actually deleted {} records", records.size(), deletedRecords);
                    }
                }
            }
        });
    }
}
