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
package org.thingsboard.mqtt.broker.common.data.security.basic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BasicMqttAuthProviderConfiguration implements MqttAuthProviderConfiguration {

    @JsonIgnore
    private AuthStrategy authStrategy;

    @Override
    public MqttAuthProviderType getType() {
        return MqttAuthProviderType.MQTT_BASIC;
    }

    public static BasicMqttAuthProviderConfiguration defaultConfiguration() {
        var basicConfig = new BasicMqttAuthProviderConfiguration();
        basicConfig.setAuthStrategy(AuthStrategy.CLIENT_ID_AND_USERNAME);
        return basicConfig;
    }

    @JsonProperty("authStrategy")
    public AuthStrategy getAuthStrategy() {
        return authStrategy == null ? AuthStrategy.CLIENT_ID_AND_USERNAME : authStrategy;
    }

    @JsonProperty("authStrategy")
    public void setAuthStrategy(AuthStrategy authStrategy) {
        this.authStrategy = authStrategy;
    }

    public enum AuthStrategy {
        CLIENT_ID,
        USERNAME,
        CLIENT_ID_AND_USERNAME
    }
}
