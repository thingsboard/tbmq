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
package org.thingsboard.mqtt.broker.dao.client.generic;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.mqtt.broker.dao.model.GenericClientSessionCtxEntity;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static org.thingsboard.mqtt.broker.dao.model.ModelConstants.GENERIC_CLIENT_SESSION_CTX_CLIENT_ID_PROPERTY;
import static org.thingsboard.mqtt.broker.dao.model.ModelConstants.GENERIC_CLIENT_SESSION_CTX_COLUMN_FAMILY_NAME;
import static org.thingsboard.mqtt.broker.dao.model.ModelConstants.GENERIC_CLIENT_SESSION_CTX_LAST_UPDATED_PROPERTY;
import static org.thingsboard.mqtt.broker.dao.model.ModelConstants.GENERIC_CLIENT_SESSION_CTX_QOS2_PUBLISH_PACKET_IDS_PROPERTY;

@Repository
@Transactional
@RequiredArgsConstructor
public class PsqlInsertGenericClientSessionCtxRepository implements InsertGenericClientSessionCtxRepository {

    private static final String INSERT_OR_UPDATE = "INSERT INTO " + GENERIC_CLIENT_SESSION_CTX_COLUMN_FAMILY_NAME + " (" +
            GENERIC_CLIENT_SESSION_CTX_CLIENT_ID_PROPERTY + ", " +
            GENERIC_CLIENT_SESSION_CTX_LAST_UPDATED_PROPERTY + ", " +
            GENERIC_CLIENT_SESSION_CTX_QOS2_PUBLISH_PACKET_IDS_PROPERTY + ") " +
            "VALUES (?, ?, ?) " +
            "ON CONFLICT (" + GENERIC_CLIENT_SESSION_CTX_CLIENT_ID_PROPERTY + ") " +
            "DO UPDATE SET " + GENERIC_CLIENT_SESSION_CTX_QOS2_PUBLISH_PACKET_IDS_PROPERTY + " = ?, " +
            GENERIC_CLIENT_SESSION_CTX_LAST_UPDATED_PROPERTY + " = ?;";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void saveOrUpdate(List<GenericClientSessionCtxEntity> entities) {
        jdbcTemplate.batchUpdate(INSERT_OR_UPDATE, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                GenericClientSessionCtxEntity genericClientSessionCtxEntity = entities.get(i);
                ps.setString(1, genericClientSessionCtxEntity.getClientId());
                ps.setLong(2, genericClientSessionCtxEntity.getLastUpdatedTime());
                ps.setString(3, genericClientSessionCtxEntity.getQos2PublishPacketIds().toString());
                ps.setString(4, genericClientSessionCtxEntity.getQos2PublishPacketIds().toString());
                ps.setLong(5, genericClientSessionCtxEntity.getLastUpdatedTime());
            }

            @Override
            public int getBatchSize() {
                return entities.size();
            }
        });
    }
}
