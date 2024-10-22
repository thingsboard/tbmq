/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.install;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketConnectionService;
import org.thingsboard.mqtt.broker.dto.AdminDto;
import org.thingsboard.mqtt.broker.service.install.data.ConnectivitySettings;
import org.thingsboard.mqtt.broker.service.install.data.WebSocketClientSettings;
import org.thingsboard.mqtt.broker.service.user.AdminService;

import java.util.ArrayList;
import java.util.List;

@Service
@Profile("install")
@Slf4j
@RequiredArgsConstructor
public class DefaultSystemDataLoaderService implements SystemDataLoaderService {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final AdminService adminService;
    private final AdminSettingsService adminSettingsService;
    private final MqttClientCredentialsService mqttClientCredentialsService;
    private final WebSocketConnectionService webSocketConnectionService;
    private final UserService userService;

    private User sysadmin;
    private MqttClientCredentials systemWebSocketCredentials;

    @Bean
    protected BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    public void createAdmin() throws ThingsboardException {
        AdminDto adminDto = AdminDto.builder()
                .email("sysadmin@thingsboard.org")
                .password("sysadmin")
                .build();
        sysadmin = adminService.createAdmin(adminDto, false);
    }

    @Override
    public void createAdminSettings() {
        AdminSettings generalSettings = new AdminSettings();
        generalSettings.setKey("general");
        ObjectNode node = objectMapper.createObjectNode();
        node.put("baseUrl", "http://localhost:8083");
        node.put("prohibitDifferentUrl", false);
        generalSettings.setJsonValue(node);
        adminSettingsService.saveAdminSettings(generalSettings);

        AdminSettings mailSettings = new AdminSettings();
        mailSettings.setKey("mail");
        node = objectMapper.createObjectNode();
        node.put("mailFrom", "ThingsBoard <sysadmin@localhost.localdomain>");
        node.put("smtpProtocol", "smtp");
        node.put("smtpHost", "localhost");
        node.put("smtpPort", "25");
        node.put("timeout", "10000");
        node.put("enableTls", false);
        node.put("username", "");
        node.put("password", ""); //NOSONAR, key used to identify password field (not password value itself)
        node.put("tlsVersion", "TLSv1.2");
        node.put("enableProxy", false);
        node.put("showChangePassword", false);
        mailSettings.setJsonValue(node);
        adminSettingsService.saveAdminSettings(mailSettings);

        adminSettingsService.saveAdminSettings(ConnectivitySettings.createConnectivitySettings());
        adminSettingsService.saveAdminSettings(WebSocketClientSettings.createWsClientSettings());
    }

    @Override
    public void createWebSocketMqttClientCredentials() {
        systemWebSocketCredentials = mqttClientCredentialsService.saveSystemWebSocketCredentials();
    }

    @Override
    public void createDefaultWebSocketConnection() throws ThingsboardException {
        webSocketConnectionService.saveDefaultWebSocketConnection(sysadmin.getId(), systemWebSocketCredentials.getId());
    }

    @Override
    public void createDefaultWebSocketConnections() throws ThingsboardException {
        List<User> foundUsers = new ArrayList<>();

        PageLink pageLink = new PageLink(100);
        PageData<User> pageData;
        do {
            pageData = userService.findUsers(pageLink);
            foundUsers.addAll(pageData.getData());
            pageLink = pageLink.nextPageLink();
        } while (pageData.hasNext());

        for (User user : foundUsers) {
            webSocketConnectionService.saveDefaultWebSocketConnection(user.getId(), systemWebSocketCredentials.getId());
        }
    }

}
