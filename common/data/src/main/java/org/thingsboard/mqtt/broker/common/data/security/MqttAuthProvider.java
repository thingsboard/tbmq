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
import org.thingsboard.mqtt.broker.common.data.BaseData;
import org.thingsboard.mqtt.broker.common.data.security.basic.BasicMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.jwt.JwtMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.scram.ScramMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.security.ssl.SslMqttAuthProviderConfiguration;
import org.thingsboard.mqtt.broker.common.data.validation.NoXss;

import java.io.Serial;

@Data
@ToString
@EqualsAndHashCode(callSuper = true)
public class MqttAuthProvider extends BaseData {

    @Serial
    private static final long serialVersionUID = 464223366680445871L;

    private boolean enabled;

    @NoXss
    private MqttAuthProviderType type;
    private MqttAuthProviderConfiguration configuration;

    public static MqttAuthProvider defaultBasicAuthProvider() {
        MqttAuthProvider basicMqttAuthProvider = new MqttAuthProvider();
        basicMqttAuthProvider.setType(MqttAuthProviderType.MQTT_BASIC);
        basicMqttAuthProvider.setEnabled(false);
        basicMqttAuthProvider.setConfiguration(new BasicMqttAuthProviderConfiguration());
        return basicMqttAuthProvider;
    }

    public static MqttAuthProvider defaultSslAuthProvider() {
        MqttAuthProvider sslMqttAuthProvider = new MqttAuthProvider();
        sslMqttAuthProvider.setType(MqttAuthProviderType.X_509);
        sslMqttAuthProvider.setEnabled(false);
        var sslConfig = new SslMqttAuthProviderConfiguration();
        sslConfig.setSkipValidityCheckForClientCert(false);
        sslMqttAuthProvider.setConfiguration(sslConfig);
        return sslMqttAuthProvider;
    }

    public static MqttAuthProvider defaultJwtAuthProvider() {
        MqttAuthProvider jwtMqttAuthProvider = new MqttAuthProvider();
        jwtMqttAuthProvider.setType(MqttAuthProviderType.JWT);
        jwtMqttAuthProvider.setEnabled(false);
        jwtMqttAuthProvider.setConfiguration(JwtMqttAuthProviderConfiguration.defaultConfiguration());
        return jwtMqttAuthProvider;
    }

    public static MqttAuthProvider defaultScramAuthProvider() {
        MqttAuthProvider scramMqttAuthProvider = new MqttAuthProvider();
        scramMqttAuthProvider.setType(MqttAuthProviderType.SCRAM);
        scramMqttAuthProvider.setEnabled(false);
        scramMqttAuthProvider.setConfiguration(new ScramMqttAuthProviderConfiguration());
        return scramMqttAuthProvider;
    }

}
