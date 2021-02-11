/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.client;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientSessionQueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.TopicSubscription;
import org.thingsboard.mqtt.broker.service.mqtt.client.ClientSessionService;
import org.thingsboard.mqtt.broker.service.mqtt.client.DefaultClientSessionService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class ClientSessionServiceTestSuite {
    private static final String DEFAULT_CLIENT_ID = "test";
    private static final ClientType DEFAULT_CLIENT_TYPE = ClientType.DEVICE;
    private static final ClientSession DEFAULT_CLIENT_SESSION = ClientSession.builder()
            .connected(true)
            .persistent(true)
            .clientInfo(new ClientInfo(DEFAULT_CLIENT_ID, DEFAULT_CLIENT_TYPE))
            .topicSubscriptions(Set.of(new TopicSubscription("test/topic", 1)))
            .build()
            ;

    private ClientSessionService clientSessionService;

    @Before
    public void init() {
        ClientSessionQueueFactory clientSessionQueueFactoryMock = Mockito.mock(ClientSessionQueueFactory.class);
        TbQueueControlledOffsetConsumer<TbProtoQueueMsg<QueueProtos.ClientSessionProto>> mockConsumer = Mockito.mock(TbQueueControlledOffsetConsumer.class);
        TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClientSessionProto>> mockProducer = Mockito.mock(TbQueueProducer.class);
        Mockito.when(clientSessionQueueFactoryMock.createConsumer()).thenReturn(mockConsumer);
        Mockito.when(clientSessionQueueFactoryMock.createProducer()).thenReturn(mockProducer);
        Mockito.doAnswer(invocation -> {
            TbQueueCallback callback = invocation.getArgument(2);
            callback.onSuccess(null);
            return null;
        }).when(mockProducer).send(Mockito.any(), Mockito.any());
        this.clientSessionService = new DefaultClientSessionService(clientSessionQueueFactoryMock);
    }

    @Test
    public void testReplaceClientSession_withEmpty() {
        clientSessionService.replaceClientSession(DEFAULT_CLIENT_ID, null, DEFAULT_CLIENT_SESSION);
        Assert.assertEquals(DEFAULT_CLIENT_SESSION, clientSessionService.getClientSession(DEFAULT_CLIENT_ID));
    }

    @Test
    public void testReplaceClientSession_withExistent() {
        clientSessionService.replaceClientSession(DEFAULT_CLIENT_ID, null, DEFAULT_CLIENT_SESSION);

        ClientSession updatedClientSession = DEFAULT_CLIENT_SESSION.toBuilder()
                .topicSubscriptions(new HashSet<>())
                .build();
        clientSessionService.replaceClientSession(DEFAULT_CLIENT_ID, DEFAULT_CLIENT_SESSION, updatedClientSession);
        Assert.assertEquals(updatedClientSession, clientSessionService.getClientSession(DEFAULT_CLIENT_ID));
    }

    @Test(expected = MqttException.class)
    public void testReplaceClientSession_withExistentNull() {
        clientSessionService.replaceClientSession(DEFAULT_CLIENT_ID, null, DEFAULT_CLIENT_SESSION);

        ClientSession updatedClientSession = DEFAULT_CLIENT_SESSION.toBuilder()
                .topicSubscriptions(new HashSet<>())
                .build();
        clientSessionService.replaceClientSession(DEFAULT_CLIENT_ID, null, updatedClientSession);
    }

    @Test(expected = MqttException.class)
    public void testReplaceClientSession_withExistentNotEqual() {
        clientSessionService.replaceClientSession(DEFAULT_CLIENT_ID, null, DEFAULT_CLIENT_SESSION);
        ClientSession updatedClientSession = DEFAULT_CLIENT_SESSION.toBuilder()
                .topicSubscriptions(new HashSet<>())
                .build();
        clientSessionService.replaceClientSession(DEFAULT_CLIENT_ID, updatedClientSession, updatedClientSession);
    }

    @Test
    public void testGetPersistedClients() {
        ClientSession persistentSession1 = DEFAULT_CLIENT_SESSION.toBuilder()
                .clientInfo(new ClientInfo("persistent_1", ClientType.DEVICE))
                .persistent(true).build();
        ClientSession persistentSession2 = DEFAULT_CLIENT_SESSION.toBuilder()
                .clientInfo(new ClientInfo("persistent_2", ClientType.DEVICE))
                .persistent(true).build();
        ClientSession notPersistentSession = DEFAULT_CLIENT_SESSION.toBuilder()
                .clientInfo(new ClientInfo("not_persistent", ClientType.DEVICE))
                .persistent(false).build();
        clientSessionService.replaceClientSession("persistent_1", null, persistentSession1);
        clientSessionService.replaceClientSession("persistent_2", null, persistentSession2);
        clientSessionService.replaceClientSession("not_persistent", null, notPersistentSession);

        List<String> persistedClients = clientSessionService.getPersistedClients();
        Assert.assertEquals(2, persistedClients.size());
        Assert.assertTrue(persistedClients.contains("persistent_1") && persistedClients.contains("persistent_2"));
    }

    @Test(expected = MqttException.class)
    public void testFail_differentClientIds() {
        ClientSession notValidClientSession = DEFAULT_CLIENT_SESSION.toBuilder()
                .clientInfo(new ClientInfo(DEFAULT_CLIENT_ID + "_not_valid", ClientType.DEVICE))
                .build();
        clientSessionService.replaceClientSession(DEFAULT_CLIENT_ID, null, notValidClientSession);
    }
}
