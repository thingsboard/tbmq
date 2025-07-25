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
package org.thingsboard.mqtt.broker.common.data.security.ssl;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SslMqttAuthProviderConfiguration implements MqttAuthProviderConfiguration {

    private boolean skipValidityCheckForClientCert;
    private MqttClientAuthType clientAuthType;

    @Override
    public MqttAuthProviderType getType() {
        return MqttAuthProviderType.X_509;
    }

    public static SslMqttAuthProviderConfiguration defaultConfiguration() {
        var x509Config = new SslMqttAuthProviderConfiguration();
        x509Config.setSkipValidityCheckForClientCert(false);
        x509Config.setClientAuthType(MqttClientAuthType.CLIENT_AUTH_REQUESTED);
        return x509Config;
    }

}
