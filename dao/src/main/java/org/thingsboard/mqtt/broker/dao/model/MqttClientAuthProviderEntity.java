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
import org.thingsboard.mqtt.broker.common.data.security.MqttClientAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientAuthProviderType;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.util.mapping.JsonConverter;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = ModelConstants.MQTT_CLIENT_AUTH_PROVIDER_COLUMN_FAMILY_NAME)
public class MqttClientAuthProviderEntity extends BaseSqlEntity<MqttClientAuthProvider> implements BaseEntity<MqttClientAuthProvider> {

    @Column(name = ModelConstants.MQTT_CLIENT_AUTH_PROVIDER_ENABLED_PROPERTY)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = ModelConstants.MQTT_CLIENT_AUTH_PROVIDER_TYPE_PROPERTY)
    private MqttClientAuthProviderType type;

    @Convert(converter = JsonConverter.class)
    @JdbcType(PostgreSQLJsonPGObjectJsonbType.class)
    @Column(name = ModelConstants.MQTT_CLIENT_AUTH_PROVIDER_CONFIGURATION_PROPERTY, columnDefinition = "jsonb")
    private JsonNode configuration;

    public MqttClientAuthProviderEntity() {
    }

    public MqttClientAuthProviderEntity(MqttClientAuthProvider mqttClientAuthProvider) {
        if (mqttClientAuthProvider.getId() != null) {
            this.setId(mqttClientAuthProvider.getId());
        }
        this.setCreatedTime(mqttClientAuthProvider.getCreatedTime());
        this.enabled = mqttClientAuthProvider.isEnabled();
        this.type = mqttClientAuthProvider.getType();
        this.configuration = JacksonUtil.convertValue(
                mqttClientAuthProvider.getMqttClientAuthProviderConfiguration(), ObjectNode.class);
    }

    @Override
    public MqttClientAuthProvider toData() {
        MqttClientAuthProvider mqttClientAuthProvider = new MqttClientAuthProvider();
        mqttClientAuthProvider.setId(id);
        mqttClientAuthProvider.setCreatedTime(createdTime);
        mqttClientAuthProvider.setEnabled(enabled);
        mqttClientAuthProvider.setType(type);
        mqttClientAuthProvider.setMqttClientAuthProviderConfiguration(
                JacksonUtil.convertValue(configuration, MqttClientAuthProviderConfiguration.class));
        return mqttClientAuthProvider;
    }

}
