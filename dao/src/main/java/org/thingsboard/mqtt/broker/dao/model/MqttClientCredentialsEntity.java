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
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;

import javax.persistence.*;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = ModelConstants.MQTT_CLIENT_CREDENTIALS_COLUMN_FAMILY_NAME)
public class MqttClientCredentialsEntity extends BaseEntity<MqttClientCredentials> {

    @Column(name = ModelConstants.MQTT_CLIENT_CREDENTIALS_NAME_PROPERTY)
    private String name;

    @Column(name = ModelConstants.MQTT_CLIENT_CREDENTIALS_ID_PROPERTY, unique = true)
    private String credentialsId;

    @Enumerated(EnumType.STRING)
    @Column(name = ModelConstants.MQTT_CLIENT_CREDENTIALS_TYPE_PROPERTY)
    private ClientCredentialsType credentialsType;

    @Column(name = ModelConstants.MQTT_CLIENT_CREDENTIALS_VALUE_PROPERTY)
    private String credentialsValue;

    public MqttClientCredentialsEntity() {
    }

    public MqttClientCredentialsEntity(MqttClientCredentials mqttClientCredentials) {
        if (mqttClientCredentials.getId() != null) {
            this.setId(mqttClientCredentials.getId());
        }
        this.setCreatedTime(mqttClientCredentials.getCreatedTime());
        this.name = mqttClientCredentials.getName();
        this.credentialsId = mqttClientCredentials.getCredentialsId();
        this.credentialsType = mqttClientCredentials.getCredentialsType();
        this.credentialsValue = mqttClientCredentials.getCredentialsValue();
    }

    @Override
    public MqttClientCredentials toData() {
        MqttClientCredentials mqttClientCredentials = new MqttClientCredentials(id);
        mqttClientCredentials.setCreatedTime(createdTime);
        mqttClientCredentials.setName(name);
        mqttClientCredentials.setCredentialsId(credentialsId);
        mqttClientCredentials.setCredentialsType(credentialsType);
        mqttClientCredentials.setCredentialsValue(credentialsValue);
        return mqttClientCredentials;
    }
}
