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
package org.thingsboard.mqtt.broker.dao.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.MqttClient;

import javax.persistence.*;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = ModelConstants.MQTT_CLIENT_COLUMN_FAMILY_NAME)
public class MqttClientEntity extends BaseEntity<MqttClient> {

    @Column(name = ModelConstants.MQTT_CLIENT_CLIENT_ID_PROPERTY, unique = true)
    private String clientId;

    @Column(name = ModelConstants.MQTT_CLIENT_NAME_PROPERTY)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = ModelConstants.MQTT_CLIENT_TYPE_PROPERTY)
    private ClientType type;

    public MqttClientEntity() {
    }

    public MqttClientEntity(MqttClient mqttClient) {
        if (mqttClient.getId() != null) {
            this.setId(mqttClient.getId());
        }
        this.setCreatedTime(mqttClient.getCreatedTime());
        this.clientId = mqttClient.getClientId();
        this.name = mqttClient.getName();
        this.type = mqttClient.getType();
    }

    @Override
    public MqttClient toData() {
        MqttClient mqttClient = new MqttClient(id);
        mqttClient.setCreatedTime(createdTime);
        mqttClient.setClientId(clientId);
        mqttClient.setName(name);
        mqttClient.setType(type);
        return mqttClient;
    }
}
