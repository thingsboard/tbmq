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
import org.thingsboard.mqtt.broker.common.data.security.Authority;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnectionConfiguration;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscription;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscriptionConfiguration;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.dao.service.AbstractServiceTest;
import org.thingsboard.mqtt.broker.dao.user.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@DaoSqlTest
public class WebSocketSubscriptionServiceImplTest extends AbstractServiceTest {

    private final IdComparator<WebSocketSubscription> idComparator = new IdComparator<>();

    @Autowired
    private WebSocketSubscriptionService webSocketSubscriptionService;
    @Autowired
    private WebSocketConnectionService webSocketConnectionService;
    @Autowired
    private UserService userService;

    User savedUser;
    WebSocketConnection savedWebSocketConnection;

    @Before
    public void setUp() throws Exception {
        savedUser = saveUser();
        savedWebSocketConnection = saveWebSocketConnection("Test WS Connection");
    }

    private User saveUser() {
        User user = new User();
        user.setEmail("test@.gmail.com");
        user.setAuthority(Authority.SYS_ADMIN);
        return userService.saveUser(user);
    }

    private WebSocketConnection saveWebSocketConnection(String name) {
        WebSocketConnection webSocketConnection = new WebSocketConnection();
        webSocketConnection.setName(name);
        webSocketConnection.setUserId(savedUser.getId());
        WebSocketConnectionConfiguration config = new WebSocketConnectionConfiguration();
        config.setClientId("clientId");
        config.setUrl("url");
        webSocketConnection.setConfiguration(config);
        return webSocketConnectionService.saveWebSocketConnection(webSocketConnection);
    }

    @After
    public void tearDown() throws Exception {
        List<WebSocketSubscription> subscriptions = webSocketSubscriptionService.getWebSocketSubscriptions(savedWebSocketConnection.getId());
        for (var subscription : subscriptions) {
            webSocketSubscriptionService.deleteWebSocketSubscription(subscription.getId());
        }

        webSocketConnectionService.deleteWebSocketConnection(savedWebSocketConnection.getId());
        userService.deleteUser(savedUser.getId());
    }

    @Test
    public void givenWebSocketSubscription_whenExecuteSave_thenSuccess() {
        WebSocketSubscription subscription = getWebSocketSubscription();
        webSocketSubscriptionService.saveWebSocketSubscription(subscription);
    }

    @Test(expected = DataValidationException.class)
    public void givenWebSocketSubscriptionWithNoWebSocketConnectionId_whenExecuteSave_thenFailure() {
        WebSocketSubscription subscription = new WebSocketSubscription();
        subscription.setConfiguration(getWebSocketSubscriptionConfiguration("#"));
        webSocketSubscriptionService.saveWebSocketSubscription(subscription);
    }

    @Test(expected = DataValidationException.class)
    public void givenWebSocketSubscriptionWithWrongWebSocketConnectionId_whenExecuteSave_thenFailure() {
        WebSocketSubscription subscription = new WebSocketSubscription();
        subscription.setConfiguration(getWebSocketSubscriptionConfiguration("#"));
        subscription.setWebSocketConnectionId(UUID.randomUUID());
        webSocketSubscriptionService.saveWebSocketSubscription(subscription);
    }

    @Test(expected = DataValidationException.class)
    public void givenWebSocketSubscriptionWithNoConfiguration_whenExecuteSave_thenFailure() {
        WebSocketSubscription subscription = new WebSocketSubscription();
        subscription.setWebSocketConnectionId(savedWebSocketConnection.getId());
        webSocketSubscriptionService.saveWebSocketSubscription(subscription);
    }

    @Test(expected = DataValidationException.class)
    public void givenWebSocketSubscriptionWithWrongConfigurationWithoutTopicFilter_whenExecuteSave_thenFailure() {
        WebSocketSubscription subscription = new WebSocketSubscription();
        subscription.setWebSocketConnectionId(savedWebSocketConnection.getId());
        subscription.setConfiguration(new WebSocketSubscriptionConfiguration());

        webSocketSubscriptionService.saveWebSocketSubscription(subscription);
    }

    @Test(expected = DataValidationException.class)
    public void givenWebSocketSubscriptionWithId_whenSaveWithAbsentInDbId_thenFailure() {
        WebSocketSubscription webSocketSubscription = getWebSocketSubscription();
        webSocketSubscription.setId(UUID.randomUUID());
        webSocketSubscriptionService.saveWebSocketSubscription(webSocketSubscription);
    }

