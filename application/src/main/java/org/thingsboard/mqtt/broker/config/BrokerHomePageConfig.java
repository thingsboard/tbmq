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

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.SysAdminSettingType;
import org.thingsboard.mqtt.broker.common.data.dto.ShortMqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.client.connectivity.ConnectivityInfo;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.dto.HomePageConfigDto;
import org.thingsboard.mqtt.broker.dto.MqttListenerName;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    private final MqttClientCredentialsService mqttClientCredentialsService;
    private final AdminSettingsService adminSettingsService;
    private final MqttAuthProviderService mqttAuthProviderService;

    public HomePageConfigDto getConfig() {
        Map<String, ConnectivityInfo> connectivityInfoMap = getConnectivityInfoMap();
        List<MqttAuthProviderType> enabledTypes = getEnabledProviderTypes();
        return HomePageConfigDto.builder()
                .basicAuthEnabled(enabledTypes.contains(MqttAuthProviderType.MQTT_BASIC))
                .x509AuthEnabled(enabledTypes.contains(MqttAuthProviderType.X_509))
                .scramAuthEnabled(enabledTypes.contains(MqttAuthProviderType.SCRAM))
                .jwtAuthEnabled(enabledTypes.contains(MqttAuthProviderType.JWT))
                .tcpPort(getTcpPort(connectivityInfoMap))
                .tlsPort(getTlsPort(connectivityInfoMap))
                .wsPort(getWsPort(connectivityInfoMap))
                .wssPort(getWssPort(connectivityInfoMap))
                .tcpListenerEnabled(tcpListenerEnabled)
                .tlsListenerEnabled(tlsListenerEnabled)
                .wsListenerEnabled(wsListenerEnabled)
                .wssListenerEnabled(wssListenerEnabled)
                .tcpMaxPayloadSize(tcpMaxPayloadSize)
                .tlsMaxPayloadSize(tlsMaxPayloadSize)
                .wsMaxPayloadSize(wsMaxPayloadSize)
                .wssMaxPayloadSize(wssMaxPayloadSize)
                .existsBasicCredentials(existsBasicCredentials())
                .existsX509Credentials(existsX509Credentials())
                .existsScramCredentials(existsScramCredentials())
                .build();
    }

    public int getListenerPort(MqttListenerName mqttListenerName) {
        return switch (mqttListenerName) {
            case MQTT -> tcpPort;
            case MQTTS -> tlsPort;
            case WS -> wsPort;
            case WSS -> wssPort;
        };
    }

    private Map<String, ConnectivityInfo> getConnectivityInfoMap() {
        AdminSettings connectivityAdminSettings = adminSettingsService.findAdminSettingsByKey(SysAdminSettingType.CONNECTIVITY.getKey());
        return connectivityAdminSettings == null ? Collections.emptyMap() : JacksonUtil.convertValue(connectivityAdminSettings.getJsonValue(), new TypeReference<>() {
        });
    }

    private List<MqttAuthProviderType> getEnabledProviderTypes() {
        return mqttAuthProviderService.getShortAuthProviders(new PageLink(BrokerConstants.DEFAULT_PAGE_SIZE))
                .getData().stream()
                .filter(ShortMqttAuthProvider::isEnabled)
                .map(ShortMqttAuthProvider::getType)
                .toList();
    }

    private int getTcpPort(Map<String, ConnectivityInfo> connectivityInfoMap) {
        return getPort(connectivityInfoMap, BrokerConstants.MQTT_CONNECTIVITY, tcpPort);
    }

    private int getTlsPort(Map<String, ConnectivityInfo> connectivityInfoMap) {
        return getPort(connectivityInfoMap, BrokerConstants.MQTTS_CONNECTIVITY, tlsPort);
    }

    private int getWsPort(Map<String, ConnectivityInfo> connectivityInfoMap) {
        return getPort(connectivityInfoMap, BrokerConstants.WS_CONNECTIVITY, wsPort);
    }

    private int getWssPort(Map<String, ConnectivityInfo> connectivityInfoMap) {
        return getPort(connectivityInfoMap, BrokerConstants.WSS_CONNECTIVITY, wssPort);
    }

    private int getPort(Map<String, ConnectivityInfo> connectivityInfoMap, String key, int defaultPort) {
        int port = getPortFromConnectivitySettings(connectivityInfoMap, key);
        return port != -1 ? port : defaultPort;
    }

    private boolean existsBasicCredentials() {
        return existsByCredentialsType(ClientCredentialsType.MQTT_BASIC);
    }

    private boolean existsX509Credentials() {
        return existsByCredentialsType(ClientCredentialsType.X_509);
    }

    private boolean existsScramCredentials() {
        return existsByCredentialsType(ClientCredentialsType.SCRAM);
    }

    private boolean existsByCredentialsType(ClientCredentialsType type) {
        return mqttClientCredentialsService.existsByCredentialsType(type);
    }

    private int getPortFromConnectivitySettings(Map<String, ConnectivityInfo> connectivityInfoMap, String key) {
        ConnectivityInfo connectivityInfo = connectivityInfoMap.get(key);
        if (connectivityInfo != null && connectivityInfo.isEnabled() && StringUtils.isNotEmpty(connectivityInfo.getPort())) {
            return Integer.parseInt(connectivityInfo.getPort());
        }
        return -1;
    }
}
