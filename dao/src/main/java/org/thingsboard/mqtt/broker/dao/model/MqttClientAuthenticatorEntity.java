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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLJsonPGObjectJsonbType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientAuthenticator;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientAuthenticatorConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientAuthenticatorType;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.util.mapping.JsonConverter;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = ModelConstants.MQTT_CLIENT_AUTHENTICATOR_COLUMN_FAMILY_NAME)
public class MqttClientAuthenticatorEntity extends BaseSqlEntity<MqttClientAuthenticator> implements BaseEntity<MqttClientAuthenticator> {

    @Enumerated(EnumType.STRING)
    @Column(name = ModelConstants.MQTT_CLIENT_AUTHENTICATOR_TYPE_PROPERTY)
    private MqttClientAuthenticatorType type;

    @Convert(converter = JsonConverter.class)
    @JdbcType(PostgreSQLJsonPGObjectJsonbType.class)
    @Column(name = ModelConstants.MQTT_CLIENT_AUTHENTICATOR_CONFIGURATION_PROPERTY, columnDefinition = "jsonb")
    private JsonNode configuration;

    public MqttClientAuthenticatorEntity() {
    }

    public MqttClientAuthenticatorEntity(MqttClientAuthenticator mqttClientAuthenticator) {
        if (mqttClientAuthenticator.getId() != null) {
            this.setId(mqttClientAuthenticator.getId());
        }
        this.setCreatedTime(mqttClientAuthenticator.getCreatedTime());
        this.type = mqttClientAuthenticator.getType();
        this.configuration = JacksonUtil.convertValue(
                mqttClientAuthenticator.getMqttClientAuthenticatorConfiguration(), ObjectNode.class);
    }

    @Override
    public MqttClientAuthenticator toData() {
        MqttClientAuthenticator mqttClientAuthenticator = new MqttClientAuthenticator();
        mqttClientAuthenticator.setId(id);
        mqttClientAuthenticator.setCreatedTime(createdTime);
        mqttClientAuthenticator.setType(type);
        mqttClientAuthenticator.setMqttClientAuthenticatorConfiguration(
                JacksonUtil.convertValue(configuration, MqttClientAuthenticatorConfiguration.class));
        return mqttClientAuthenticator;
    }

}
