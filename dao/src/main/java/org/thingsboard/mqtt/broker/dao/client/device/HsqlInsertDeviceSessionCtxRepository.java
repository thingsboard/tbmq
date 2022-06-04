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
package org.thingsboard.mqtt.broker.dao.client.device;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.mqtt.broker.dao.model.DeviceSessionCtxEntity;
import org.thingsboard.mqtt.broker.dao.util.HsqlDao;

import java.util.List;

@HsqlDao
@Repository
@Transactional
public class HsqlInsertDeviceSessionCtxRepository implements InsertDeviceSessionCtxRepository {
    private static final String INSERT_OR_UPDATE =
            "MERGE INTO device_session_ctx USING(VALUES ?, ?, ?, ?) " +
                    "D (client_id, last_updated_time, last_serial_number, last_packet_id) " +
                    "ON device_session_ctx.client_id=D.client_id " +
                    "WHEN MATCHED THEN UPDATE SET device_session_ctx.last_serial_number = D.last_serial_number, " +
                    "device_session_ctx.last_packet_id = D.last_packet_id, device_session_ctx.last_updated_time = D.last_updated_time " +
                    "WHEN NOT MATCHED THEN INSERT (client_id, last_updated_time, last_serial_number, last_packet_id) " +
                    "VALUES (D.client_id, D.last_updated_time, D.last_serial_number, D.last_packet_id)";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void saveOrUpdate(List<DeviceSessionCtxEntity> entities) {
        entities.forEach(entity ->
                jdbcTemplate.update(INSERT_OR_UPDATE, ps -> {
                    ps.setString(1, entity.getClientId());
                    ps.setLong(2, entity.getLastUpdatedTime());
                    ps.setLong(3, entity.getLastSerialNumber());
                    ps.setInt(4, entity.getLastPacketId());
                }));
    }
}
