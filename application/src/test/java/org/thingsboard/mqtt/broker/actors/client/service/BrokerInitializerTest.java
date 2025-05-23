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
package org.thingsboard.mqtt.broker.actors.client.service;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionService;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.subscription.ClientTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.limits.RateLimitCacheService;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.BlockedClientService;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.consumer.BlockedClientConsumerService;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.ClientIdBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.queue.DeviceMsgQueueConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerService;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgConsumerService;
import org.thingsboard.mqtt.broker.service.processing.downlink.basic.BasicDownLinkConsumer;
import org.thingsboard.mqtt.broker.service.processing.downlink.persistent.PersistentDownLinkConsumer;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionConsumer;
import org.thingsboard.mqtt.broker.service.subscription.data.SubscriptionsSourceKey;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = BrokerInitializer.class)
public class BrokerInitializerTest {

    @MockBean
    ClientSessionConsumer clientSessionConsumer;
    @MockBean
    ClientSubscriptionConsumer clientSubscriptionConsumer;
    @MockBean
    RetainedMsgConsumer retainedMsgConsumer;
    @MockBean
    BlockedClientConsumerService blockedClientConsumer;
    @MockBean
    ClientSessionService clientSessionService;
    @MockBean
    ClientSubscriptionService clientSubscriptionService;
    @MockBean
    RetainedMsgListenerService retainedMsgListenerService;
    @MockBean
    BlockedClientService blockedClientService;
    @MockBean
    ClientSessionEventService clientSessionEventService;
    @MockBean
    ServiceInfoProvider serviceInfoProvider;
    @MockBean
    RateLimitCacheService rateLimitCacheService;
    @MockBean
    ClientSessionEventConsumer clientSessionEventConsumer;
    @MockBean
    PublishMsgConsumerService publishMsgConsumerService;
    @MockBean
    DisconnectClientCommandConsumer disconnectClientCommandConsumer;
    @MockBean
    DeviceMsgQueueConsumer deviceMsgQueueConsumer;
    @MockBean
    BasicDownLinkConsumer basicDownLinkConsumer;
    @MockBean
    PersistentDownLinkConsumer persistentDownLinkConsumer;

    @SpyBean
    BrokerInitializer brokerInitializer;

    @Before
    public void setUp() {

    }

    @Test
    public void testInitClientSessions() throws QueuePersistenceException {
        Map<String, ClientSessionInfo> preparedSessions = prepareSessions();

        doReturn(preparedSessions).when(clientSessionConsumer).initLoad();
        doReturn("serviceId1").when(serviceInfoProvider).getServiceId();

        Map<String, ClientSessionInfo> allClientSessions = brokerInitializer.initClientSessions();

        Assert.assertEquals(preparedSessions.size(), allClientSessions.size());

        ClientSessionInfo clientSessionInfo = getSessionForServiceId(allClientSessions);

        Assert.assertNotNull(clientSessionInfo);
        Assert.assertFalse(clientSessionInfo.isConnected());
        verify(rateLimitCacheService).initSessionCount(preparedSessions.size());
        verify(clientSessionEventService).requestClientSessionCleanup(any());
    }

    private ClientSessionInfo getSessionForServiceId(Map<String, ClientSessionInfo> allClientSessions) {
        return allClientSessions
                .values().stream()
                .filter(csi -> csi.getServiceId().equals("serviceId1"))
                .findFirst().orElse(null);
    }

    private Map<String, ClientSessionInfo> prepareSessions() {
        Map<String, ClientSessionInfo> allClientSessions = new HashMap<>();
        allClientSessions.put("clientId1", ClientSessionInfoFactory.getClientSessionInfo("clientId1", "serviceId1"));
        allClientSessions.put("clientId2", ClientSessionInfoFactory.getClientSessionInfo("clientId2", "serviceId2"));
        return allClientSessions;
    }

    @Test
    public void testIsNotPersistent() {
        ClientSessionInfo clientSessionInfo1 = getSessionInfo(false, 0);
        ClientSessionInfo clientSessionInfo2 = getSessionInfo(false, 10);
        ClientSessionInfo clientSessionInfo3 = getSessionInfo(true, 0);
        ClientSessionInfo clientSessionInfo4 = getSessionInfo(true, 10);

        Assert.assertFalse(brokerInitializer.isCleanSession(clientSessionInfo1));
        Assert.assertFalse(brokerInitializer.isCleanSession(clientSessionInfo2));
        Assert.assertTrue(brokerInitializer.isCleanSession(clientSessionInfo3));
        Assert.assertFalse(brokerInitializer.isCleanSession(clientSessionInfo4));
    }

