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
package org.thingsboard.mqtt.broker.dao.messages.sql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.mqtt.broker.dao.messages.DeletePacketInfo;
import org.thingsboard.mqtt.broker.dao.messages.UpdatePacketTypeInfo;
import org.thingsboard.mqtt.broker.dao.messages.LowLevelDeviceMsgRepository;
import org.thingsboard.mqtt.broker.dao.model.ModelConstants;
import org.thingsboard.mqtt.broker.dao.model.sql.DevicePublishMsgEntity;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Repository
@Transactional
public class SqlLowLevelDeviceMsgRepository implements LowLevelDeviceMsgRepository {
    private static final String DEVICE_PUBLISH_MSG = ModelConstants.DEVICE_PUBLISH_MSG_COLUMN_FAMILY_NAME;
    private static final String CLIENT_ID = ModelConstants.DEVICE_PUBLISH_MSG_CLIENT_ID_PROPERTY;
    private static final String TOPIC = ModelConstants.DEVICE_PUBLISH_MSG_TOPIC_PROPERTY;
    private static final String SERIAL_NUMBER = ModelConstants.DEVICE_PUBLISH_MSG_SERIAL_NUMBER_PROPERTY;
    private static final String PACKET_ID = ModelConstants.DEVICE_PUBLISH_MSG_PACKET_ID_PROPERTY;
    private static final String PACKET_TYPE = ModelConstants.DEVICE_PUBLISH_MSG_PACKET_TYPE_PROPERTY;
    private static final String TIME = ModelConstants.DEVICE_PUBLISH_MSG_TIME_PROPERTY;
    private static final String QOS = ModelConstants.DEVICE_PUBLISH_MSG_QOS_PROPERTY;
    private static final String PAYLOAD = ModelConstants.DEVICE_PUBLISH_MSG_PAYLOAD_PROPERTY;

    private static final String INSERT = "INSERT INTO " + DEVICE_PUBLISH_MSG + " (" +
            CLIENT_ID + ", " + TOPIC + ", " + SERIAL_NUMBER + ", " + PACKET_ID + ", " +
            PACKET_TYPE + ", " + TIME + ", " + QOS + ", " + PAYLOAD + ") " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?);";

    private static final String UPDATE_PACKET_TYPE = "UPDATE " + DEVICE_PUBLISH_MSG + " SET " +
            PACKET_TYPE + "=?  WHERE " + CLIENT_ID + "=? AND " + PACKET_ID + "=?;";

    private static final String DELETE_PACKET = "DELETE FROM " + DEVICE_PUBLISH_MSG + " WHERE " +
            CLIENT_ID + "=? AND " + PACKET_ID + "=?;";

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
                ps.setInt(4, devicePublishMsgEntity.getPacketId());
                ps.setString(5, devicePublishMsgEntity.getPacketType().toString());
                ps.setLong(6, devicePublishMsgEntity.getTime());
                ps.setInt(7, devicePublishMsgEntity.getQos());
                ps.setBytes(8, devicePublishMsgEntity.getPayload());
            }

            @Override
            public int getBatchSize() {
                return entities.size();
            }
        });
    }

    @Override
    @Transactional
    public void updatePacketTypes(List<UpdatePacketTypeInfo> packets) {
        int[] result = jdbcTemplate.batchUpdate(UPDATE_PACKET_TYPE, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                UpdatePacketTypeInfo packet = packets.get(i);
                ps.setString(1, packet.getPacketType().toString());
                ps.setString(2, packet.getClientId());
                ps.setInt(3, packet.getPacketId());
            }

            @Override
            public int getBatchSize() {
                return packets.size();
            }
        });
        int updatedPacketTypes = IntStream.of(result).sum();
        if (updatedPacketTypes != packets.size()) {
            log.debug("Expected to update {} packet types, actually updated {} packets", packets.size(), updatedPacketTypes);
        }
    }

    @Override
    @Transactional
    public void removePackets(List<DeletePacketInfo> packets) {
        int[] result = jdbcTemplate.batchUpdate(DELETE_PACKET, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                DeletePacketInfo packetToDelete = packets.get(i);
                ps.setString(1, packetToDelete.getClientId());
                ps.setInt(2, packetToDelete.getPacketId());
            }

            @Override
            public int getBatchSize() {
                return packets.size();
            }
        });
        int deletedPackets = IntStream.of(result).sum();
        if (deletedPackets != packets.size()) {
            log.debug("Expected to delete {} packet, actually deleted {} packets", packets.size(), deletedPackets);
        }
    }
}
