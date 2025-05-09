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
package org.thingsboard.mqtt.broker.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.server.MqttServerBootstrap;

import java.util.Optional;

@Component("tbmq")
public class TbmqHealthIndicator implements HealthIndicator {

    private final Optional<MqttServerBootstrap> tcpServerOpt;
    private final Optional<MqttServerBootstrap> tlsServerOpt;
    private final Optional<MqttServerBootstrap> wsServerOpt;
    private final Optional<MqttServerBootstrap> wssServerOpt;

    @Autowired
    public TbmqHealthIndicator(@Qualifier("mqttTcpServerBootstrap") Optional<MqttServerBootstrap> tcpServerOpt,
                               @Qualifier("mqttSslServerBootstrap") Optional<MqttServerBootstrap> tlsServerOpt,
                               @Qualifier("mqttWsServerBootstrap") Optional<MqttServerBootstrap> wsServerOpt,
                               @Qualifier("mqttWssServerBootstrap") Optional<MqttServerBootstrap> wssServerOpt) {
        this.tcpServerOpt = tcpServerOpt;
        this.tlsServerOpt = tlsServerOpt;
        this.wsServerOpt = wsServerOpt;
        this.wssServerOpt = wssServerOpt;
    }

    @Override
    public Health health() {
        boolean tcpHealthy = isHealthy(tcpServerOpt);
        boolean tlsHealthy = isHealthy(tlsServerOpt);
        boolean wsHealthy = isHealthy(wsServerOpt);
        boolean wssHealthy = isHealthy(wssServerOpt);
        if (tcpHealthy && tlsHealthy && wsHealthy && wssHealthy) {
            return Health.up().build();
        }
        return Health.down().build();
    }

    private boolean isHealthy(Optional<MqttServerBootstrap> serverBootstrapOpt) {
        return serverBootstrapOpt
                .map(server -> server.getServerChannel().isOpen() && server.getServerChannel().isActive())
                .orElse(true);
    }

}
