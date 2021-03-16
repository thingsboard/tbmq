/**
 * Copyright © 2016-2020 The Thingsboard Authors
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.dao.messages.sql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.thingsboard.mqtt.broker.dao.messages.InsertDeviceMsgRepository;
import org.thingsboard.mqtt.broker.dao.model.ModelConstants;
import org.thingsboard.mqtt.broker.dao.model.sql.DevicePublishMsgEntity;
import org.thingsboard.mqtt.broker.dao.util.PsqlDao;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Slf4j
@PsqlDao
@Repository
public class PsqlInsertDeviceMsgRepository implements InsertDeviceMsgRepository {

    private static final String INSERT = "INSERT INTO " + ModelConstants.DEVICE_PUBLISH_MSG_COLUMN_FAMILY_NAME + " (" +
            ModelConstants.DEVICE_PUBLISH_MSG_CLIENT_ID_PROPERTY + ", " +
            ModelConstants.DEVICE_PUBLISH_MSG_TOPIC_PROPERTY + ", " +
            ModelConstants.DEVICE_PUBLISH_MSG_SERIAL_NUMBER_PROPERTY + ", " +
            ModelConstants.DEVICE_PUBLISH_MSG_TIME_PROPERTY + ", " +
            ModelConstants.DEVICE_PUBLISH_MSG_QOS_PROPERTY + ", " +
            ModelConstants.DEVICE_PUBLISH_MSG_PAYLOAD_PROPERTY + ") " +
            "VALUES (?, ?, ?, ?, ?, ?);";


    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void insert(List<DevicePublishMsgEntity> entities) {
        jdbcTemplate.batchUpdate(INSERT, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                DevicePublishMsgEntity devicePublishMsgEntity = entities.get(i);
                ps.setString(1, devicePublishMsgEntity.getClientId());
                ps.setString(2, devicePublishMsgEntity.getTopic());
                ps.setLong(3, devicePublishMsgEntity.getSerialNumber());
                ps.setLong(4, devicePublishMsgEntity.getTime());
                ps.setInt(5, devicePublishMsgEntity.getQos());
                ps.setBytes(6, devicePublishMsgEntity.getPayload());
            }

            @Override
            public int getBatchSize() {
                return entities.size();
            }
        });
    }
}