    private ClientSessionInfo getSessionInfo(boolean cleanStart, int sessionExpiryInterval) {
        return ClientSessionInfo.builder()
                .cleanStart(cleanStart)
                .sessionExpiryInterval(sessionExpiryInterval)
                .build();
    }

    @Test
    public void testInitRetainedMessages() throws QueuePersistenceException {
        Map<String, RetainedMsg> map = Map.of("key", new RetainedMsg("topic", BrokerConstants.DUMMY_PAYLOAD));
        when(retainedMsgConsumer.initLoad()).thenReturn(map);

        brokerInitializer.initRetainedMessages();

        verify(retainedMsgConsumer).initLoad();
        verify(retainedMsgListenerService).init(eq(map));
    }

    @Test
    public void testInitBlockedClients() throws QueuePersistenceException {
        Map<String, BlockedClient> map = Map.of("key", new ClientIdBlockedClient("clientId"));
        when(blockedClientConsumer.initLoad()).thenReturn(map);

        brokerInitializer.initBlockedClients();

        verify(blockedClientConsumer).initLoad();
        verify(blockedClientService).init(eq(map));
    }

    @Test
    public void testInitEmptyClientSubscriptions() throws QueuePersistenceException {
        Map<SubscriptionsSourceKey, Set<TopicSubscription>> map = new HashMap<>(Map.of(
                SubscriptionsSourceKey.newInstance("test1"), Set.of(new ClientTopicSubscription("#")),
                SubscriptionsSourceKey.newInstance("test2"), Set.of(new ClientTopicSubscription("test/#"), new ClientTopicSubscription("temp/#"))
        ));
        when(clientSubscriptionConsumer.initLoad()).thenReturn(map);

        brokerInitializer.initClientSubscriptions(Map.of());

        verify(clientSubscriptionConsumer).initLoad();
        verify(clientSubscriptionService).init(eq(Map.of()));
    }

    @Test
    public void testInitClientSubscriptions() throws QueuePersistenceException {
        Map<SubscriptionsSourceKey, Set<TopicSubscription>> map = new HashMap<>(Map.of(
                SubscriptionsSourceKey.newInstance("clientId1"), Set.of(new ClientTopicSubscription("#")),
                SubscriptionsSourceKey.newInstance("clientId2"), Set.of(new ClientTopicSubscription("test/#"), new ClientTopicSubscription("temp/#"))
        ));
        when(clientSubscriptionConsumer.initLoad()).thenReturn(map);

        brokerInitializer.initClientSubscriptions(prepareSessions());

        verify(clientSubscriptionConsumer).initLoad();
        verify(clientSubscriptionService).init(eq(map));
    }

    @Test
    public void testOnApplicationEvent() throws QueuePersistenceException {
        ApplicationReadyEvent readyEvent = mock(ApplicationReadyEvent.class);
        brokerInitializer.onApplicationEvent(readyEvent);

        verify(clientSessionConsumer).initLoad();
        verify(clientSessionService).init(anyMap());
        verify(clientSessionService).startListening(any());

        verify(clientSubscriptionConsumer).initLoad();
        verify(clientSubscriptionService).init(anyMap());
        verify(clientSubscriptionService).startListening(any());

        verify(retainedMsgConsumer).initLoad();
        verify(retainedMsgListenerService).init(anyMap());
        verify(retainedMsgListenerService).startListening(any());

        verify(blockedClientConsumer).initLoad();
        verify(blockedClientService).init(anyMap());
        verify(retainedMsgListenerService).startListening(any());

        verify(clientSessionEventConsumer).startConsuming();
        verify(publishMsgConsumerService).startConsuming();
        verify(disconnectClientCommandConsumer).startConsuming();
        verify(deviceMsgQueueConsumer).startConsuming();
        verify(basicDownLinkConsumer).startConsuming();
        verify(persistentDownLinkConsumer).startConsuming();
    }

}
