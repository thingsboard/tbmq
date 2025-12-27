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
package org.thingsboard.mqtt.broker.actors.client.service.session;

import com.google.common.util.concurrent.Futures;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.cache.Cache;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.client.messages.ClientCallback;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionRequestInfo;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.cache.TbCacheOps;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionEventResponseProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientSessionEventQueueFactory;
import org.thingsboard.mqtt.broker.service.limits.RateLimitCacheService;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationRemovedEventService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationTopicService;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = SessionClusterManagerImpl.class)
@TestPropertySource(properties = {
        "queue.client-session-event-response.max-request-timeout=100000",
        "queue.client-session-event-response.response-sender-threads=8"
})
public class SessionClusterManagerImplTest {

    @MockitoBean
    ClientSessionService clientSessionService;
    @MockitoBean
    ClientSubscriptionService clientSubscriptionService;
    @MockitoBean
    DisconnectClientCommandService disconnectClientCommandService;
    @MockitoBean
    ClientSessionEventQueueFactory clientSessionEventQueueFactory;
    @MockitoBean
    ServiceInfoProvider serviceInfoProvider;
    @MockitoBean
    MsgPersistenceManager msgPersistenceManager;
    @MockitoBean
    ApplicationRemovedEventService applicationRemovedEventService;
    @MockitoBean
    ApplicationTopicService applicationTopicService;
    @MockitoBean
    RateLimitCacheService rateLimitCacheService;
    @MockitoBean
    TbCacheOps cacheOps;
    @MockitoBean
    TimeseriesService timeseriesService;

    @MockitoSpyBean
    SessionClusterManagerImpl sessionClusterManager;

    @Before
    public void setUp() {
        TbQueueProducer<TbProtoQueueMsg<ClientSessionEventResponseProto>> eventResponseProducer = new TbQueueProducer<>() {
            @Override
            public String getDefaultTopic() {
                return null;
            }

            @Override
            public void send(TbProtoQueueMsg<ClientSessionEventResponseProto> msg, TbQueueCallback callback) {
            }

            @Override
            public void send(String topic, Integer partition, TbProtoQueueMsg<ClientSessionEventResponseProto> msg, TbQueueCallback callback) {
            }

            @Override
            public void stop() {
            }
        };
        doReturn(eventResponseProducer).when(clientSessionEventQueueFactory).createEventResponseProducer(any());
        when(timeseriesService.removeAllLatestForClient(anyString())).thenReturn(Futures.immediateFuture(null));
    }

    @Test
    public void givenSameSessionId_whenProcessConnectionRequest_thenDoNotUpdateClientSession() {
        ClientSessionInfo clientSessionInfo = ClientSessionInfoFactory.getClientSessionInfo("clientId");
        SessionInfo sessionInfo = ClientSessionInfoFactory.clientSessionInfoToSessionInfo(clientSessionInfo);

        doReturn(clientSessionInfo).when(clientSessionService).getClientSessionInfo(any());

        sessionClusterManager.processConnectionRequest(sessionInfo, getConnectionRequestInfo());

        verify(sessionClusterManager, never()).updateClientSession(any(), any(), any());
    }

    @Test
    public void givenPresentNonPersistentSession_whenProcessConnectionRequest_thenVerify() {
        Cache cache = mock(Cache.class);
        when(cacheOps.cache(anyString())).thenReturn(cache);

        SessionInfo sessionInfoNew = getSessionInfo("clientId1");
        ClientSessionInfo sessionInfoOld = ClientSessionInfoFactory.getClientSessionInfo("clientId1");

        doReturn(sessionInfoOld).when(clientSessionService).getClientSessionInfo(any());

        sessionClusterManager.processConnectionRequest(sessionInfoNew, getConnectionRequestInfo());

        verify(disconnectClientCommandService).disconnectOnSessionConflict(any(), any(), any(), eq(true));
        verify(clientSessionService).clearClientSession(any(), any());
        verify(clientSubscriptionService).clearSubscriptionsAndPersist(any(), any());
        verify(sessionClusterManager).updateClientSession(any(), any(), any());
    }

