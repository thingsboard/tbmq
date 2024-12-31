/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLJsonPGObjectJsonbType;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnectionConfiguration;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.util.mapping.JsonConverter;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity

@Table(name = ModelConstants.WEBSOCKET_CONNECTION_COLUMN_FAMILY_NAME)
public class WebSocketConnectionEntity extends BaseSqlEntity<WebSocketConnection> implements BaseEntity<WebSocketConnection> {

    @Column(name = ModelConstants.WEBSOCKET_CONNECTION_NAME_PROPERTY)
    private String name;

    @Column(name = ModelConstants.WEBSOCKET_CONNECTION_USER_ID_PROPERTY, columnDefinition = "uuid")
    private UUID userId;

    @Convert(converter = JsonConverter.class)
    @JdbcType(PostgreSQLJsonPGObjectJsonbType.class)
    @Column(name = ModelConstants.WEBSOCKET_CONNECTION_CONFIGURATION_PROPERTY, columnDefinition = "jsonb")
    private JsonNode configuration;

    public WebSocketConnectionEntity() {
    }

    public WebSocketConnectionEntity(WebSocketConnection connection) {
        if (connection.getId() != null) {
            this.setId(connection.getId());
        }
        this.setCreatedTime(connection.getCreatedTime());
        this.name = connection.getName();
        if (connection.getUserId() != null) {
            this.userId = connection.getUserId();
        }
        this.configuration = JacksonUtil.convertValue(connection.getConfiguration(), ObjectNode.class);
    }

    @Override
    public WebSocketConnection toData() {
        WebSocketConnection webSocketConnection = new WebSocketConnection();
        webSocketConnection.setId(id);
        webSocketConnection.setCreatedTime(createdTime);
        webSocketConnection.setName(name);
        if (userId != null) {
            webSocketConnection.setUserId(userId);
        }
        webSocketConnection.setConfiguration(JacksonUtil.convertValue(configuration, WebSocketConnectionConfiguration.class));
        return webSocketConnection;
    }

}
