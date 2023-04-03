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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.dto.HomePageConfigDto;

@Component
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

    public HomePageConfigDto getConfig() {
        return HomePageConfigDto.builder()
                .tcpPort(getTcpPort())
                .tlsPort(getTlsPort())
                .tcpListenerEnabled(isTcpListenerEnabled())
                .tlsListenerEnabled(isTlsListenerEnabled())
                .tcpMaxPayloadSize(getTcpMaxPayloadSize())
                .tlsMaxPayloadSize(getTlsMaxPayloadSize())
                .basicAuthEnabled(isBasicAuthEnabled())
                .x509AuthEnabled(isX509AuthEnabled())
                .build();
    }

    public int getTcpPort() {
        String tcpPortStr = System.getenv("LISTENER_TCP_BIND_PORT");
        if (tcpPortStr != null) {
            return Integer.parseInt(tcpPortStr);
        } else {
            return tcpPort;
        }
    }

    public int getTlsPort() {
        String tlsPortStr = System.getenv("LISTENER_SSL_BIND_PORT");
        if (tlsPortStr != null) {
            return Integer.parseInt(tlsPortStr);
        } else {
            return tlsPort;
        }
    }

    public boolean isTcpListenerEnabled() {
        String tcpListenerEnabledStr = System.getenv("LISTENER_TCP_ENABLED");
        if (tcpListenerEnabledStr != null) {
            return Boolean.parseBoolean(tcpListenerEnabledStr);
        } else {
            return tcpListenerEnabled;
        }
    }

    public boolean isTlsListenerEnabled() {
        String tlsListenerEnabledStr = System.getenv("LISTENER_SSL_ENABLED");
        if (tlsListenerEnabledStr != null) {
            return Boolean.parseBoolean(tlsListenerEnabledStr);
        } else {
            return tlsListenerEnabled;
        }
    }

    public int getTcpMaxPayloadSize() {
        String tcpMaxPayloadSizeStr = System.getenv("TCP_NETTY_MAX_PAYLOAD_SIZE");
        if (tcpMaxPayloadSizeStr != null) {
            return Integer.parseInt(tcpMaxPayloadSizeStr);
        } else {
            return tcpMaxPayloadSize;
        }
    }

    public int getTlsMaxPayloadSize() {
        String tlsMaxPayloadSizeStr = System.getenv("SSL_NETTY_MAX_PAYLOAD_SIZE");
        if (tlsMaxPayloadSizeStr != null) {
            return Integer.parseInt(tlsMaxPayloadSizeStr);
        } else {
            return tlsMaxPayloadSize;
        }
    }

    public boolean isBasicAuthEnabled() {
        String basicAuthEnabledStr = System.getenv("SECURITY_MQTT_BASIC_ENABLED");
        if (basicAuthEnabledStr != null) {
            return Boolean.parseBoolean(basicAuthEnabledStr);
        } else {
            return basicAuthEnabled;
        }
    }

    public boolean isX509AuthEnabled() {
        String x509AuthEnabledStr = System.getenv("SECURITY_MQTT_SSL_ENABLED");
        if (x509AuthEnabledStr != null) {
            return Boolean.parseBoolean(x509AuthEnabledStr);
        } else {
            return x509AuthEnabled;
        }
    }
}
