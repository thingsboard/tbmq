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
package org.thingsboard.mqtt.broker.dao.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.dto.WebSocketConnectionDto;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.dao.service.DataValidator;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.dao.util.exception.DbExceptionUtil;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.thingsboard.mqtt.broker.dao.service.Validator.validatePageLink;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebSocketConnectionServiceImpl implements WebSocketConnectionService {

    private final WebSocketConnectionDao webSocketConnectionDao;
    private final UserService userService;

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
    public PageData<WebSocketConnectionDto> getWebSocketConnections(PageLink pageLink) {
        if (log.isTraceEnabled()) {
            log.trace("Executing getWebSocketConnections, pageLink [{}]", pageLink);
        }
        validatePageLink(pageLink);
        return toShortWebSocketConnectionPageData(webSocketConnectionDao.findAll(pageLink));
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
    public WebSocketConnection findWebSocketConnectionByName(String name) {
        if (log.isTraceEnabled()) {
            log.trace("Executing findWebSocketConnectionByName [{}]", name);
        }
        return webSocketConnectionDao.findByName(name);
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
                }
            };

    private WebSocketConnectionDto newWebSocketConnectionDto(WebSocketConnection webSocketConnection) {
        return WebSocketConnectionDto.fromWebSocketConnection(webSocketConnection);
    }

}