    @Test
    public void givenPresentPersistentSession_whenUpdateClientSession_thenVerify() {
        SessionInfo sessionInfoNew = getSessionInfo("clientId");

        sessionClusterManager.updateClientSession(sessionInfoNew, getConnectionRequestInfo(),
                ClientSessionInfo.withClientType(ClientType.APPLICATION));

        verify(clientSubscriptionService).clearSubscriptionsAndPersist(any(), any());
        verify(msgPersistenceManager, times(2)).clearPersistedMessages(any(), any());
        verify(applicationRemovedEventService).sendApplicationRemovedEvent(any());
        verify(clientSessionService).saveClientSession(any(), any());
    }

    @Test
    public void givenPresentNonPersistentSession_whenUpdateClientSession_thenVerify() {
        SessionInfo sessionInfoNew = getSessionInfo("clientId1");

        sessionClusterManager.updateClientSession(sessionInfoNew, getConnectionRequestInfo(),
                ClientSessionInfo.withClientType(ClientType.APPLICATION));

        verify(msgPersistenceManager, times(2)).clearPersistedMessages(any(), any());
        verify(applicationRemovedEventService).sendApplicationRemovedEvent(any());
        verify(clientSessionService).saveClientSession(any(), any());
    }

    @Test
    public void givenPresentNonPersistentSessionSameClientType_whenUpdateClientSession_thenVerify() {
        SessionInfo sessionInfoNew = getSessionInfo("clientId1");

        sessionClusterManager.updateClientSession(sessionInfoNew, getConnectionRequestInfo(),
                ClientSessionInfo.withClientType(ClientType.DEVICE));

        verify(clientSessionService).saveClientSession(any(), any());
    }

    @Test
    public void givenPresentPersistentSession_whenProcessConnectionRequest_thenVerify() {
        SessionInfo sessionInfoNew = getSessionInfo("clientId1");
        ClientSessionInfo sessionInfoOld = ClientSessionInfoFactory
                .getClientSessionInfo(true, "clientId2", ClientType.DEVICE, false);

        doReturn(sessionInfoOld).when(clientSessionService).getClientSessionInfo(any());

        sessionClusterManager.processConnectionRequest(sessionInfoNew, getConnectionRequestInfo());

        verify(disconnectClientCommandService).disconnectOnSessionConflict(any(), any(), any(), eq(true));
        verify(clientSessionService, times(2)).saveClientSession(any(), any());
        verify(sessionClusterManager).updateClientSession(any(), any(), any());
    }

    @Test
    public void givenRequestTime_whenIsRequestTimedOut_thenOk() {
        boolean requestTimedOut = sessionClusterManager.isRequestTimedOut(1);
        Assert.assertTrue(requestTimedOut);

        requestTimedOut = sessionClusterManager.isRequestTimedOut(System.currentTimeMillis());
        Assert.assertFalse(requestTimedOut);
    }

