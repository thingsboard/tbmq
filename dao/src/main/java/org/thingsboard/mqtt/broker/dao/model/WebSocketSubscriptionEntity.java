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
package org.thingsboard.mqtt.broker.dao.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscription;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscriptionConfiguration;
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
@Table(name = ModelConstants.WEBSOCKET_SUBSCRIPTION_COLUMN_FAMILY_NAME)
public class WebSocketSubscriptionEntity extends BaseSqlEntity<WebSocketSubscription> implements BaseEntity<WebSocketSubscription> {

    @Column(name = ModelConstants.WEBSOCKET_SUBSCRIPTION_CONNECTION_ID_PROPERTY, columnDefinition = "uuid")
    private UUID webSocketConnectionId;

    @Type(type = "jsonb")
    @Column(name = ModelConstants.WEBSOCKET_SUBSCRIPTION_CONFIGURATION_PROPERTY, columnDefinition = "jsonb")
    private JsonNode configuration;

    public WebSocketSubscriptionEntity() {
    }

    public WebSocketSubscriptionEntity(WebSocketSubscription subscription) {
        if (subscription.getId() != null) {
            this.setId(subscription.getId());
        }
        this.setCreatedTime(subscription.getCreatedTime());
        if (subscription.getWebSocketConnectionId() != null) {
            this.webSocketConnectionId = subscription.getWebSocketConnectionId();
        }
        this.configuration = JacksonUtil.convertValue(subscription.getConfiguration(), ObjectNode.class);
    }

    @Override
    public WebSocketSubscription toData() {
        WebSocketSubscription webSocketSubscription = new WebSocketSubscription();
        webSocketSubscription.setId(id);
        webSocketSubscription.setCreatedTime(createdTime);
        if (webSocketConnectionId != null) {
            webSocketSubscription.setWebSocketConnectionId(webSocketConnectionId);
        }
        webSocketSubscription.setConfiguration(JacksonUtil.convertValue(configuration, WebSocketSubscriptionConfiguration.class));
        return webSocketSubscription;
    }

}
