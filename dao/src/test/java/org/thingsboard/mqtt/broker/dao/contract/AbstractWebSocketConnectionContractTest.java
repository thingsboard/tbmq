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
package org.thingsboard.mqtt.broker.dao.contract;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.dto.WebSocketConnectionDto;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.common.data.security.Authority;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.ws.LastWillMsg;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnectionConfiguration;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscription;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.exception.EntityConstraintViolationException;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketConnectionDao;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketConnectionService;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketSubscriptionService;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractWebSocketConnectionContractTest extends AbstractContractTest {

    private final IdComparator<WebSocketConnectionDto> idComparator = new IdComparator<>();

    @Autowired
    protected WebSocketConnectionService webSocketConnectionService;
    @Autowired
    protected WebSocketConnectionDao webSocketConnectionDao;
    @Autowired
    protected WebSocketSubscriptionService webSocketSubscriptionService;
    @Autowired
    protected MqttClientCredentialsService mqttClientCredentialsService;
    @Autowired
    protected UserService userService;

    User savedUser;

    @Before
    public void setUp() throws Exception {
        savedUser = saveUser("test@.gmail.com");
    }

    private User saveUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setAuthority(Authority.SYS_ADMIN);
        return userService.saveUser(user);
    }

    @After
    public void tearDown() throws Exception {
        PageData<WebSocketConnectionDto> connections = webSocketConnectionService.getWebSocketConnections(savedUser.getId(), new PageLink(1000));
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

    @Test
    public void givenTwoConnectionsWithOneNameForOneUser_whenSaveSecond_thenAlreadyRegistered() {
        webSocketConnectionService.saveWebSocketConnection(getWebSocketConnection("Same", savedUser.getId()));

        DataValidationException e = Assert.assertThrows(DataValidationException.class,
                () -> webSocketConnectionService.saveWebSocketConnection(getWebSocketConnection("Same", savedUser.getId())));
        Assert.assertEquals("Specified WebSocket connection is already registered!", e.getMessage());
    }

    @Test
    public void givenWebSocketConnectionWithSameNameDifferentUsers_whenExecuteSaveTwoTimes_thenSuccess() {
        final String wsConnectionName = "Test WS Connection";

        WebSocketConnection webSocketConnection = getWebSocketConnection(wsConnectionName, savedUser.getId());
        webSocketConnectionService.saveWebSocketConnection(webSocketConnection);

        User anotherUser = saveUser("another@.gmail.com");
        WebSocketConnection anotherWebSocketConnection = getWebSocketConnection(wsConnectionName, anotherUser.getId());
        webSocketConnectionService.saveWebSocketConnection(anotherWebSocketConnection);
        userService.deleteUser(anotherUser.getId());
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
    public void givenWebSocketConnectionWithWrongConfigurationUrlScheme_whenExecuteSave_thenFailure() {
        WebSocketConnection connection = new WebSocketConnection();
        connection.setName("test");
        connection.setUserId(savedUser.getId());

        WebSocketConnectionConfiguration config = new WebSocketConnectionConfiguration();
        config.setClientId("clientId");
        config.setUrl("wrong://localhost:8084/mqtt");
        connection.setConfiguration(config);

        webSocketConnectionService.saveWebSocketConnection(connection);
    }

    @Test(expected = DataValidationException.class)
    public void givenWebSocketConnectionWithWrongConfigurationUrlNoHost_whenExecuteSave_thenFailure() {
        WebSocketConnection connection = new WebSocketConnection();
        connection.setName("test");
        connection.setUserId(savedUser.getId());

        WebSocketConnectionConfiguration config = new WebSocketConnectionConfiguration();
        config.setClientId("clientId");
        config.setUrl("ws://:8084/mqtt");
        connection.setConfiguration(config);

        webSocketConnectionService.saveWebSocketConnection(connection);
    }

    @Test(expected = DataValidationException.class)
    public void givenWebSocketConnectionWithWrongConfigurationUrlWrongPort_whenExecuteSave_thenFailure() {
        WebSocketConnection connection = new WebSocketConnection();
        connection.setName("test");
        connection.setUserId(savedUser.getId());

        WebSocketConnectionConfiguration config = new WebSocketConnectionConfiguration();
        config.setClientId("clientId");
        config.setUrl("ws://localhost:656565/mqtt");
        connection.setConfiguration(config);

        webSocketConnectionService.saveWebSocketConnection(connection);
    }

    @Test(expected = DataValidationException.class)
    public void givenWebSocketConnectionWithWrongConfigurationUrlNoPath_whenExecuteSave_thenFailure() {
        WebSocketConnection connection = new WebSocketConnection();
        connection.setName("test");
        connection.setUserId(savedUser.getId());

        WebSocketConnectionConfiguration config = new WebSocketConnectionConfiguration();
        config.setClientId("clientId");
        config.setUrl("ws://localhost:8084");
        connection.setConfiguration(config);

        webSocketConnectionService.saveWebSocketConnection(connection);
    }

    @Test(expected = DataValidationException.class)
    public void givenWebSocketConnectionWithWrongConfigurationNoTopicInLastWillMsg_whenExecuteSave_thenFailure() {
        WebSocketConnection connection = new WebSocketConnection();
        connection.setName("test");
        connection.setUserId(savedUser.getId());

        WebSocketConnectionConfiguration config = new WebSocketConnectionConfiguration();
        config.setClientId("clientId");
        config.setUrl("ws://localhost:8084/mqtt");

        LastWillMsg lastWillMsg = new LastWillMsg();
        lastWillMsg.setTopic("");
        config.setLastWillMsg(lastWillMsg);

        connection.setConfiguration(config);

        webSocketConnectionService.saveWebSocketConnection(connection);
    }

    @Test(expected = DataValidationException.class)
    public void givenWebSocketConnectionWithWrongConfigurationInvalidTopicInLastWillMsg_whenExecuteSave_thenFailure() {
        WebSocketConnection connection = new WebSocketConnection();
        connection.setName("test");
        connection.setUserId(savedUser.getId());

        WebSocketConnectionConfiguration config = new WebSocketConnectionConfiguration();
        config.setClientId("clientId");
        config.setUrl("ws://localhost:8084/mqtt");

        LastWillMsg lastWillMsg = new LastWillMsg();
        lastWillMsg.setTopic("test/topic/#");
        config.setLastWillMsg(lastWillMsg);

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
            pageData = webSocketConnectionService.getWebSocketConnections(savedUser.getId(), pageLink);
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
        pageData = webSocketConnectionService.getWebSocketConnections(savedUser.getId(), pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertTrue(pageData.getData().isEmpty());
    }

    @Test
    public void givenWebSocketConnections_whenGetWebSocketConnectionsWithTextSearchAndSortOrder_thenSuccess() {
        List<WebSocketConnectionDto> webSocketConnectionDtoList = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            WebSocketConnection savedWebSocketConnection = webSocketConnectionService.saveWebSocketConnection(
                    getWebSocketConnection("Test" + i)
            );
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            webSocketConnectionDtoList.add(WebSocketConnectionDto.fromWebSocketConnection(savedWebSocketConnection));
        }

        PageLink pageLink = new PageLink(100, 0, "wrong");
        PageData<WebSocketConnectionDto> pageData = webSocketConnectionService.getWebSocketConnections(savedUser.getId(), pageLink);
        Assert.assertTrue(pageData.getData().isEmpty());

        pageLink = new PageLink(100, 1);
        pageData = webSocketConnectionService.getWebSocketConnections(savedUser.getId(), pageLink);
        Assert.assertTrue(pageData.getData().isEmpty());

        pageLink = new PageLink(100, 0, "test");
        pageData = webSocketConnectionService.getWebSocketConnections(savedUser.getId(), pageLink);
        Assert.assertEquals(32, pageData.getData().size());

        pageLink = new PageLink(100, 0, null, new SortOrder("createdTime", SortOrder.Direction.ASC));
        pageData = webSocketConnectionService.getWebSocketConnections(savedUser.getId(), pageLink);
        Assert.assertEquals("Test0", pageData.getData().get(0).getName());

        pageLink = new PageLink(100, 0, null, new SortOrder("createdTime", SortOrder.Direction.DESC));
        pageData = webSocketConnectionService.getWebSocketConnections(savedUser.getId(), pageLink);
        Assert.assertEquals("Test31", pageData.getData().get(0).getName());

        pageLink = new PageLink(100, 0, null, new SortOrder("name", SortOrder.Direction.DESC));
        pageData = webSocketConnectionService.getWebSocketConnections(savedUser.getId(), pageLink);
        Assert.assertEquals("Test9", pageData.getData().get(0).getName());

        webSocketConnectionDtoList.forEach(wsc ->
                webSocketConnectionService.deleteWebSocketConnection(wsc.getId()));

        pageLink = new PageLink(33);
        pageData = webSocketConnectionService.getWebSocketConnections(savedUser.getId(), pageLink);
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

        WebSocketConnection webSocketConnectionByName = webSocketConnectionService.findWebSocketConnectionByName(savedUser.getId(), savedWebSocketConnection.getName());

        Assert.assertEquals(savedWebSocketConnection, webSocketConnectionByName);
    }

    @Test
    public void givenNoWebSocketConnection_whenFindWebSocketConnectionByName_thenNothingFound() {
        WebSocketConnection webSocketConnectionByName = webSocketConnectionService.findWebSocketConnectionByName(savedUser.getId(), "absent");
        Assert.assertNull(webSocketConnectionByName);
    }

    @Test
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

    @Test
    public void connectionsArePagedAndSearchedPerUser() {
        for (String name : List.of("Alpha-1", "Alpha-2", "Beta")) {
            webSocketConnectionService.saveWebSocketConnection(getWebSocketConnection(name));
        }
        User anotherUser = saveUser("another-paged@.gmail.com");
        try {
            webSocketConnectionService.saveWebSocketConnection(getWebSocketConnection("Alpha-other", anotherUser.getId()));

            PageData<WebSocketConnectionDto> own = webSocketConnectionService.getWebSocketConnections(savedUser.getId(), new PageLink(10));
            PageData<WebSocketConnectionDto> searched = webSocketConnectionService.getWebSocketConnections(savedUser.getId(), new PageLink(10, 0, "ALPHA"));
            PageData<WebSocketConnectionDto> firstPage = webSocketConnectionService.getWebSocketConnections(savedUser.getId(),
                    new PageLink(1, 0, "alpha", new SortOrder("name", SortOrder.Direction.ASC)));
            PageData<WebSocketConnectionDto> secondPage = webSocketConnectionService.getWebSocketConnections(savedUser.getId(),
                    new PageLink(1, 1, "alpha", new SortOrder("name", SortOrder.Direction.ASC)));
            PageData<WebSocketConnectionDto> others = webSocketConnectionService.getWebSocketConnections(anotherUser.getId(), new PageLink(10));

            assertThat(own.getData()).extracting(WebSocketConnectionDto::getName).containsExactlyInAnyOrder("Alpha-1", "Alpha-2", "Beta");
            assertThat(own.getTotalElements()).isEqualTo(3);
            assertThat(searched.getData()).extracting(WebSocketConnectionDto::getName).containsExactlyInAnyOrder("Alpha-1", "Alpha-2");
            assertThat(searched.getTotalElements()).isEqualTo(2);
            assertThat(firstPage.getData()).extracting(WebSocketConnectionDto::getName).containsExactly("Alpha-1");
            assertThat(firstPage.getTotalPages()).isEqualTo(2);
            assertThat(firstPage.hasNext()).isTrue();
            assertThat(secondPage.getData()).extracting(WebSocketConnectionDto::getName).containsExactly("Alpha-2");
            assertThat(secondPage.hasNext()).isFalse();
            assertThat(others.getData()).extracting(WebSocketConnectionDto::getName).containsExactly("Alpha-other");
        } finally {
            userService.deleteUser(anotherUser.getId());
        }
    }

    @Test
    public void aConnectionIsFoundByNameForItsOwnUserOnly() {
        WebSocketConnection own = webSocketConnectionService.saveWebSocketConnection(getWebSocketConnection("Shared name"));
        User anotherUser = saveUser("another-by-name@.gmail.com");
        try {
            WebSocketConnection others = webSocketConnectionService.saveWebSocketConnection(getWebSocketConnection("Shared name", anotherUser.getId()));

            assertThat(webSocketConnectionService.findWebSocketConnectionByName(savedUser.getId(), "Shared name")).isEqualTo(own);
            assertThat(webSocketConnectionService.findWebSocketConnectionByName(anotherUser.getId(), "Shared name")).isEqualTo(others);
            assertThat(webSocketConnectionService.findWebSocketConnectionByName(anotherUser.getId(), "Test")).isNull();
        } finally {
            userService.deleteUser(anotherUser.getId());
        }
    }

    @Test
    public void theDefaultConnectionComesWithItsDefaultSubscription() throws Exception {
        MqttClientCredentials systemCredentials = mqttClientCredentialsService.findSystemWebSocketCredentials();
        boolean createdHere = systemCredentials == null;
        if (createdHere) {
            systemCredentials = mqttClientCredentialsService.saveSystemWebSocketCredentials();
        }
        try {
            WebSocketConnection saved = webSocketConnectionService.saveDefaultWebSocketConnection(savedUser.getId(), null);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getName()).isEqualTo(BrokerConstants.WEB_SOCKET_DEFAULT_CONNECTION_NAME);
            assertThat(saved.getUserId()).isEqualTo(savedUser.getId());
            assertThat(saved.getConfiguration().getClientCredentialsId()).isEqualTo(systemCredentials.getId());
            assertThat(webSocketConnectionService.findWebSocketConnectionByName(savedUser.getId(), BrokerConstants.WEB_SOCKET_DEFAULT_CONNECTION_NAME))
                    .isEqualTo(saved);
            PageData<WebSocketSubscription> subscriptions = webSocketSubscriptionService.getWebSocketSubscriptions(saved.getId(), new PageLink(10));
            assertThat(subscriptions.getData()).hasSize(1);
            assertThat(subscriptions.getData().get(0).getConfiguration().getTopicFilter())
                    .isEqualTo(BrokerConstants.WEB_SOCKET_DEFAULT_SUBSCRIPTION_TOPIC_FILTER);
            assertThat(subscriptions.getData().get(0).getConfiguration().getQos()).isEqualTo(BrokerConstants.WEB_SOCKET_DEFAULT_SUBSCRIPTION_QOS);
            assertThat(subscriptions.getData().get(0).getConfiguration().getColor()).isEqualTo(BrokerConstants.WEB_SOCKET_DEFAULT_SUBSCRIPTION_COLOR);
        } finally {
            if (createdHere) {
                mqttClientCredentialsService.deleteCredentials(systemCredentials.getId());
            }
        }
    }

    @Test
    public void removingAMissingIdAnswersTrue() {
        assertThat(webSocketConnectionDao.removeById(UUID.randomUUID())).isTrue();
    }

    // the service refuses an unknown user first: only a caller of the DAO reaches the foreign key
    @Test
    public void aConnectionOfAMissingUserIsRefusedByTheForeignKey() {
        String name = "orphan-" + UUID.randomUUID();
        WebSocketConnection orphan = getWebSocketConnection(name, UUID.randomUUID());

        assertThatThrownBy(() -> webSocketConnectionDao.save(orphan))
                .satisfies(e -> assertThat(EntityConstraintViolationException.isViolationOf(e, "fk_user_id")).isTrue());
        assertThat(webSocketConnectionDao.find()).extracting(WebSocketConnection::getName).doesNotContain(name);
    }

    private WebSocketConnection getWebSocketConnection() {
        return getWebSocketConnection("Test");
    }

    private WebSocketConnection getWebSocketConnection(String name) {
        return getWebSocketConnection(name, savedUser.getId());
    }

    private WebSocketConnection getWebSocketConnection(String name, UUID userId) {
        WebSocketConnection connection = new WebSocketConnection();
        connection.setName(name);
        connection.setConfiguration(getWebSocketConnectionConfiguration());
        connection.setUserId(userId);
        return connection;
    }

    private WebSocketConnectionConfiguration getWebSocketConnectionConfiguration() {
        WebSocketConnectionConfiguration config = new WebSocketConnectionConfiguration();
        config.setClientId("testClientId");
        config.setUrl("ws://localhost:8084/mqtt");
        return config;
    }

}
