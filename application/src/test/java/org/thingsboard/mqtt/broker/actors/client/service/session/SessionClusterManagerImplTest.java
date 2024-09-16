/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.Cache;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.client.messages.ClientCallback;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionRequestInfo;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.cache.CacheNameResolver;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSession;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.ConnectionInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
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

    @MockBean
    ClientSessionService clientSessionService;
    @MockBean
    ClientSubscriptionService clientSubscriptionService;
    @MockBean
    DisconnectClientCommandService disconnectClientCommandService;
    @MockBean
    ClientSessionEventQueueFactory clientSessionEventQueueFactory;
    @MockBean
    ServiceInfoProvider serviceInfoProvider;
    @MockBean
    MsgPersistenceManager msgPersistenceManager;
    @MockBean
    ApplicationRemovedEventService applicationRemovedEventService;
    @MockBean
    ApplicationTopicService applicationTopicService;
    @MockBean
    RateLimitCacheService rateLimitCacheService;
    @MockBean
    CacheNameResolver cacheNameResolver;
    @MockBean
    TimeseriesService timeseriesService;

    @SpyBean
    SessionClusterManagerImpl sessionClusterManager;

    @Before
    public void setUp() {
        TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClientSessionEventResponseProto>> eventResponseProducer = new TbQueueProducer<>() {
            @Override
            public String getDefaultTopic() {
                return null;
            }

            @Override
            public void send(TbProtoQueueMsg<QueueProtos.ClientSessionEventResponseProto> msg, TbQueueCallback callback) {
            }

            @Override
            public void send(String topic, Integer partition, TbProtoQueueMsg<QueueProtos.ClientSessionEventResponseProto> msg, TbQueueCallback callback) {
            }

            @Override
            public void stop() {
            }
        };
        doReturn(eventResponseProducer).when(clientSessionEventQueueFactory).createEventResponseProducer(any());
        when(timeseriesService.removeAllLatestForClient(anyString())).thenReturn(Futures.immediateFuture(null));
        sessionClusterManager.init();
    }

    @Test
    public void givenSameSessionId_whenProcessConnectionRequest_thenDoNotUpdateClientSession() {
        SessionInfo sessionInfo = getSessionInfo("clientId");

        doReturn(getClientSession(true, sessionInfo)).when(clientSessionService).getClientSession(any());

        sessionClusterManager.processConnectionRequest(sessionInfo, getConnectionRequestInfo());

        verify(sessionClusterManager, never()).updateClientSession(any(), any(), any());
    }

    @Test
    public void givenPresentNonPersistentSession_whenProcessConnectionRequest_thenVerify() {
        Cache cache = mock(Cache.class);
        when(cacheNameResolver.getCache(anyString())).thenReturn(cache);

        SessionInfo sessionInfoNew = getSessionInfo("clientId1");
        SessionInfo sessionInfoOld = getSessionInfo("clientId2");

        doReturn(getClientSession(true, sessionInfoOld)).when(clientSessionService).getClientSession(any());

        sessionClusterManager.processConnectionRequest(sessionInfoNew, getConnectionRequestInfo());

        verify(disconnectClientCommandService, times(1)).disconnectOnSessionConflict(any(), any(), any(), eq(true));
        verify(clientSessionService, times(1)).clearClientSession(any(), any());
        verify(clientSubscriptionService, times(1)).clearSubscriptionsAndPersist(any(), any());
        verify(sessionClusterManager, times(1)).updateClientSession(any(), any(), any());
    }

    @Test
    public void givenPresentPersistentSession_whenUpdateClientSession_thenVerify() {
        SessionInfo sessionInfoNew = getSessionInfo("clientId", ClientType.DEVICE, true);

        sessionClusterManager.updateClientSession(sessionInfoNew, getConnectionRequestInfo(),
                SessionInfo.withClientType(ClientType.APPLICATION));

        verify(clientSubscriptionService, times(1)).clearSubscriptionsAndPersist(any(), any());
        verify(msgPersistenceManager, times(2)).clearPersistedMessages(any());
        verify(applicationRemovedEventService, times(1)).sendApplicationRemovedEvent(any());
        verify(clientSessionService, times(1)).saveClientSession(any(), any(), any());
    }

    @Test
    public void givenPresentNonPersistentSession_whenUpdateClientSession_thenVerify() {
        SessionInfo sessionInfoNew = getSessionInfo("clientId1", ClientType.DEVICE, true);

        sessionClusterManager.updateClientSession(sessionInfoNew, getConnectionRequestInfo(),
                SessionInfo.withClientType(ClientType.APPLICATION));

        verify(msgPersistenceManager, times(2)).clearPersistedMessages(any());
        verify(applicationRemovedEventService, times(1)).sendApplicationRemovedEvent(any());
        verify(clientSessionService, times(1)).saveClientSession(any(), any(), any());
    }

    @Test
    public void givenPresentNonPersistentSessionSameClientType_whenUpdateClientSession_thenVerify() {
        SessionInfo sessionInfoNew = getSessionInfo("clientId1", ClientType.DEVICE, true);

        sessionClusterManager.updateClientSession(sessionInfoNew, getConnectionRequestInfo(),
                SessionInfo.withClientType(ClientType.DEVICE));

        verify(clientSessionService, times(1)).saveClientSession(any(), any(), any());
    }

    @Test
    public void givenPresentPersistentSession_whenProcessConnectionRequest_thenVerify() {
        SessionInfo sessionInfoNew = getSessionInfo("clientId1");
        SessionInfo sessionInfoOld = getSessionInfo("clientId2", ClientType.DEVICE, false);

        doReturn(getClientSession(true, sessionInfoOld)).when(clientSessionService).getClientSession(any());

        sessionClusterManager.processConnectionRequest(sessionInfoNew, getConnectionRequestInfo());

        verify(disconnectClientCommandService, times(1)).disconnectOnSessionConflict(any(), any(), any(), eq(true));
        verify(clientSessionService, times(2)).saveClientSession(any(), any(), any());
        verify(sessionClusterManager, times(1)).updateClientSession(any(), any(), any());
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
        SessionInfo sessionInfo = getSessionInfo("clientId", ClientType.DEVICE, false);
        doReturn(getClientSession(true, sessionInfo)).when(clientSessionService).getClientSession(any());

        sessionClusterManager.processRemoveApplicationTopicRequest("clientId", new ClientCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailure(Throwable e) {
            }
        });
        verify(applicationTopicService, times(1)).deleteTopic(any(), any());
    }

    @Test
    public void givenApplicationClientType_whenProcessRemoveApplicationTopicRequest_thenOk() {
        SessionInfo sessionInfo = getSessionInfo("clientId", ClientType.APPLICATION, false);
        doReturn(getClientSession(true, sessionInfo)).when(clientSessionService).getClientSession(any());

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
        SessionInfo sessionInfo = getSessionInfo("clientId");
        doReturn(getClientSession(true, sessionInfo)).when(clientSessionService).getClientSession(any());

        sessionClusterManager.processClearSession("clientId", UUID.randomUUID());

        verify(clientSessionService, never()).clearClientSession(any(), any());
        verify(clientSubscriptionService, never()).clearSubscriptionsAndPersist(any(), any());
        verify(msgPersistenceManager, never()).clearPersistedMessages(any());

        sessionClusterManager.processClearSession("clientId", sessionInfo.getSessionId());

        verify(clientSessionService, never()).clearClientSession(any(), any());
        verify(clientSubscriptionService, never()).clearSubscriptionsAndPersist(any(), any());
        verify(msgPersistenceManager, never()).clearPersistedMessages(any());
    }

    @Test
    public void givenDisconnectedSession_whenProcessClearSession_thenVerify() {
        Cache cache = mock(Cache.class);
        when(cacheNameResolver.getCache(anyString())).thenReturn(cache);

        SessionInfo sessionInfo = getSessionInfo("clientId");
        doReturn(getClientSession(false, sessionInfo)).when(clientSessionService).getClientSession(any());

        sessionClusterManager.processClearSession("clientId", sessionInfo.getSessionId());

        verify(clientSessionService, times(1)).clearClientSession(any(), any());
        verify(clientSubscriptionService, times(1)).clearSubscriptionsAndPersist(any(), any());
        verify(msgPersistenceManager, times(1)).clearPersistedMessages(any());
    }

    @Test
    public void givenClientSession_whenProcessMarkSessionDisconnected_thenOk() {
        SessionInfo sessionInfo = getSessionInfo("test", ClientType.APPLICATION, false);
        ClientSession clientSessionConnected = getClientSession(true, sessionInfo);

        ClientSession clientSessionDisconnected = sessionClusterManager.markSessionDisconnected(clientSessionConnected, -1);

        Assert.assertFalse(clientSessionDisconnected.isConnected());
        Assert.assertEquals("test", clientSessionDisconnected.getSessionInfo().getClientId());
        Assert.assertEquals(ClientType.APPLICATION, clientSessionDisconnected.getSessionInfo().getClientType());
        Assert.assertFalse(clientSessionDisconnected.getSessionInfo().isCleanStart());
        Assert.assertEquals(0, clientSessionDisconnected.getSessionInfo().getSessionExpiryInterval());
    }

    @Test
    public void givenClientSessionAndNewSessionsExpiryInterval_whenProcessMarkSessionDisconnected_thenOk() {
        SessionInfo sessionInfo = getSessionInfo("test", ClientType.APPLICATION, false);
        ClientSession clientSessionConnected = getClientSession(true, sessionInfo);

        ClientSession clientSessionDisconnected = sessionClusterManager.markSessionDisconnected(clientSessionConnected, 100);

        Assert.assertFalse(clientSessionDisconnected.isConnected());
        Assert.assertEquals("test", clientSessionDisconnected.getSessionInfo().getClientId());
        Assert.assertEquals(ClientType.APPLICATION, clientSessionDisconnected.getSessionInfo().getClientType());
        Assert.assertFalse(clientSessionDisconnected.getSessionInfo().isCleanStart());
        Assert.assertEquals(100, clientSessionDisconnected.getSessionInfo().getSessionExpiryInterval());
    }

    private SessionInfo getSessionInfo(String clientId) {
        return getSessionInfo(clientId, ClientType.DEVICE, true);
    }

    private SessionInfo getSessionInfo(String clientId, ClientType clientType, boolean cleanStart) {
        ClientInfo clientInfo = ClientSessionInfoFactory.getClientInfo(clientId, clientType);
        ConnectionInfo connectionInfo = ClientSessionInfoFactory.getConnectionInfo();
        return ClientSessionInfoFactory.getSessionInfo(cleanStart, "serviceId", clientInfo, connectionInfo);
    }

    private ConnectionRequestInfo getConnectionRequestInfo() {
        return new ConnectionRequestInfo(UUID.randomUUID(), System.currentTimeMillis(), "responseTopic");
    }

    private ClientSession getClientSession(boolean connected, SessionInfo sessionInfo) {
        return new ClientSession(connected, sessionInfo);
    }

}
