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
package org.thingsboard.mqtt.broker.server.wss;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.server.AbstractMqttHandlerProvider;
import org.thingsboard.mqtt.broker.ssl.config.SslCredentials;
import org.thingsboard.mqtt.broker.ssl.config.SslCredentialsConfig;

@Slf4j
@Component("MqttWssHandlerProvider")
@ConditionalOnProperty(prefix = "listener.wss", value = "enabled", havingValue = "true", matchIfMissing = false)
public class MqttWssHandlerProvider extends AbstractMqttHandlerProvider {

    @Value("${listener.wss.config.protocol}")
    private String sslProtocol;

    @Bean
    @ConfigurationProperties(prefix = "listener.wss.config.credentials")
    public SslCredentialsConfig mqttWssCredentials() {
        return new SslCredentialsConfig("MQTT WSS Credentials", false);
    }

    @Autowired(required = false)
    @Lazy
    @Qualifier("mqttWssCredentials")
    private SslCredentialsConfig mqttWssCredentialsConfig;

    @Override
    protected String getSslProtocol() {
        return sslProtocol;
    }

    @Override
    protected SslCredentials getSslCredentials() {
        return this.mqttWssCredentialsConfig.getCredentials();
    }

}
