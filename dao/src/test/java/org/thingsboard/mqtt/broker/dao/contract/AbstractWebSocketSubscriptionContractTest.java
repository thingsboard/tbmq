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
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.Authority;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnectionConfiguration;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscription;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscriptionConfiguration;
import org.thingsboard.mqtt.broker.dao.exception.EntityConstraintViolationException;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketSubscriptionDao;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketConnectionService;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketSubscriptionService;
import org.thingsboard.mqtt.broker.exception.DataValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractWebSocketSubscriptionContractTest extends AbstractContractTest {

    private final IdComparator<WebSocketSubscription> idComparator = new IdComparator<>();

    @Autowired
    protected WebSocketSubscriptionService webSocketSubscriptionService;
    @Autowired
    protected WebSocketSubscriptionDao webSocketSubscriptionDao;
    @Autowired
    protected WebSocketConnectionService webSocketConnectionService;
    @Autowired
    protected UserService userService;

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
        config.setUrl("ws://localhost:8084/mqtt");
        webSocketConnection.setConfiguration(config);
        return webSocketConnectionService.saveWebSocketConnection(webSocketConnection);
    }

    @After
    public void tearDown() throws Exception {
        PageData<WebSocketSubscription> subscriptions = webSocketSubscriptionService.getWebSocketSubscriptions(
                savedWebSocketConnection.getId(), new PageLink(1000, 0));
        for (var subscription : subscriptions.getData()) {
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
        WebSocketSubscriptionConfiguration config = new WebSocketSubscriptionConfiguration();
        config.setTopicFilter("");
        subscription.setConfiguration(config);

        webSocketSubscriptionService.saveWebSocketSubscription(subscription);
    }

    @Test(expected = DataValidationException.class)
    public void givenWebSocketSubscriptionWithWrongConfigurationIncorrectTopicFilter_whenExecuteSave_thenFailure() {
        WebSocketSubscription subscription = new WebSocketSubscription();
        subscription.setWebSocketConnectionId(savedWebSocketConnection.getId());
        WebSocketSubscriptionConfiguration config = new WebSocketSubscriptionConfiguration();
        config.setTopicFilter("test/#/wrong");
        subscription.setConfiguration(config);

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

        PageData<WebSocketSubscription> pageData = webSocketSubscriptionService.getWebSocketSubscriptions(
                savedWebSocketConnection.getId(), new PageLink(1000, 0));
        List<WebSocketSubscription> loadedWebSocketSubscriptions = pageData.getData();

        webSocketSubscriptions.sort(idComparator);
        loadedWebSocketSubscriptions.sort(idComparator);

        Assert.assertEquals(webSocketSubscriptions, loadedWebSocketSubscriptions);

        loadedWebSocketSubscriptions.forEach(wss ->
                webSocketSubscriptionService.deleteWebSocketSubscription(wss.getId()));

        pageData = webSocketSubscriptionService.getWebSocketSubscriptions(
                savedWebSocketConnection.getId(), new PageLink(1000, 0));
        Assert.assertTrue(pageData.getData().isEmpty());
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

    @Test
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

    @Test
    public void subscriptionsArePagedPerConnection() {
        WebSocketConnection another = saveWebSocketConnection("Another connection");
        for (String filter : List.of("a/1", "a/2", "a/3")) {
            webSocketSubscriptionService.saveWebSocketSubscription(getWebSocketSubscription(filter));
        }
        webSocketSubscriptionService.saveWebSocketSubscription(getWsSubscription("b/1", another.getId()));

        PageData<WebSocketSubscription> first = webSocketSubscriptionService.getWebSocketSubscriptions(
                savedWebSocketConnection.getId(), new PageLink(2, 0));
        PageData<WebSocketSubscription> second = webSocketSubscriptionService.getWebSocketSubscriptions(
                savedWebSocketConnection.getId(), new PageLink(2, 1));
        PageData<WebSocketSubscription> others = webSocketSubscriptionService.getWebSocketSubscriptions(
                another.getId(), new PageLink(10, 0));

        assertThat(first.getData()).hasSize(2);
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.getData()).hasSize(1);
        assertThat(second.hasNext()).isFalse();
        assertThat(others.getData()).extracting(s -> s.getConfiguration().getTopicFilter()).containsExactly("b/1");
        webSocketConnectionService.deleteWebSocketConnection(another.getId());
    }

    @Test
    public void removingAMissingIdAnswersTrue() {
        assertThat(webSocketSubscriptionDao.removeById(UUID.randomUUID())).isTrue();
    }

    // the service refuses an unknown connection first: only a caller of the DAO reaches the foreign key
    @Test
    public void aSubscriptionOfAMissingConnectionIsRefusedByTheForeignKey() {
        WebSocketSubscription orphan = getWsSubscription("orphan/" + UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> webSocketSubscriptionDao.save(orphan))
                .satisfies(e -> assertThat(EntityConstraintViolationException.isViolationOf(e, "fk_websocket_connection_id")).isTrue());
        assertThat(webSocketSubscriptionDao.find()).extracting(s -> s.getConfiguration().getTopicFilter())
                .doesNotContain(orphan.getConfiguration().getTopicFilter());
    }

    private WebSocketSubscription getWebSocketSubscription(String topicFilter) {
        return getWsSubscription(topicFilter);
    }

    private WebSocketSubscription getWebSocketSubscription() {
        return getWsSubscription("test/topic/+");
    }

    private WebSocketSubscription getWsSubscription(String topicFilter) {
        return getWsSubscription(topicFilter, savedWebSocketConnection.getId());
    }

    private WebSocketSubscription getWsSubscription(String topicFilter, UUID webSocketConnectionId) {
        WebSocketSubscription subscription = new WebSocketSubscription();
        subscription.setWebSocketConnectionId(webSocketConnectionId);
        subscription.setConfiguration(getWebSocketSubscriptionConfiguration(topicFilter));
        return subscription;
    }

    private WebSocketSubscriptionConfiguration getWebSocketSubscriptionConfiguration(String topicFilter) {
        WebSocketSubscriptionConfiguration webSocketSubscriptionConfiguration = new WebSocketSubscriptionConfiguration();
        webSocketSubscriptionConfiguration.setTopicFilter(topicFilter);
        return webSocketSubscriptionConfiguration;
    }

}