    @Test
    public void givenDeviceClientType_whenProcessRemoveApplicationTopicRequest_thenOk() {
        ClientSessionInfo clientSessionInfo = ClientSessionInfoFactory
                .getClientSessionInfo("clientId", ClientType.DEVICE, false);
        doReturn(clientSessionInfo).when(clientSessionService).getClientSessionInfo(any());

        sessionClusterManager.processRemoveApplicationTopicRequest("clientId", new ClientCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailure(Throwable e) {
            }
        });
        verify(applicationTopicService).deleteTopic(any(), any());
    }

    @Test
    public void givenApplicationClientType_whenProcessRemoveApplicationTopicRequest_thenOk() {
        ClientSessionInfo clientSessionInfo = ClientSessionInfoFactory
                .getClientSessionInfo("clientId", ClientType.APPLICATION, false);
        doReturn(clientSessionInfo).when(clientSessionService).getClientSessionInfo(any());

        sessionClusterManager.processRemoveApplicationTopicRequest("clientId", new ClientCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailure(Throwable e) {
            }
        });
        verify(applicationTopicService, never()).deleteTopic(any(), any());
    }

    @Test
    public void givenSession_whenProcessClearSession_thenDoNothing() {
        ClientSessionInfo clientSessionInfo = ClientSessionInfoFactory.getClientSessionInfo("clientId");
        doReturn(clientSessionInfo).when(clientSessionService).getClientSessionInfo(any());

        sessionClusterManager.processClearSession("clientId", UUID.randomUUID());

        verify(clientSessionService, never()).clearClientSession(any(), any());
        verify(clientSubscriptionService, never()).clearSubscriptionsAndPersist(any(), any());
        verify(msgPersistenceManager, never()).clearPersistedMessages(any(), any());

        sessionClusterManager.processClearSession("clientId", clientSessionInfo.getSessionId());

        verify(clientSessionService, never()).clearClientSession(eq("clientId"), eq(null));
        verify(clientSubscriptionService, never()).clearSubscriptionsAndPersist(eq("clientId"), eq(null));
        verify(msgPersistenceManager, never()).clearPersistedMessages(eq("clientId"), eq(ClientType.DEVICE));
    }

    @Test
    public void givenDisconnectedSession_whenProcessClearSession_thenVerify() {
        Cache cache = mock(Cache.class);
        when(cacheOps.cache(anyString())).thenReturn(cache);

        ClientSessionInfo clientSessionInfo = ClientSessionInfoFactory.getClientSessionInfo("clientId", "serviceId", false);
        doReturn(clientSessionInfo).when(clientSessionService).getClientSessionInfo(any());

        sessionClusterManager.processClearSession("clientId", clientSessionInfo.getSessionId());

        verify(clientSessionService).clearClientSession(any(), any());
        verify(clientSubscriptionService).clearSubscriptionsAndPersist(any(), any());
        verify(msgPersistenceManager).clearPersistedMessages(any(), any());
    }

    @Test
    public void givenClientSession_whenProcessMarkSessionDisconnected_thenOk() {
        ClientSessionInfo clientSessionInfoConnected = ClientSessionInfoFactory
                .getClientSessionInfo(
                        "test",
                        true,
                        "serviceId",
                        false,
                        ClientType.APPLICATION,
                        System.currentTimeMillis(),
                        0);

        ClientSessionInfo clientSessionDisconnected = sessionClusterManager.markSessionDisconnected(clientSessionInfoConnected, -1);

        Assert.assertFalse(clientSessionDisconnected.isConnected());
        Assert.assertEquals("test", clientSessionDisconnected.getClientId());
        Assert.assertEquals(ClientType.APPLICATION, clientSessionDisconnected.getType());
        Assert.assertFalse(clientSessionDisconnected.isCleanStart());
        Assert.assertEquals(0, clientSessionDisconnected.getSessionExpiryInterval());
    }

    @Test
    public void givenClientSessionAndNewSessionsExpiryInterval_whenProcessMarkSessionDisconnected_thenOk() {
        ClientSessionInfo clientSessionInfoConnected = ClientSessionInfoFactory
                .getClientSessionInfo(
                        "test",
                        true,
                        "serviceId",
                        false,
                        ClientType.APPLICATION,
                        System.currentTimeMillis(),
                        0);

        ClientSessionInfo clientSessionDisconnected = sessionClusterManager.markSessionDisconnected(clientSessionInfoConnected, 100);

        Assert.assertFalse(clientSessionDisconnected.isConnected());
        Assert.assertEquals("test", clientSessionDisconnected.getClientId());
        Assert.assertEquals(ClientType.APPLICATION, clientSessionDisconnected.getType());
        Assert.assertFalse(clientSessionDisconnected.isCleanStart());
        Assert.assertEquals(100, clientSessionDisconnected.getSessionExpiryInterval());
    }

    private SessionInfo getSessionInfo(String clientId) {
        ClientInfo clientInfo = ClientSessionInfoFactory.getClientInfo(clientId, ClientType.DEVICE);
        return ClientSessionInfoFactory.getSessionInfo(true, "serviceId", clientInfo);
    }

    private ConnectionRequestInfo getConnectionRequestInfo() {
        return new ConnectionRequestInfo(UUID.randomUUID(), System.currentTimeMillis(), "responseTopic");
    }

}
