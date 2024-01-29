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

import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.dto.WebSocketConnectionDto;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.Authority;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnectionConfiguration;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.dao.service.AbstractServiceTest;
import org.thingsboard.mqtt.broker.dao.user.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@DaoSqlTest
public class WebSocketConnectionServiceImplTest extends AbstractServiceTest {

    private final IdComparator<WebSocketConnectionDto> idComparator = new IdComparator<>();

    @Autowired
    private WebSocketConnectionService webSocketConnectionService;
    @Autowired
    private UserService userService;

    User savedUser;

    @Before
    public void setUp() throws Exception {
        User user = new User();
        user.setEmail("test@.gmail.com");
        user.setAuthority(Authority.SYS_ADMIN);
        savedUser = userService.saveUser(user);
    }

    @After
    public void tearDown() throws Exception {
        PageData<WebSocketConnectionDto> connections = webSocketConnectionService.getWebSocketConnections(new PageLink(1000));
        for (var connection : connections.getData()) {
            webSocketConnectionService.deleteWebSocketConnection(connection.getId());
        }
        userService.deleteUser(savedUser.getId());
    }

    @Test
    public void givenWebSocketConnection_whenExecuteSave_thenSuccess() {
        WebSocketConnection webSocketConnection = getWebSocketConnection();
        webSocketConnectionService.saveWebSocketConnection(webSocketConnection);
    }

    @Test(expected = DataValidationException.class)
    public void givenWebSocketConnection_whenExecuteSaveTwoTimes_thenGetConstraintViolationException() {
        WebSocketConnection webSocketConnection = getWebSocketConnection();
        webSocketConnectionService.saveWebSocketConnection(webSocketConnection);

        webSocketConnectionService.saveWebSocketConnection(webSocketConnection);
    }

    @Test(expected = DataValidationException.class)
    public void givenWebSocketConnectionWithNoName_whenExecuteSave_thenFailure() {
        WebSocketConnection connection = new WebSocketConnection();
        connection.setUserId(savedUser.getId());
        connection.setConfiguration(getWebSocketConnectionConfiguration());
        webSocketConnectionService.saveWebSocketConnection(connection);
    }

    @Test(expected = DataValidationException.class)
    public void givenWebSocketConnectionWithNoUserId_whenExecuteSave_thenFailure() {
        WebSocketConnection connection = new WebSocketConnection();
        connection.setName("test");
        connection.setConfiguration(getWebSocketConnectionConfiguration());
        webSocketConnectionService.saveWebSocketConnection(connection);
    }

    @Test(expected = DataValidationException.class)
    public void givenWebSocketConnectionWithWrongUserId_whenExecuteSave_thenFailure() {
        WebSocketConnection connection = new WebSocketConnection();
        connection.setName("test");
        connection.setUserId(UUID.randomUUID());
        connection.setConfiguration(getWebSocketConnectionConfiguration());
        webSocketConnectionService.saveWebSocketConnection(connection);
    }

    @Test(expected = DataValidationException.class)
    public void givenWebSocketConnectionWithNoConfiguration_whenExecuteSave_thenFailure() {
        WebSocketConnection connection = new WebSocketConnection();
        connection.setName("test");
        connection.setUserId(savedUser.getId());
        webSocketConnectionService.saveWebSocketConnection(connection);
    }

    @Test(expected = DataValidationException.class)
    public void givenWebSocketConnectionWithWrongConfigurationWithoutClientId_whenExecuteSave_thenFailure() {
        WebSocketConnection connection = new WebSocketConnection();
        connection.setName("test");
        connection.setUserId(savedUser.getId());

        WebSocketConnectionConfiguration config = new WebSocketConnectionConfiguration();
        config.setUrl("url");
        connection.setConfiguration(config);

        webSocketConnectionService.saveWebSocketConnection(connection);
    }

    @Test(expected = DataValidationException.class)
    public void givenWebSocketConnectionWithWrongConfigurationWithoutUrl_whenExecuteSave_thenFailure() {
        WebSocketConnection connection = new WebSocketConnection();
        connection.setName("test");
        connection.setUserId(savedUser.getId());

        WebSocketConnectionConfiguration config = new WebSocketConnectionConfiguration();
        config.setClientId("clientId");
        connection.setConfiguration(config);

        webSocketConnectionService.saveWebSocketConnection(connection);
    }

    @Test(expected = DataValidationException.class)
    public void givenWebSocketConnectionWithId_whenSaveWithAbsentInDbId_thenFailure() {
        WebSocketConnection webSocketConnection = getWebSocketConnection();
        webSocketConnection.setId(UUID.randomUUID());
        webSocketConnectionService.saveWebSocketConnection(webSocketConnection);
    }

