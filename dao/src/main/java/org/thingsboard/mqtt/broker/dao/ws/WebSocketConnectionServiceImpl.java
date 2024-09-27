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
package org.thingsboard.mqtt.broker.dao.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.data.dto.WebSocketConnectionDto;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.ws.SizeUnit;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnectionConfiguration;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.dao.service.DataValidator;
import org.thingsboard.mqtt.broker.dao.topic.TopicValidationService;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.dao.util.exception.DbExceptionUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.thingsboard.mqtt.broker.dao.service.Validator.validatePageLink;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebSocketConnectionServiceImpl implements WebSocketConnectionService {

    private final WebSocketConnectionDao webSocketConnectionDao;
    private final WebSocketSubscriptionService webSocketSubscriptionService;
    private final UserService userService;
    private final MqttClientCredentialsService mqttClientCredentialsService;
    private final TopicValidationService topicValidationService;

    @Override
    public WebSocketConnection saveWebSocketConnection(WebSocketConnection connection) {
        if (log.isTraceEnabled()) {
            log.trace("Executing saveWebSocketConnection [{}]", connection);
        }
        webSocketConnectionValidator.validate(connection);
        try {
            return webSocketConnectionDao.save(connection);
        } catch (Exception t) {
            ConstraintViolationException e = DbExceptionUtil.extractConstraintViolationException(t).orElse(null);
            if (e != null && e.getConstraintName() != null
                    && e.getConstraintName().equalsIgnoreCase("name_unq_key")) {
                throw new DataValidationException("Specified WebSocket connection is already registered!");
            } else {
                throw t;
            }
        }
    }

    @Override
    @Transactional
    public WebSocketConnection saveDefaultWebSocketConnection(UUID userId, UUID clientCredentialsId) throws ThingsboardException {
        if (log.isTraceEnabled()) {
            log.trace("Executing saveDefaultWebSocketConnection [{}][{}]", userId, clientCredentialsId);
        }
        if (clientCredentialsId == null) {
            MqttClientCredentials systemWebSocketCredentials = mqttClientCredentialsService.findSystemWebSocketCredentials();
            if (systemWebSocketCredentials == null) {
                throw new ThingsboardException("Failed to find system WebSocket MQTT client credentials", ThingsboardErrorCode.ITEM_NOT_FOUND);
            }
            clientCredentialsId = systemWebSocketCredentials.getId();
        }

        WebSocketConnection connection = new WebSocketConnection();
        connection.setName(BrokerConstants.WEB_SOCKET_DEFAULT_CONNECTION_NAME);
        connection.setUserId(userId);
        connection.setConfiguration(getWebSocketConnectionConfiguration(clientCredentialsId));
        WebSocketConnection savedWebSocketConnection = saveWebSocketConnection(connection);

        webSocketSubscriptionService.saveDefaultWebSocketSubscription(savedWebSocketConnection.getId());

        return savedWebSocketConnection;
    }

    @Override
    public PageData<WebSocketConnectionDto> getWebSocketConnections(UUID userId, PageLink pageLink) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Executing getWebSocketConnections, pageLink [{}]", userId, pageLink);
        }
        validatePageLink(pageLink);
        return toShortWebSocketConnectionPageData(webSocketConnectionDao.findAll(userId, pageLink));
    }

    private PageData<WebSocketConnectionDto> toShortWebSocketConnectionPageData(PageData<WebSocketConnection> pageData) {
        List<WebSocketConnectionDto> data = pageData.getData().stream()
                .map(this::newWebSocketConnectionDto)
                .collect(Collectors.toList());
        return new PageData<>(data, pageData.getTotalPages(), pageData.getTotalElements(), pageData.hasNext());
    }

    @Override
    public Optional<WebSocketConnection> getWebSocketConnectionById(UUID id) {
        if (log.isTraceEnabled()) {
            log.trace("Executing getWebSocketConnectionById [{}]", id);
        }
        return Optional.ofNullable(webSocketConnectionDao.findById(id));
    }

    @Override
    public WebSocketConnection findWebSocketConnectionByName(UUID userId, String name) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Executing findWebSocketConnectionByName [{}]", userId, name);
        }
        return webSocketConnectionDao.findByUserIdAndName(userId, name);
    }

    @Override
    public boolean deleteWebSocketConnection(UUID id) {
        if (log.isTraceEnabled()) {
            log.trace("Executing deleteWebSocketConnection [{}]", id);
        }
        return webSocketConnectionDao.removeById(id);
    }

    private final DataValidator<WebSocketConnection> webSocketConnectionValidator =
            new DataValidator<>() {

                @Override
                protected void validateUpdate(WebSocketConnection webSocketConnection) {
                    WebSocketConnection existingConnection = webSocketConnectionDao.findById(webSocketConnection.getId());
                    if (existingConnection == null) {
                        throw new DataValidationException("Unable to update non-existent WebSocket Connection!");
                    }
                }

                @Override
                protected void validateDataImpl(WebSocketConnection webSocketConnection) {
                    validateString("WebSocket Connection name", webSocketConnection.getName());
                    if (webSocketConnection.getUserId() == null) {
                        throw new DataValidationException("WebSocket Connection should be assigned to user!");
                    } else {
                        if (userService.findUserById(webSocketConnection.getUserId()) == null) {
                            throw new DataValidationException("WebSocket Connection is referencing to non-existent user!");
                        }
                    }
                    if (webSocketConnection.getConfiguration() == null) {
                        throw new DataValidationException("WebSocket Connection configuration should be specified!");
                    }
                    validateString("WebSocket Connection clientId", webSocketConnection.getConfiguration().getClientId());
                    validateString("WebSocket Connection URL", webSocketConnection.getConfiguration().getUrl());
                    validateURL(webSocketConnection.getConfiguration().getUrl());
                    if (webSocketConnection.getConfiguration().getLastWillMsg() != null) {
                        validateString("WebSocket Connection Last Will Msg topic", webSocketConnection.getConfiguration().getLastWillMsg().getTopic());
                        topicValidationService.validateTopic(webSocketConnection.getConfiguration().getLastWillMsg().getTopic());
                    }
                }
            };

    private WebSocketConnectionDto newWebSocketConnectionDto(WebSocketConnection webSocketConnection) {
        return WebSocketConnectionDto.fromWebSocketConnection(webSocketConnection);
    }

    private WebSocketConnectionConfiguration getWebSocketConnectionConfiguration(UUID clientCredentialsId) {
        WebSocketConnectionConfiguration configuration = new WebSocketConnectionConfiguration();
        configuration.setUrl("ws://localhost:8084/mqtt");
        configuration.setClientCredentialsId(clientCredentialsId);
        configuration.setClientId("tbmq_" + RandomStringUtils.randomAlphanumeric(8));
        configuration.setUsername(BrokerConstants.WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_USERNAME);
        configuration.setCleanStart(true);
        configuration.setKeepAlive(2);
        configuration.setKeepAliveUnit(TimeUnit.MINUTES);
        configuration.setConnectTimeout(10);
        configuration.setConnectTimeoutUnit(TimeUnit.SECONDS);
        configuration.setReconnectPeriod(5);
        configuration.setReconnectPeriodUnit(TimeUnit.SECONDS);
        configuration.setMqttVersion(5);
        configuration.setSessionExpiryInterval(0);
        configuration.setSessionExpiryIntervalUnit(TimeUnit.SECONDS);
        configuration.setMaxPacketSize(64);
        configuration.setMaxPacketSizeUnit(SizeUnit.KILOBYTE);
        configuration.setTopicAliasMax(0);
        configuration.setReceiveMax(BrokerConstants.DEFAULT_RECEIVE_MAXIMUM);
        configuration.setRequestResponseInfo(false);
        configuration.setRequestProblemInfo(false);

        configuration.setLastWillMsg(null);
        configuration.setUserProperties(null);

        return configuration;
    }

    void validateURL(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getPath();

            if (!(BrokerConstants.WS.equalsIgnoreCase(scheme) || BrokerConstants.WSS.equalsIgnoreCase(scheme))) {
                throw new DataValidationException("WebSocket Connection URL has incorrect scheme!");
            }
            if (StringUtils.isEmpty(host)) {
                throw new DataValidationException("WebSocket Connection URL does not contain host!");
            }
            if ((port < -1) || (port > 65535)) {
                throw new DataValidationException("WebSocket Connection URL has incorrect port!");
            }
            if (StringUtils.isEmpty(path)) {
                throw new DataValidationException("WebSocket Connection URL does not contain path! In most cases it should be '/mqtt'");
            }
        } catch (URISyntaxException e) {
            throw new DataValidationException("WebSocket Connection URL is not formatted strictly according to RFC2396!");
        }
    }

}
