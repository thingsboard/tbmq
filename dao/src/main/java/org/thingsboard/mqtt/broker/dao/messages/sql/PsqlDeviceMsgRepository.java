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
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgRepository;
import org.thingsboard.mqtt.broker.dao.messages.TopicFilterQuery;
import org.thingsboard.mqtt.broker.dao.model.ModelConstants;
import org.thingsboard.mqtt.broker.dao.model.sql.DevicePublishMsgEntity;
import org.thingsboard.mqtt.broker.dao.util.PsqlDao;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@PsqlDao
@Repository
public class PsqlDeviceMsgRepository implements DeviceMsgRepository {

    private static final String INSERT = "INSERT INTO " + ModelConstants.DEVICE_PUBLISH_MSG_COLUMN_FAMILY_NAME + " (" +
            ModelConstants.DEVICE_PUBLISH_MSG_TIMESTAMP_PROPERTY + ", " +
            ModelConstants.DEVICE_PUBLISH_MSG_TOPIC_PROPERTY + ", " +
            ModelConstants.DEVICE_PUBLISH_MSG_QOS_PROPERTY + ", " +
            ModelConstants.DEVICE_PUBLISH_MSG_PAYLOAD_PROPERTY + ") " +
            "VALUES (?, ?, ?, ?);";

    private static final String SELECT_TEMPLATE = "SELECT " + ModelConstants.DEVICE_PUBLISH_MSG_TIMESTAMP_PROPERTY + ", " +
            ModelConstants.DEVICE_PUBLISH_MSG_TOPIC_PROPERTY + ", " +
            ModelConstants.DEVICE_PUBLISH_MSG_QOS_PROPERTY + ", " +
            ModelConstants.DEVICE_PUBLISH_MSG_PAYLOAD_PROPERTY + " FROM " +
            ModelConstants.DEVICE_PUBLISH_MSG_COLUMN_FAMILY_NAME +
            " WHERE (%s) LIMIT %s;";

    private static final String SELECT_DISTINCT_TOPICS = "SELECT DISTINCT " + ModelConstants.DEVICE_PUBLISH_MSG_TOPIC_PROPERTY + " FROM " +
            ModelConstants.DEVICE_PUBLISH_MSG_COLUMN_FAMILY_NAME + ";";


    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void insert(List<DevicePublishMsgEntity> entities) {
        jdbcTemplate.batchUpdate(INSERT, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                DevicePublishMsgEntity devicePublishMsgEntity = entities.get(i);
                ps.setLong(1, devicePublishMsgEntity.getTimestamp());
                ps.setString(2, devicePublishMsgEntity.getTopic());
                ps.setInt(3, devicePublishMsgEntity.getQos());
                ps.setBytes(4, devicePublishMsgEntity.getPayload());
            }

            @Override
            public int getBatchSize() {
                return entities.size();
            }
        });
    }

    @Override
    public List<DevicePublishMsgEntity> findByQuery(List<TopicFilterQuery> topicFilterQueries, int limit) {
        String whereClause = topicFilterQueries.stream()
                .map(PsqlDeviceMsgRepository::convertTopicFilterQuery)
                .collect(Collectors.joining(" OR "));
        String query = String.format(SELECT_TEMPLATE, whereClause, limit);

        log.trace("Finding device messages by query {}.", query);
        List<DevicePublishMsgEntity> entities = new ArrayList<>();
        jdbcTemplate.query(query, rs -> {
            DevicePublishMsgEntity devicePublishMsgEntity = extractDeviceMsgEntity(rs);
            entities.add(devicePublishMsgEntity);
        });
        return entities;
    }

    @Override
    public List<String> findAllTopics() {
        return jdbcTemplate.queryForList(SELECT_DISTINCT_TOPICS, String.class);
    }

    private static DevicePublishMsgEntity extractDeviceMsgEntity(java.sql.ResultSet rs) throws SQLException {
        DevicePublishMsgEntity devicePublishMsgEntity = new DevicePublishMsgEntity();
        devicePublishMsgEntity.setTimestamp(rs.getLong(ModelConstants.DEVICE_PUBLISH_MSG_TIMESTAMP_PROPERTY));
        devicePublishMsgEntity.setTopic(rs.getString(ModelConstants.DEVICE_PUBLISH_MSG_TOPIC_PROPERTY));
        devicePublishMsgEntity.setQos(rs.getInt(ModelConstants.DEVICE_PUBLISH_MSG_QOS_PROPERTY));
        devicePublishMsgEntity.setPayload(rs.getBytes(ModelConstants.DEVICE_PUBLISH_MSG_PAYLOAD_PROPERTY));
        return devicePublishMsgEntity;
    }

    private static String convertTopicFilterQuery(TopicFilterQuery topicFilterQuery) {
        return "(" +
                ModelConstants.DEVICE_PUBLISH_MSG_TOPIC_PROPERTY +
                " IN (" + String.join(", ", topicFilterQuery.getMatchingTopics()) + ")" +
                " AND " +
                ModelConstants.DEVICE_PUBLISH_MSG_TIMESTAMP_PROPERTY +
                " > " + topicFilterQuery.getLastTimestamp() +
                ")";
    }
}