    @Test
    public void givenWebSocketConnections_whenGetWebSocketConnections_thenSuccess() {
        List<WebSocketConnectionDto> webSocketConnectionDtoList = new ArrayList<>();
        for (int i = 0; i < 152; i++) {
            WebSocketConnection savedWebSocketConnection = webSocketConnectionService.saveWebSocketConnection(
                    getWebSocketConnection("Test" + i)
            );
            webSocketConnectionDtoList.add(WebSocketConnectionDto.fromWebSocketConnection(savedWebSocketConnection));
        }

        List<WebSocketConnectionDto> loadedWebSocketConnectionDtoList = new ArrayList<>();
        PageLink pageLink = new PageLink(23);
        PageData<WebSocketConnectionDto> pageData;
        do {
            pageData = webSocketConnectionService.getWebSocketConnections(pageLink);
            loadedWebSocketConnectionDtoList.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        webSocketConnectionDtoList.sort(idComparator);
        loadedWebSocketConnectionDtoList.sort(idComparator);

        Assert.assertEquals(webSocketConnectionDtoList, loadedWebSocketConnectionDtoList);

        loadedWebSocketConnectionDtoList.forEach(wsc ->
                webSocketConnectionService.deleteWebSocketConnection(wsc.getId()));

        pageLink = new PageLink(33);
        pageData = webSocketConnectionService.getWebSocketConnections(pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertTrue(pageData.getData().isEmpty());
    }

    @Test
    public void givenWebSocketConnection_whenGetWebSocketConnectionById_thenSuccess() {
        WebSocketConnection webSocketConnection = getWebSocketConnection();
        WebSocketConnection savedWebSocketConnection = webSocketConnectionService.saveWebSocketConnection(webSocketConnection);

        Optional<WebSocketConnection> webSocketConnectionById = webSocketConnectionService.getWebSocketConnectionById(savedWebSocketConnection.getId());

        Assert.assertTrue(webSocketConnectionById.isPresent());
        Assert.assertEquals(savedWebSocketConnection, webSocketConnectionById.get());
    }

    @Test
    public void givenNoWebSocketConnection_whenGetWebSocketConnectionById_thenNothingFound() {
        Optional<WebSocketConnection> webSocketConnectionById = webSocketConnectionService.getWebSocketConnectionById(UUID.randomUUID());
        Assert.assertFalse(webSocketConnectionById.isPresent());
    }

    @Test
    public void givenWebSocketConnection_whenFindWebSocketConnectionByName_thenSuccess() {
        WebSocketConnection webSocketConnection = getWebSocketConnection();
        WebSocketConnection savedWebSocketConnection = webSocketConnectionService.saveWebSocketConnection(webSocketConnection);

        WebSocketConnection webSocketConnectionByName = webSocketConnectionService.findWebSocketConnectionByName(savedWebSocketConnection.getName());

        Assert.assertEquals(savedWebSocketConnection, webSocketConnectionByName);
    }

    @Test
    public void givenNoWebSocketConnection_whenFindWebSocketConnectionByName_thenNothingFound() {
        WebSocketConnection webSocketConnectionByName = webSocketConnectionService.findWebSocketConnectionByName("absent");
        Assert.assertNull(webSocketConnectionByName);
    }

    @Test(expected = EmptyResultDataAccessException.class)
    public void givenNoWebSocketConnection_whenDelete_thenNothingRemoved() {
        webSocketConnectionService.deleteWebSocketConnection(UUID.randomUUID());
    }

    @Test
    public void givenWebSocketConnection_whenDelete_thenSuccess() {
        WebSocketConnection webSocketConnection = getWebSocketConnection();
        WebSocketConnection savedWebSocketConnection = webSocketConnectionService.saveWebSocketConnection(webSocketConnection);

        boolean result = webSocketConnectionService.deleteWebSocketConnection(savedWebSocketConnection.getId());
        Assert.assertTrue(result);

        Optional<WebSocketConnection> webSocketConnectionById = webSocketConnectionService.getWebSocketConnectionById(savedWebSocketConnection.getId());
        Assert.assertFalse(webSocketConnectionById.isPresent());
    }

    @NotNull
    private WebSocketConnection getWebSocketConnection() {
        return getWebSocketConnection("Test");
    }

    @NotNull
    private WebSocketConnection getWebSocketConnection(String name) {
        WebSocketConnection connection = new WebSocketConnection();
        connection.setName(name);
        connection.setConfiguration(getWebSocketConnectionConfiguration());
        connection.setUserId(savedUser.getId());
        return connection;
    }

    @NotNull
    private WebSocketConnectionConfiguration getWebSocketConnectionConfiguration() {
        WebSocketConnectionConfiguration config = new WebSocketConnectionConfiguration();
        config.setClientId("testClientId");
        config.setUrl("url");
        return config;
    }

}
