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
package org.thingsboard.mqtt.broker.dao.client.unauthorized;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.thingsboard.mqtt.broker.dao.model.UnauthorizedClientEntity;
import org.thingsboard.mqtt.broker.dao.sqlts.insert.AbstractInsertDeleteRepository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
@Transactional
public class InsertUnauthorizedClientRepositoryImpl extends AbstractInsertDeleteRepository implements InsertUnauthorizedClientRepository {

    private static final String BATCH_UPDATE =
            "UPDATE unauthorized_client SET ts = ?, ip_address = ?, username = ?, password_provided = ?, tls_used = ?, " +
                    "reason = ? WHERE client_id = ?";

    private static final String INSERT_OR_UPDATE =
            "INSERT INTO unauthorized_client (client_id, ip_address, ts, username, password_provided, tls_used, reason) " +
                    "VALUES(?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (client_id) DO UPDATE SET ts = ?, ip_address = ?, username = ?, password_provided = ?, " +
                    "tls_used = ?, reason = ?";

    @Override
    public void saveOrUpdate(List<UnauthorizedClientEntity> unauthorizedClients) {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                int[] result = jdbcTemplate.batchUpdate(BATCH_UPDATE, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        UnauthorizedClientEntity unauthorizedClient = unauthorizedClients.get(i);
                        ps.setLong(1, unauthorizedClient.getTs());
                        ps.setString(2, unauthorizedClient.getIpAddress());
                        ps.setString(3, unauthorizedClient.getUsername());
                        ps.setBoolean(4, unauthorizedClient.isPasswordProvided());
                        ps.setBoolean(5, unauthorizedClient.isTlsUsed());
                        ps.setString(6, unauthorizedClient.getReason());
                        ps.setString(7, unauthorizedClient.getClientId());
                    }

                    @Override
                    public int getBatchSize() {
                        return unauthorizedClients.size();
                    }
                });

                int toInsertCount = 0;
                for (int i = 0; i < result.length; i++) {
                    if (result[i] == 0) {
                        toInsertCount++;
                    }
                }

                List<UnauthorizedClientEntity> insertEntities = new ArrayList<>(toInsertCount);
                for (int i = 0; i < result.length; i++) {
                    if (result[i] == 0) {
                        insertEntities.add(unauthorizedClients.get(i));
                    }
                }

                jdbcTemplate.batchUpdate(INSERT_OR_UPDATE, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        UnauthorizedClientEntity unauthorizedClient = insertEntities.get(i);
                        ps.setString(1, unauthorizedClient.getClientId());
                        ps.setString(2, unauthorizedClient.getIpAddress());
                        ps.setLong(3, unauthorizedClient.getTs());
                        ps.setString(4, unauthorizedClient.getUsername());
                        ps.setBoolean(5, unauthorizedClient.isPasswordProvided());
                        ps.setBoolean(6, unauthorizedClient.isTlsUsed());
                        ps.setString(7, unauthorizedClient.getReason());
                        ps.setLong(8, unauthorizedClient.getTs());
                        ps.setString(9, unauthorizedClient.getIpAddress());
                        ps.setString(10, unauthorizedClient.getUsername());
                        ps.setBoolean(11, unauthorizedClient.isPasswordProvided());
                        ps.setBoolean(12, unauthorizedClient.isTlsUsed());
                        ps.setString(13, unauthorizedClient.getReason());
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
