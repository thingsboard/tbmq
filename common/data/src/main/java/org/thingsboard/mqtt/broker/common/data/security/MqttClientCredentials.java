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
package org.thingsboard.mqtt.broker.common.data.security;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.thingsboard.mqtt.broker.common.data.BaseData;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
public class MqttClientCredentials extends BaseData {
    @Getter
    @Setter
    private String clientId;
    @Getter
    @Setter
    private String credentialsId;
    @Getter
    @Setter
    private ClientCredentialsType credentialsType;
    @Getter
    @Setter
    private String credentialsValue;

    public MqttClientCredentials() {
    }

    public MqttClientCredentials(UUID id) {
        super(id);
    }

    public MqttClientCredentials(MqttClientCredentials mqttClientCredentials) {
        super(mqttClientCredentials);
        this.clientId = mqttClientCredentials.clientId;
        this.credentialsId = mqttClientCredentials.credentialsId;
        this.credentialsType = mqttClientCredentials.credentialsType;
        this.credentialsValue = mqttClientCredentials.credentialsValue;
    }


    @Override
    public String toString() {
        return "MqttClientCredentials [clientId=" +
                clientId +
                ", credentialsId=" +
                credentialsId +
                ", credentialsType=" +
                credentialsType +
                ", credentialsValue=" +
                credentialsValue +
                ", createdTime=" +
                createdTime +
                ", id=" +
                id +
                "]";
    }
}
