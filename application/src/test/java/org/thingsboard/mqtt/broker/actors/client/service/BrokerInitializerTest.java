/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.TbActorSystem;
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionService;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionConsumer;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.queue.DeviceMsgQueueConsumer;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgConsumerService;
import org.thingsboard.mqtt.broker.service.processing.downlink.basic.BasicDownLinkConsumer;
import org.thingsboard.mqtt.broker.service.processing.downlink.persistent.PersistentDownLinkConsumer;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionConsumer;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

// TODO: 19/05/2022 add more tests and verify BrokerInitializer logic
@RunWith(MockitoJUnitRunner.class)
public class BrokerInitializerTest {

    @Mock
    ClientSubscriptionConsumer clientSubscriptionConsumer;
    @Mock
    ClientSessionConsumer clientSessionConsumer;
    @Mock
    ClientSubscriptionService clientSubscriptionService;
    @Mock
    ClientSessionService clientSessionService;
    @Mock
    ActorSystemContext actorSystemContext;
    @Mock
    TbActorSystem actorSystem;
    @Mock
    ClientSessionEventService clientSessionEventService;
    @Mock
    ServiceInfoProvider serviceInfoProvider;
    @Mock
    DisconnectClientCommandConsumer disconnectClientCommandConsumer;
    @Mock
    ClientSessionEventConsumer clientSessionEventConsumer;
    @Mock
    DeviceMsgQueueConsumer deviceMsgQueueConsumer;
    @Mock
    PublishMsgConsumerService publishMsgConsumerService;
    @Mock
    BasicDownLinkConsumer basicDownLinkConsumer;
    @Mock
    PersistentDownLinkConsumer persistentDownLinkConsumer;

    BrokerInitializer brokerInitializer;

    @Before
    public void setUp() {
        brokerInitializer = spy(init());
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
        Assert.assertFalse(clientSessionInfo.getClientSession().isConnected());
    }

    private ClientSessionInfo getSessionForServiceId(Map<String, ClientSessionInfo> allClientSessions) {
        return allClientSessions
                .values().stream()
                .filter(csi -> csi.getClientSession().getSessionInfo().getServiceId().equals("serviceId1"))
                .findFirst().orElse(null);
    }

    private Map<String, ClientSessionInfo> prepareSessions() {
        Map<String, ClientSessionInfo> allClientSessions = new HashMap<>();
        allClientSessions.put("clientId1", ClientSessionInfoFactory.getClientSessionInfo("clientId1", "serviceId1"));
        allClientSessions.put("clientId2", ClientSessionInfoFactory.getClientSessionInfo("clientId2", "serviceId2"));
        return allClientSessions;
    }

    private BrokerInitializer init() {
        return new BrokerInitializer(clientSubscriptionConsumer, clientSessionConsumer, clientSubscriptionService,
                clientSessionService, actorSystemContext, actorSystem, clientSessionEventService,
                serviceInfoProvider, disconnectClientCommandConsumer, clientSessionEventConsumer,
                deviceMsgQueueConsumer, publishMsgConsumerService, basicDownLinkConsumer, persistentDownLinkConsumer);
    }
}