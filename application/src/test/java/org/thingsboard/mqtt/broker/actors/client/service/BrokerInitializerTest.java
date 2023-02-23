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
package org.thingsboard.mqtt.broker.actors.client.service;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.TbActorSystem;
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionService;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.queue.DeviceMsgQueueConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerService;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgConsumerService;
import org.thingsboard.mqtt.broker.service.processing.downlink.basic.BasicDownLinkConsumer;
import org.thingsboard.mqtt.broker.service.processing.downlink.persistent.PersistentDownLinkConsumer;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionConsumer;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.doReturn;

// TODO: 19/05/2022 add more tests
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = BrokerInitializer.class)
public class BrokerInitializerTest {

    @MockBean
    ClientSubscriptionConsumer clientSubscriptionConsumer;
    @MockBean
    ClientSessionConsumer clientSessionConsumer;
    @MockBean
    RetainedMsgConsumer retainedMsgConsumer;
    @MockBean
    ClientSubscriptionService clientSubscriptionService;
    @MockBean
    ClientSessionService clientSessionService;
    @MockBean
    RetainedMsgListenerService retainedMsgListenerService;
    @MockBean
    ActorSystemContext actorSystemContext;
    @MockBean
    TbActorSystem actorSystem;
    @MockBean
    ClientSessionEventService clientSessionEventService;
    @MockBean
    ServiceInfoProvider serviceInfoProvider;
    @MockBean
    DisconnectClientCommandConsumer disconnectClientCommandConsumer;
    @MockBean
    ClientSessionEventConsumer clientSessionEventConsumer;
    @MockBean
    DeviceMsgQueueConsumer deviceMsgQueueConsumer;
    @MockBean
    PublishMsgConsumerService publishMsgConsumerService;
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
        SessionInfo clientSessionInfo1 = getSessionInfo(false, 0);
        SessionInfo clientSessionInfo2 = getSessionInfo(false, 10);
        SessionInfo clientSessionInfo3 = getSessionInfo(true, 0);
        SessionInfo clientSessionInfo4 = getSessionInfo(true, 10);

        Assert.assertFalse(brokerInitializer.isCleanSession(clientSessionInfo1));
        Assert.assertFalse(brokerInitializer.isCleanSession(clientSessionInfo2));
        Assert.assertTrue(brokerInitializer.isCleanSession(clientSessionInfo3));
        Assert.assertFalse(brokerInitializer.isCleanSession(clientSessionInfo4));
    }

    private SessionInfo getSessionInfo(boolean cleanStart, int sessionExpiryInterval) {
        return SessionInfo.builder()
                .cleanStart(cleanStart)
                .sessionExpiryInterval(sessionExpiryInterval)
                .build();
    }
}