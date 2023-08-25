/**
 * Copyright © 2016-2023 The Thingsboard Authors
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

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dto.HomePageConfigDto;

@Component
@RequiredArgsConstructor
public class BrokerHomePageConfig {

    @Value("${listener.tcp.bind_port}")
    private int tcpPort;
    @Value("${listener.ssl.bind_port}")
    private int tlsPort;
    @Value("${listener.tcp.enabled}")
    private boolean tcpListenerEnabled;
    @Value("${listener.ssl.enabled}")
    private boolean tlsListenerEnabled;
    @Value("${listener.tcp.netty.max_payload_size}")
    private int tcpMaxPayloadSize;
    @Value("${listener.ssl.netty.max_payload_size}")
    private int tlsMaxPayloadSize;
    @Value("${security.mqtt.basic.enabled}")
    private boolean basicAuthEnabled;
    @Value("${security.mqtt.ssl.enabled}")
    private boolean x509AuthEnabled;

    @Value("${listener.ws.bind_port}")
    private int wsPort;
    @Value("${listener.wss.bind_port}")
    private int wssPort;
    @Value("${listener.ws.enabled}")
    private boolean wsListenerEnabled;
    @Value("${listener.wss.enabled}")
    private boolean wssListenerEnabled;
    @Value("${listener.ws.netty.max_payload_size}")
    private int wsMaxPayloadSize;
    @Value("${listener.wss.netty.max_payload_size}")
    private int wssMaxPayloadSize;

    public final MqttClientCredentialsService mqttClientCredentialsService;

    public HomePageConfigDto getConfig() {
        return HomePageConfigDto.builder()
                .basicAuthEnabled(isBasicAuthEnabled())
                .x509AuthEnabled(isX509AuthEnabled())
                .tcpPort(getTcpPort())
                .tlsPort(getTlsPort())
                .wsPort(getWsPort())
                .wssPort(getWssPort())
                .tcpListenerEnabled(isTcpListenerEnabled())
                .tlsListenerEnabled(isTlsListenerEnabled())
                .wsListenerEnabled(isWsListenerEnabled())
                .wssListenerEnabled(isWssListenerEnabled())
                .tcpMaxPayloadSize(getTcpMaxPayloadSize())
                .tlsMaxPayloadSize(getTlsMaxPayloadSize())
                .wsMaxPayloadSize(getWsMaxPayloadSize())
                .wssMaxPayloadSize(getWssMaxPayloadSize())
                .existsBasicCredentials(existsBasicCredentials())
                .existsX509Credentials(existsX509Credentials())
                .build();
    }

    private int getTcpPort() {
        String tcpPortStr = System.getenv("LISTENER_TCP_BIND_PORT");
        if (tcpPortStr != null) {
            return Integer.parseInt(tcpPortStr);
        } else {
            return tcpPort;
        }
    }

    private int getTlsPort() {
        String tlsPortStr = System.getenv("LISTENER_SSL_BIND_PORT");
        if (tlsPortStr != null) {
            return Integer.parseInt(tlsPortStr);
        } else {
            return tlsPort;
        }
    }

    private boolean isTcpListenerEnabled() {
        String tcpListenerEnabledStr = System.getenv("LISTENER_TCP_ENABLED");
        if (tcpListenerEnabledStr != null) {
            return Boolean.parseBoolean(tcpListenerEnabledStr);
        } else {
            return tcpListenerEnabled;
        }
    }

    private boolean isTlsListenerEnabled() {
        String tlsListenerEnabledStr = System.getenv("LISTENER_SSL_ENABLED");
        if (tlsListenerEnabledStr != null) {
            return Boolean.parseBoolean(tlsListenerEnabledStr);
        } else {
            return tlsListenerEnabled;
        }
    }

    private int getTcpMaxPayloadSize() {
        String tcpMaxPayloadSizeStr = System.getenv("TCP_NETTY_MAX_PAYLOAD_SIZE");
        if (tcpMaxPayloadSizeStr != null) {
            return Integer.parseInt(tcpMaxPayloadSizeStr);
        } else {
            return tcpMaxPayloadSize;
        }
    }

    private int getTlsMaxPayloadSize() {
        String tlsMaxPayloadSizeStr = System.getenv("SSL_NETTY_MAX_PAYLOAD_SIZE");
        if (tlsMaxPayloadSizeStr != null) {
            return Integer.parseInt(tlsMaxPayloadSizeStr);
        } else {
            return tlsMaxPayloadSize;
        }
    }

    private boolean isBasicAuthEnabled() {
        String basicAuthEnabledStr = System.getenv("SECURITY_MQTT_BASIC_ENABLED");
        if (basicAuthEnabledStr != null) {
            return Boolean.parseBoolean(basicAuthEnabledStr);
        } else {
            return basicAuthEnabled;
        }
    }

    private boolean isX509AuthEnabled() {
        String x509AuthEnabledStr = System.getenv("SECURITY_MQTT_SSL_ENABLED");
        if (x509AuthEnabledStr != null) {
            return Boolean.parseBoolean(x509AuthEnabledStr);
        } else {
            return x509AuthEnabled;
        }
    }

    private int getWsPort() {
        String wsPortStr = System.getenv("LISTENER_WS_BIND_PORT");
        if (wsPortStr != null) {
            return Integer.parseInt(wsPortStr);
        } else {
            return wsPort;
        }
    }

    private int getWssPort() {
        String wssPortStr = System.getenv("LISTENER_WSS_BIND_PORT");
        if (wssPortStr != null) {
            return Integer.parseInt(wssPortStr);
        } else {
            return wssPort;
        }
    }

    private boolean isWsListenerEnabled() {
        String wsListenerEnabledStr = System.getenv("LISTENER_WS_ENABLED");
        if (wsListenerEnabledStr != null) {
            return Boolean.parseBoolean(wsListenerEnabledStr);
        } else {
            return wsListenerEnabled;
        }
    }

    private boolean isWssListenerEnabled() {
        String wssListenerEnabledStr = System.getenv("LISTENER_WSS_ENABLED");
        if (wssListenerEnabledStr != null) {
            return Boolean.parseBoolean(wssListenerEnabledStr);
        } else {
            return wssListenerEnabled;
        }
    }

    private int getWsMaxPayloadSize() {
        String wsMaxPayloadSizeStr = System.getenv("WS_NETTY_MAX_PAYLOAD_SIZE");
        if (wsMaxPayloadSizeStr != null) {
            return Integer.parseInt(wsMaxPayloadSizeStr);
        } else {
            return wsMaxPayloadSize;
        }
    }

    private int getWssMaxPayloadSize() {
        String wssMaxPayloadSizeStr = System.getenv("WSS_NETTY_MAX_PAYLOAD_SIZE");
        if (wssMaxPayloadSizeStr != null) {
            return Integer.parseInt(wssMaxPayloadSizeStr);
        } else {
            return wssMaxPayloadSize;
        }
    }

    private boolean existsBasicCredentials() {
        return mqttClientCredentialsService.existsByCredentialsType(ClientCredentialsType.MQTT_BASIC);
    }

    private boolean existsX509Credentials() {
        return mqttClientCredentialsService.existsByCredentialsType(ClientCredentialsType.SSL);
    }
}
