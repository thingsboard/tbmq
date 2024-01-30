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
package org.thingsboard.mqtt.broker.dao.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnectionConfiguration;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.util.mapping.JsonBinaryType;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
@Table(name = ModelConstants.WEBSOCKET_CONNECTION_COLUMN_FAMILY_NAME)
public class WebSocketConnectionEntity extends BaseSqlEntity<WebSocketConnection> implements SearchTextEntity<WebSocketConnection> {

    @Column(name = ModelConstants.WEBSOCKET_CONNECTION_NAME_PROPERTY)
    private String name;

    @Column(name = ModelConstants.WEBSOCKET_CONNECTION_USER_ID_PROPERTY, columnDefinition = "uuid")
    private UUID userId;

    @Type(type = "jsonb")
    @Column(name = ModelConstants.WEBSOCKET_CONNECTION_CONFIGURATION_PROPERTY, columnDefinition = "jsonb")
    private JsonNode configuration;

    @Column(name = ModelConstants.SEARCH_TEXT_PROPERTY)
    private String searchText;

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
        this.searchText = connection.getSearchText();
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

    @Override
    public String getSearchTextSource() {
        return name;
    }

}
