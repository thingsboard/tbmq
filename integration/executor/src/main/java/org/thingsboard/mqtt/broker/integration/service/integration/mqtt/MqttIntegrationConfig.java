/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.integration.service.integration.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.mqtt.broker.common.data.credentials.AnonymousCredentials;
import org.thingsboard.mqtt.broker.common.data.credentials.ClientCredentials;

import java.util.Objects;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
public class MqttIntegrationConfig {

    private boolean sendOnlyMsgPayload;
    private String host;
    private int port;
    private String topicName;
    private boolean useMsgTopicName;
    private String clientId;
    private ClientCredentials credentials;
    private boolean ssl;
    private int connectTimeoutSec;
    private int reconnectPeriodSec;
    private int mqttVersion; // Allowed values: 3, 4, 5. See io.netty.handler.codec.mqtt.MqttVersion
    private int qos; // Allowed values: 0, 1, 2. See org.thingsboard.mqtt.broker.common.data.MqttQoS
    private boolean useMsgQoS;
    private boolean retained;
    private boolean useMsgRetain;
    private int keepAliveSec; // Value 0 disables Keep Alive mechanism

    public ClientCredentials getCredentials() {
        return Objects.requireNonNullElseGet(this.credentials, AnonymousCredentials::new);
    }

}
