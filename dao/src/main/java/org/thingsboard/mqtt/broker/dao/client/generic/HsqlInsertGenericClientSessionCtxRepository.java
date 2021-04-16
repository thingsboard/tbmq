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
package org.thingsboard.mqtt.broker.dao.client.generic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.mqtt.broker.dao.model.GenericClientSessionCtxEntity;
import org.thingsboard.mqtt.broker.dao.util.HsqlDao;

import java.util.List;

@HsqlDao
@Repository
@Transactional
public class HsqlInsertGenericClientSessionCtxRepository implements InsertGenericClientSessionCtxRepository {
    private static final String INSERT_OR_UPDATE =
            "MERGE INTO generic_client_session_ctx USING(VALUES ?, ?, ?) " +
                    "G (client_id, last_updated_time, qos2_publish_packet_ids) " +
                    "ON generic_client_session_ctx.client_id=G.client_id) " +
                    "WHEN MATCHED THEN UPDATE SET generic_client_session_ctx.qos2_publish_packet_ids = G.qos2_publish_packet_ids, " +
                    "generic_client_session_ctx.last_updated_time = G.last_updated_time " +
                    "WHEN NOT MATCHED THEN INSERT (client_id, last_updated_time, qos2_publish_packet_ids) " +
                    "VALUES (G.client_id, G.last_updated_time, G.qos2_publish_packet_ids)";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void saveOrUpdate(List<GenericClientSessionCtxEntity> entities) {
        entities.forEach(entity ->
                jdbcTemplate.update(INSERT_OR_UPDATE, ps -> {
                    ps.setString(1, entity.getClientId());
                    ps.setLong(2, entity.getLastUpdatedTime());
                    ps.setString(3, entity.getQos2PublishPacketIds().toString());
                }));
    }
}