    @Test
    public void givenWebSocketSubscriptions_whenGetWebSocketSubscriptions_thenSuccess() {
        List<WebSocketSubscription> webSocketSubscriptions = new ArrayList<>();
        for (int i = 0; i < 152; i++) {
            WebSocketSubscription savedWebSocketSubscription = webSocketSubscriptionService.saveWebSocketSubscription(
                    getWebSocketSubscription("test/topic/" + i)
            );
            webSocketSubscriptions.add(savedWebSocketSubscription);
        }

        List<WebSocketSubscription> loadedWebSocketSubscriptions = webSocketSubscriptionService.getWebSocketSubscriptions(savedWebSocketConnection.getId());

        webSocketSubscriptions.sort(idComparator);
        loadedWebSocketSubscriptions.sort(idComparator);

        Assert.assertEquals(webSocketSubscriptions, loadedWebSocketSubscriptions);

        loadedWebSocketSubscriptions.forEach(wss ->
                webSocketSubscriptionService.deleteWebSocketSubscription(wss.getId()));

        loadedWebSocketSubscriptions = webSocketSubscriptionService.getWebSocketSubscriptions(savedWebSocketConnection.getId());
        Assert.assertTrue(loadedWebSocketSubscriptions.isEmpty());
    }

    @Test
    public void givenWebSocketSubscription_whenGetWebSocketSubscriptionById_thenSuccess() {
        WebSocketSubscription webSocketSubscription = getWebSocketSubscription();
        WebSocketSubscription savedWebSocketSubscription = webSocketSubscriptionService.saveWebSocketSubscription(webSocketSubscription);

        Optional<WebSocketSubscription> webSocketSubscriptionById = webSocketSubscriptionService.getWebSocketSubscriptionById(savedWebSocketSubscription.getId());

        Assert.assertTrue(webSocketSubscriptionById.isPresent());
        Assert.assertEquals(savedWebSocketSubscription, webSocketSubscriptionById.get());
    }

    @Test
    public void givenNoWebSocketSubscription_whenGetWebSocketSubscriptionById_thenNothingFound() {
        Optional<WebSocketSubscription> webSocketSubscriptionById = webSocketSubscriptionService.getWebSocketSubscriptionById(UUID.randomUUID());
        Assert.assertFalse(webSocketSubscriptionById.isPresent());
    }

    @Test(expected = EmptyResultDataAccessException.class)
    public void givenNoWebSocketSubscription_whenDelete_thenNothingRemoved() {
        webSocketSubscriptionService.deleteWebSocketSubscription(UUID.randomUUID());
    }

    @Test
    public void givenWebSocketSubscription_whenDelete_thenSuccess() {
        WebSocketSubscription webSocketSubscription = getWebSocketSubscription();
        WebSocketSubscription savedWebSocketSubscription = webSocketSubscriptionService.saveWebSocketSubscription(webSocketSubscription);

        boolean result = webSocketSubscriptionService.deleteWebSocketSubscription(savedWebSocketSubscription.getId());
        Assert.assertTrue(result);

        Optional<WebSocketSubscription> webSocketSubscriptionById = webSocketSubscriptionService.getWebSocketSubscriptionById(savedWebSocketSubscription.getId());
        Assert.assertFalse(webSocketSubscriptionById.isPresent());
    }

    @Test
    public void givenWebSocketConnectionAndSubscription_whenDeleteWebSocketConnection_thenWebSocketSubscriptionIsAlsoRemoved() {
        WebSocketConnection savedWebSocketConnection = saveWebSocketConnection("Test WS Connection for removal");

        WebSocketSubscription webSocketSubscription = getWsSubscription("#", savedWebSocketConnection.getId());
        WebSocketSubscription savedWebSocketSubscription = webSocketSubscriptionService.saveWebSocketSubscription(webSocketSubscription);

        boolean result = webSocketConnectionService.deleteWebSocketConnection(savedWebSocketConnection.getId());
        Assert.assertTrue(result);

        Optional<WebSocketSubscription> webSocketSubscriptionById = webSocketSubscriptionService.getWebSocketSubscriptionById(savedWebSocketSubscription.getId());
        Assert.assertFalse(webSocketSubscriptionById.isPresent());
    }

    @NotNull
    private WebSocketSubscription getWebSocketSubscription(String topicFilter) {
        return getWsSubscription(topicFilter);
    }

    @NotNull
    private WebSocketSubscription getWebSocketSubscription() {
        return getWsSubscription("test/topic/+");
    }

    @NotNull
    private WebSocketSubscription getWsSubscription(String topicFilter) {
        return getWsSubscription(topicFilter, savedWebSocketConnection.getId());
    }

    @NotNull
    private WebSocketSubscription getWsSubscription(String topicFilter, UUID webSocketConnectionId) {
        WebSocketSubscription subscription = new WebSocketSubscription();
        subscription.setWebSocketConnectionId(webSocketConnectionId);
        subscription.setConfiguration(getWebSocketSubscriptionConfiguration(topicFilter));
        return subscription;
    }

    @NotNull
    private WebSocketSubscriptionConfiguration getWebSocketSubscriptionConfiguration(String topicFilter) {
        WebSocketSubscriptionConfiguration webSocketSubscriptionConfiguration = new WebSocketSubscriptionConfiguration();
        webSocketSubscriptionConfiguration.setTopicFilter(topicFilter);
        return webSocketSubscriptionConfiguration;
    }

}
