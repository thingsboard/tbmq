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
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.util.mapping.JsonConverter;

import static org.thingsboard.mqtt.broker.dao.model.ModelConstants.MQTT_AUTH_PROVIDER_ADDITIONAL_INFO_PROPERTY;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = ModelConstants.MQTT_AUTH_PROVIDER_COLUMN_FAMILY_NAME)
public class MqttAuthProviderEntity extends BaseSqlEntity<MqttAuthProvider> implements BaseEntity<MqttAuthProvider> {

    @Column(name = ModelConstants.MQTT_AUTH_PROVIDER_ENABLED_PROPERTY)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = ModelConstants.MQTT_AUTH_PROVIDER_TYPE_PROPERTY)
    private MqttAuthProviderType type;

    @Convert(converter = JsonConverter.class)
    @JdbcType(PostgreSQLJsonPGObjectJsonbType.class)
    @Column(name = ModelConstants.MQTT_AUTH_PROVIDER_CONFIGURATION_PROPERTY, columnDefinition = "jsonb")
    private JsonNode configuration;

    @Convert(converter = JsonConverter.class)
    @Column(name = MQTT_AUTH_PROVIDER_ADDITIONAL_INFO_PROPERTY)
    private JsonNode additionalInfo;

    public MqttAuthProviderEntity() {
    }

    public MqttAuthProviderEntity(MqttAuthProvider mqttAuthProvider) {
        if (mqttAuthProvider.getId() != null) {
            this.setId(mqttAuthProvider.getId());
        }
        this.setCreatedTime(mqttAuthProvider.getCreatedTime());
        this.enabled = mqttAuthProvider.isEnabled();
        this.type = mqttAuthProvider.getType();
        this.configuration = JacksonUtil.convertValue(
                mqttAuthProvider.getConfiguration(), ObjectNode.class);
        this.additionalInfo = mqttAuthProvider.getAdditionalInfo();
    }

    @Override
    public MqttAuthProvider toData() {
        MqttAuthProvider mqttAuthProvider = new MqttAuthProvider();
        mqttAuthProvider.setId(id);
        mqttAuthProvider.setCreatedTime(createdTime);
        mqttAuthProvider.setEnabled(enabled);
        mqttAuthProvider.setType(type);
        mqttAuthProvider.setConfiguration(
                JacksonUtil.convertValue(configuration, MqttAuthProviderConfiguration.class));
        mqttAuthProvider.setAdditionalInfo(additionalInfo);
        return mqttAuthProvider;
    }

}
