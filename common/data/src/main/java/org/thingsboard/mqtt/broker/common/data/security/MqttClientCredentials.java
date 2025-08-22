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
package org.thingsboard.mqtt.broker.common.data.security;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.thingsboard.mqtt.broker.common.data.BaseDataWithAdditionalInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.validation.Length;
import org.thingsboard.mqtt.broker.common.data.validation.NoXss;

import java.io.Serial;
import java.util.UUID;

@Data
@ToString
@EqualsAndHashCode(callSuper = true)
public class MqttClientCredentials extends BaseDataWithAdditionalInfo {

    @Serial
    private static final long serialVersionUID = -8551302106113554112L;

    @NoXss
    private String credentialsId;
    @NoXss
    @Length
    private String name;
    private ClientType clientType;
    private ClientCredentialsType credentialsType;
    @NoXss
    private String credentialsValue;

    public MqttClientCredentials() {
    }

    public MqttClientCredentials(UUID id) {
        super(id);
    }

    public MqttClientCredentials(MqttClientCredentials mqttClientCredentials) {
        super(mqttClientCredentials);
        this.name = mqttClientCredentials.getName();
        this.clientType = mqttClientCredentials.getClientType();
        this.credentialsId = mqttClientCredentials.getCredentialsId();
        this.credentialsType = mqttClientCredentials.getCredentialsType();
        this.credentialsValue = mqttClientCredentials.getCredentialsValue();
    }

}
