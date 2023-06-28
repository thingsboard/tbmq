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
package org.thingsboard.mqtt.broker.dao.messages.sql;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.mqtt.broker.dao.messages.DeletePacketInfo;
import org.thingsboard.mqtt.broker.dao.messages.LowLevelDeviceMsgRepository;
import org.thingsboard.mqtt.broker.dao.messages.UpdatePacketTypeInfo;
import org.thingsboard.mqtt.broker.dao.model.sql.DevicePublishMsgEntity;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Repository
@Transactional
@RequiredArgsConstructor
public class SqlLowLevelDeviceMsgRepository implements LowLevelDeviceMsgRepository {

    private static final String INSERT_OR_UPDATE = "INSERT INTO device_publish_msg " +
            "(client_id, topic, serial_number, packet_id, packet_type, time, qos, payload, user_properties, retain)" +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT (client_id, serial_number) DO UPDATE SET " +
            "topic = ?, packet_id = ?, packet_type = ?, time = ?, qos = ?, payload = ?, user_properties = ?, retain = ?;";

    private static final String INSERT = "INSERT INTO device_publish_msg " +
            "(client_id, topic, serial_number, packet_id, packet_type, time, qos, payload, user_properties, retain) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

    private static final String UPDATE_PACKET_TYPE = "UPDATE device_publish_msg SET packet_type = ? " +
            "WHERE client_id = ? AND packet_id = ?;";

    private static final String DELETE_PACKET = "DELETE FROM device_publish_msg " +
            "WHERE client_id = ? AND packet_id = ?;";

    private static final String DELETE_PACKETS_BY_CLIENT_ID = "DELETE FROM device_publish_msg " +
            "WHERE client_id = ?;";

    private final JdbcTemplate jdbcTemplate;

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
                ps.setString(9, devicePublishMsgEntity.getUserProperties());
                ps.setBoolean(10, devicePublishMsgEntity.isRetain());
            }

            @Override
            public int getBatchSize() {
                return entities.size();
            }
        });
    }

    @Override
    public void insertOrUpdate(List<DevicePublishMsgEntity> entities) {
        jdbcTemplate.batchUpdate(INSERT_OR_UPDATE, new BatchPreparedStatementSetter() {
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
                ps.setString(9, devicePublishMsgEntity.getUserProperties());
                ps.setBoolean(10, devicePublishMsgEntity.isRetain());
                ps.setString(11, devicePublishMsgEntity.getTopic());
                ps.setInt(12, devicePublishMsgEntity.getPacketId());
                ps.setString(13, devicePublishMsgEntity.getPacketType().toString());
                ps.setLong(14, devicePublishMsgEntity.getTime());
                ps.setInt(15, devicePublishMsgEntity.getQos());
                ps.setBytes(16, devicePublishMsgEntity.getPayload());
                ps.setString(17, devicePublishMsgEntity.getUserProperties());
                ps.setBoolean(18, devicePublishMsgEntity.isRetain());
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
            if (log.isDebugEnabled()) {
                log.debug("Expected to update {} packet types, actually updated {} packets", packets.size(), updatedPacketTypes);
            }
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
            if (log.isDebugEnabled()) {
                log.debug("Expected to delete {} packet, actually deleted {} packets", packets.size(), deletedPackets);
            }
        }
    }

    @Override
    public void removePacketsByClientId(String clientId) {
        int removedPackets = jdbcTemplate.update(DELETE_PACKETS_BY_CLIENT_ID, ps -> {
            ps.setString(1, clientId);
        });
        if (log.isTraceEnabled()) {
            log.trace("Removed {} packets for client {}", removedPackets, clientId);
        }
    }
}
