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
package org.thingsboard.mqtt.broker.actors.client.service.session;

import com.google.common.util.concurrent.Futures;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.cache.Cache;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.client.messages.ClientCallback;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionRequestInfo;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.ClearSessionMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.ConnectionRequestMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.SessionDisconnectedMsg;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.cache.TbCacheOps;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionEventResponseProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientSessionEventQueueFactory;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationRemovedEventService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationTopicService;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.thingsboard.mqtt.broker.cache.CacheConstants.CLIENT_MQTT_VERSION_CACHE;
import static org.thingsboard.mqtt.broker.cache.CacheConstants.CLIENT_SESSION_CREDENTIALS_CACHE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.SERVICE_ID_HEADER;

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
    RateLimitService rateLimitService;
    @MockitoBean
    TbCacheOps cacheOps;
    @MockitoBean
    TimeseriesService timeseriesService;

    @MockitoSpyBean
    SessionClusterManagerImpl sessionClusterManager;

    private final List<ClientSessionEventResponseProto> sentResponses = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() {
        sentResponses.clear();
        doAnswer(this::completeCallback).when(clientSessionService).saveClientSession(any(), any());
        doAnswer(this::completeCallback).when(clientSubscriptionService).clearSubscriptionsAndPersist(any(), any(BasicCallback.class));
        TbQueueProducer<TbProtoQueueMsg<ClientSessionEventResponseProto>> producer = new TbQueueProducer<>() {
            @Override
            public String getDefaultTopic() {
                return null;
            }

            @Override
            public void send(TbProtoQueueMsg<ClientSessionEventResponseProto> msg, TbQueueCallback callback) {
            }

            @Override
            public void send(String topic, Integer partition, TbProtoQueueMsg<ClientSessionEventResponseProto> msg, TbQueueCallback callback) {
                sentResponses.add(msg.getValue());
            }

            @Override
            public void stop() {
            }
        };
        doReturn(producer).when(clientSessionEventQueueFactory).createEventResponseProducer(any());
        when(timeseriesService.removeAllLatestForClient(anyString())).thenReturn(Futures.immediateFuture(null));
        when(serviceInfoProvider.getServiceId()).thenReturn("svc");

        sessionClusterManager.init();
    }

    // -------------------------
    // processConnectionRequest
    // -------------------------

    @Test
    public void processConnectionRequest_requestTimeout_doesNotUpdate() {
        ClientSessionInfo existing = ClientSessionInfoFactory.getClientSessionInfo("clientId");
        SessionInfo incoming = ClientSessionInfoFactory.clientSessionInfoToSessionInfo(existing);
        doReturn(existing).when(clientSessionService).getClientSessionInfo("clientId");

        ConnectionRequestMsg connectionRequestMsg = new ConnectionRequestMsg(noopCallback(), incoming, expiredReq());
        sessionClusterManager.processConnectionRequest(connectionRequestMsg);

        verify(sessionClusterManager, never()).updateClientSessionOnConnect(any(), any(), any());
        verify(disconnectClientCommandService, never()).disconnectOnSessionConflict(any(), any(), any(), anyBoolean());
        verify(clientSessionService, never()).getClientSessionInfo("clientId");
    }

    @Test
    public void processConnectionRequest_sameSessionId_doesNotUpdate() {
        ClientSessionInfo existing = ClientSessionInfoFactory.getClientSessionInfo("clientId");
        SessionInfo incoming = ClientSessionInfoFactory.clientSessionInfoToSessionInfo(existing);
        doReturn(existing).when(clientSessionService).getClientSessionInfo("clientId");

        ConnectionRequestMsg connectionRequestMsg = new ConnectionRequestMsg(noopCallback(), incoming, req());
        sessionClusterManager.processConnectionRequest(connectionRequestMsg);

        verify(sessionClusterManager, never()).updateClientSessionOnConnect(any(), any(), any());
        verify(disconnectClientCommandService, never()).disconnectOnSessionConflict(any(), any(), any(), anyBoolean());
    }

    @Test
    public void processConnectionRequest_conflictingConnectedSession_disconnectsAndUpdates() {
        givenCache();
        SessionInfo incoming = deviceSession("clientA", true);

        ClientSessionInfo currentConnected = ClientSessionInfoFactory.getClientSessionInfo(true, "clientA", ClientType.DEVICE, false);
        doReturn(currentConnected).when(clientSessionService).getClientSessionInfo("clientA");
        when(rateLimitService.checkSessionsLimit("clientA", currentConnected)).thenReturn(true);
        when(rateLimitService.checkApplicationClientsLimit(incoming, currentConnected)).thenReturn(true);

        ConnectionRequestMsg connectionRequestMsg = new ConnectionRequestMsg(noopCallback(), incoming, req());
        sessionClusterManager.processConnectionRequest(connectionRequestMsg);

        verify(disconnectClientCommandService).disconnectOnSessionConflict(eq(currentConnected.getServiceId()),
                eq("clientA"), eq(currentConnected.getSessionId()), eq(true));
        verify(sessionClusterManager).updateClientSessionOnConnect(eq(incoming), any(), any());
        verify(cacheOps, times(2)).put(anyString(), anyString(), anyString());
    }

    @Test
    public void processConnectionRequest_sessionQuotaExceeded_doesNotAllowConnection() {
        SessionInfo incoming = deviceSession("clientA", true);

        when(rateLimitService.checkSessionsLimit(anyString(), any())).thenReturn(false);

        ConnectionRequestMsg connectionRequestMsg = new ConnectionRequestMsg(noopCallback(), incoming, req());
        sessionClusterManager.processConnectionRequest(connectionRequestMsg);

        verify(disconnectClientCommandService, never()).disconnectOnSessionConflict(anyString(), anyString(), any(), anyBoolean());
        verify(sessionClusterManager, never()).updateClientSessionOnConnect(any(), any(), any());
    }

    @Test
    public void processConnectionRequest_appClientsQuotaExceeded_doesNotAllowConnection() {
        ClientSessionInfo clientSessionInfo = ClientSessionInfoFactory.getClientSessionInfo("app", ClientType.APPLICATION, false);
        SessionInfo incoming = ClientSessionInfoFactory.clientSessionInfoToSessionInfo(clientSessionInfo);

        when(rateLimitService.checkSessionsLimit(anyString(), any())).thenReturn(true);
        when(rateLimitService.checkApplicationClientsLimit(any(), any())).thenReturn(false);

        ConnectionRequestMsg connectionRequestMsg = new ConnectionRequestMsg(noopCallback(), incoming, req());
        sessionClusterManager.processConnectionRequest(connectionRequestMsg);

        verify(disconnectClientCommandService, never()).disconnectOnSessionConflict(anyString(), anyString(), any(), anyBoolean());
        verify(sessionClusterManager, never()).updateClientSessionOnConnect(any(), any(), any());
    }

    // -------------------------
    // updateClientSessionOnConnect
    // -------------------------

    @Test
    public void updateClientSessionOnConnect_firstSession_savesOnly() {
        SessionInfo incoming = deviceSession("c1", true);

        sessionClusterManager.updateClientSessionOnConnect(incoming, req(), null);

        verify(clientSessionService).saveClientSession(any(), any());
        verify(clientSubscriptionService, never()).clearSubscriptionsAndPersist(any(), any());
        verify(msgPersistenceManager, never()).clearPersistedMessages(any(), any());
        verify(applicationRemovedEventService, never()).sendApplicationRemovedEvent(any());
    }

    @Test
    public void updateClientSessionOnConnect_cleanStart_clearsSubsAndMsgs_andSavesSession() {
        SessionInfo incoming = deviceSession("c1", true);
        ClientSessionInfo previous = ClientSessionInfoFactory.getClientSessionInfo("c1", ClientType.DEVICE, false);

        sessionClusterManager.updateClientSessionOnConnect(incoming, req(), previous);

        verify(clientSubscriptionService).clearSubscriptionsAndPersist(eq("c1"), any());
        verify(msgPersistenceManager).clearPersistedMessages(eq("c1"), eq(ClientType.DEVICE));
        verify(clientSessionService).saveClientSession(any(), any());
    }

    @Test
    public void updateClientSessionOnConnect_appRemoved_appToDevice_clearsMsgs_sendsEvent_andDecrementsIfPersistentAppClient() {
        SessionInfo incomingDevice = deviceSession("appId", false);
        ClientSessionInfo previousApp = ClientSessionInfo.withClientType(ClientType.APPLICATION)
                .toBuilder().clientId("appId").sessionExpiryInterval(100).disconnectedAt(System.currentTimeMillis()).build();

        sessionClusterManager.updateClientSessionOnConnect(incomingDevice, req(), previousApp);

        verify(rateLimitService).decrementApplicationClientsCount();
        verify(msgPersistenceManager).clearPersistedMessages(eq("appId"), eq(ClientType.DEVICE));
        verify(applicationRemovedEventService).sendApplicationRemovedEvent(eq("appId"));
        verify(clientSessionService).saveClientSession(any(), any());
        // no Clean Start => no subscriptions clearing
        verify(clientSubscriptionService, never()).clearSubscriptionsAndPersist(eq("appId"), any());
    }

    @Test
    public void updateClientSessionOnConnect_noCleanStart_sameType_onlySavesSession() {
        SessionInfo incoming = deviceSession("c2", false);
        ClientSessionInfo previous = ClientSessionInfoFactory.getClientSessionInfo("c2", ClientType.DEVICE, false);

        sessionClusterManager.updateClientSessionOnConnect(incoming, req(), previous);

        verify(clientSessionService).saveClientSession(any(), any());
        verify(clientSubscriptionService, never()).clearSubscriptionsAndPersist(any(), any());
        verify(msgPersistenceManager, never()).clearPersistedMessages(any(), any());
        verify(applicationRemovedEventService, never()).sendApplicationRemovedEvent(any());
    }

    @Test
    public void updateClientSessionOnConnect_noCleanStart_previousSessionExpired_clearsSubsAndMsgs_andSavesSession() {
        SessionInfo incoming = deviceSession("c3", false);
        ClientSessionInfo previous = disconnectedSession("c3", ClientType.DEVICE, true, 3, TimeUnit.SECONDS.toMillis(16));

        sessionClusterManager.updateClientSessionOnConnect(incoming, req(), previous);

        verify(clientSubscriptionService).clearSubscriptionsAndPersist(eq("c3"), any());
        verify(msgPersistenceManager).clearPersistedMessages(eq("c3"), eq(ClientType.DEVICE));
        verify(clientSessionService).saveClientSession(any(), any());
        assertConnectResponse(false);
    }

    @Test
    public void updateClientSessionOnConnect_noCleanStart_previousSessionNotExpired_onlySavesSession() {
        SessionInfo incoming = deviceSession("c4", false);
        ClientSessionInfo previous = disconnectedSession("c4", ClientType.DEVICE, true, 30, TimeUnit.SECONDS.toMillis(1));

        sessionClusterManager.updateClientSessionOnConnect(incoming, req(), previous);

        verify(clientSessionService).saveClientSession(any(), any());
        verify(clientSubscriptionService, never()).clearSubscriptionsAndPersist(any(), any());
        verify(msgPersistenceManager, never()).clearPersistedMessages(any(), any());
        assertConnectResponse(true);
    }

    @Test
    public void updateClientSessionOnConnect_noCleanStart_previousMqtt3NotCleanSession_neverExpires_onlySavesSession() {
        SessionInfo incoming = deviceSession("c5", false);
        ClientSessionInfo previous = disconnectedSession("c5", ClientType.DEVICE, false, 0, TimeUnit.DAYS.toMillis(30));

        sessionClusterManager.updateClientSessionOnConnect(incoming, req(), previous);

        verify(clientSessionService).saveClientSession(any(), any());
        verify(clientSubscriptionService, never()).clearSubscriptionsAndPersist(any(), any());
        verify(msgPersistenceManager, never()).clearPersistedMessages(any(), any());
        assertConnectResponse(true);
    }

    @Test
    public void updateClientSessionOnConnect_expiredPersistentAppToPersistentApp_doesNotDecrementAppClientsCount() {
        // checkApplicationClientsLimit carries the slot over (no increment) for app -> app, so no decrement either
        SessionInfo incoming = persistentAppSession("app1", false);
        ClientSessionInfo previous = disconnectedSession("app1", ClientType.APPLICATION, true, 3, TimeUnit.SECONDS.toMillis(16));

        sessionClusterManager.updateClientSessionOnConnect(incoming, req(), previous);

        verify(rateLimitService, never()).decrementApplicationClientsCount();
        verify(clientSubscriptionService).clearSubscriptionsAndPersist(eq("app1"), any());
        verify(msgPersistenceManager).clearPersistedMessages(eq("app1"), eq(ClientType.APPLICATION));
        assertConnectResponse(false);
    }

    @Test
    public void updateClientSessionOnConnect_cleanStartPersistentAppToPersistentApp_doesNotDecrementAppClientsCount() {
        SessionInfo incoming = persistentAppSession("app2", true);
        ClientSessionInfo previous = disconnectedSession("app2", ClientType.APPLICATION, true, 100, 0);

        sessionClusterManager.updateClientSessionOnConnect(incoming, req(), previous);

        verify(rateLimitService, never()).decrementApplicationClientsCount();
    }

    @Test
    public void updateClientSessionOnConnect_cleanStartPersistentAppToNonPersistentApp_decrementsAppClientsCount() {
        SessionInfo incoming = persistentAppSession("app3", true).toBuilder().sessionExpiryInterval(0).build();
        ClientSessionInfo previous = disconnectedSession("app3", ClientType.APPLICATION, true, 100, 0);

        sessionClusterManager.updateClientSessionOnConnect(incoming, req(), previous);

        verify(rateLimitService).decrementApplicationClientsCount();
    }

    // -------------------------
    // isRequestTimedOut
    // -------------------------

    @Test
    public void isRequestTimedOut_behaviour() {
        Assert.assertTrue(sessionClusterManager.isRequestTimedOut(1));
        Assert.assertFalse(sessionClusterManager.isRequestTimedOut(System.currentTimeMillis()));
    }

    // -------------------------
    // processRemoveApplicationTopicRequest
    // -------------------------

    @Test
    public void processRemoveApplicationTopicRequest_deletesOnlyForDeviceSession() {
        // DEVICE -> delete
        doReturn(ClientSessionInfoFactory.getClientSessionInfo("clientId", ClientType.DEVICE, false))
                .when(clientSessionService).getClientSessionInfo("clientId");
        sessionClusterManager.processRemoveApplicationTopicRequest("clientId", noopCallback());
        verify(applicationTopicService).deleteTopic(eq("clientId"), any());

        // APPLICATION -> skip
        doReturn(ClientSessionInfoFactory.getClientSessionInfo("clientId", ClientType.APPLICATION, false))
                .when(clientSessionService).getClientSessionInfo("clientId");
        sessionClusterManager.processRemoveApplicationTopicRequest("clientId", noopCallback());
        verify(applicationTopicService).deleteTopic(eq("clientId"), any());
    }

    // -------------------------
    // processClearSession
    // -------------------------

    @Test
    public void processClearSession_sessionMissing_clearsLeftoverSubscriptionsOnly() {
        doReturn(null).when(clientSessionService).getClientSessionInfo("c3");
        when(clientSubscriptionService.getClientSubscriptions("c3")).thenReturn(Set.of(mock(TopicSubscription.class)));

        ClearSessionMsg msg = new ClearSessionMsg(noopCallback(), UUID.randomUUID());
        sessionClusterManager.processClearSession("c3", msg);

        verify(clientSubscriptionService).clearSubscriptionsAndPersist(eq("c3"), eq(null));
        verify(clientSessionService, never()).clearClientSession(any(), any());
        verify(msgPersistenceManager, never()).clearPersistedMessages(any(), any());
    }

    @Test
    public void processClearSession_sessionIdMismatchOrConnected_doesNothing() {
        ClientSessionInfo session = ClientSessionInfoFactory.getClientSessionInfo("c4");
        doReturn(session).when(clientSessionService).getClientSessionInfo("c4");

        // mismatch
        ClearSessionMsg msg = new ClearSessionMsg(noopCallback(), UUID.randomUUID());
        sessionClusterManager.processClearSession("c4", msg);

        // connected with matching id -> still does nothing
        msg = new ClearSessionMsg(noopCallback(), session.getSessionId());
        sessionClusterManager.processClearSession("c4", msg);

        verify(clientSessionService, never()).clearClientSession(any(), any());
        verify(clientSubscriptionService, never()).clearSubscriptionsAndPersist(any(), any());
        verify(msgPersistenceManager, never()).clearPersistedMessages(any(), any());
    }

    @Test
    public void processClearSession_disconnectedMatching_clearsEverything() {
        givenCache();
        ClientSessionInfo disconnected = ClientSessionInfoFactory.getClientSessionInfo(false, "c5", ClientType.DEVICE, false);
        doReturn(disconnected).when(clientSessionService).getClientSessionInfo("c5");

        ClearSessionMsg msg = new ClearSessionMsg(noopCallback(), disconnected.getSessionId());
        sessionClusterManager.processClearSession("c5", msg);

        verify(clientSessionService).clearClientSession(eq("c5"), eq(null));
        verify(clientSubscriptionService).clearSubscriptionsAndPersist(eq("c5"), eq(null));
        verify(msgPersistenceManager).clearPersistedMessages(eq("c5"), eq(disconnected.getType()));
        verify(cacheOps).evictIfPresentSafe(eq(CLIENT_SESSION_CREDENTIALS_CACHE), eq("c5"));
        verify(cacheOps).evictIfPresentSafe(eq(CLIENT_MQTT_VERSION_CACHE), eq("c5"));
    }

    // -------------------------
    // markSessionDisconnected
    // -------------------------

    @Test
    public void markSessionDisconnected_keepsOrOverridesExpiryInterval() {
        ClientSessionInfo connected = ClientSessionInfoFactory.getClientSessionInfo().toBuilder().sessionExpiryInterval(10).build();

        ClientSessionInfo keep = sessionClusterManager.markSessionDisconnected(connected, -1);
        Assert.assertFalse(keep.isConnected());
        Assert.assertEquals(10, keep.getSessionExpiryInterval());

        ClientSessionInfo override = sessionClusterManager.markSessionDisconnected(connected, 100);
        Assert.assertFalse(override.isConnected());
        Assert.assertEquals(100, override.getSessionExpiryInterval());
    }

    // -------------------------
    // processSessionDisconnected
    // -------------------------

    @Test
    public void givenNoSession_whenProcessSessionDisconnected_thenDoNothing() {
        String clientId = "c1";
        UUID eventSessionId = UUID.randomUUID();
        SessionDisconnectedMsg msg = new SessionDisconnectedMsg(noopCallback(), eventSessionId, 10);

        doReturn(null).when(clientSessionService).getClientSessionInfo(clientId);

        sessionClusterManager.processSessionDisconnected(clientId, msg);

        verify(sessionClusterManager, never()).finishDisconnect(any(), any());
        verifyNoInteractions(clientSubscriptionService, msgPersistenceManager, cacheOps);
    }

    @Test
    public void givenSessionWithDifferentSessionId_whenProcessSessionDisconnected_thenDoNothing() {
        String clientId = "c2";

        ClientSessionInfo stored = ClientSessionInfo.withClientType(ClientType.DEVICE)
                .toBuilder()
                .clientId(clientId)
                .sessionId(UUID.randomUUID())
                .connected(true)
                .build();

        SessionDisconnectedMsg msg = new SessionDisconnectedMsg(noopCallback(), UUID.randomUUID(), 10);

        doReturn(stored).when(clientSessionService).getClientSessionInfo(clientId);

        sessionClusterManager.processSessionDisconnected(clientId, msg);

        verify(sessionClusterManager, never()).finishDisconnect(any(), any());
        verifyNoInteractions(clientSubscriptionService, msgPersistenceManager, cacheOps);
    }

    @Test
    public void givenSessionAlreadyDisconnected_whenProcessSessionDisconnected_thenDoNothing() {
        String clientId = "c3";
        UUID sid = UUID.randomUUID();

        ClientSessionInfo stored = ClientSessionInfo.withClientType(ClientType.DEVICE)
                .toBuilder()
                .clientId(clientId)
                .sessionId(sid)
                .connected(false)
                .build();

        SessionDisconnectedMsg msg = new SessionDisconnectedMsg(noopCallback(), sid, 10);

        doReturn(stored).when(clientSessionService).getClientSessionInfo(clientId);

        sessionClusterManager.processSessionDisconnected(clientId, msg);

        verify(sessionClusterManager, never()).finishDisconnect(any(), any());
        verifyNoInteractions(clientSubscriptionService, msgPersistenceManager, cacheOps);
    }

    @Test
    public void givenConnectedSessionAndSameSessionId_whenProcessSessionDisconnected_thenFinishDisconnectCalledWithExpiry() {
        String clientId = "c4";
        UUID sid = UUID.randomUUID();
        int expiry = 123;

        ClientSessionInfo stored = ClientSessionInfo.withClientType(ClientType.DEVICE)
                .toBuilder()
                .clientId(clientId)
                .sessionId(sid)
                .connected(true)
                .build();

        SessionDisconnectedMsg msg = new SessionDisconnectedMsg(noopCallback(), sid, expiry);

        doReturn(stored).when(clientSessionService).getClientSessionInfo(clientId);

        sessionClusterManager.processSessionDisconnected(clientId, msg);

        verify(sessionClusterManager).finishDisconnect(eq(stored), eq(msg));
    }

    // -------------------------
    // helpers
    // -------------------------

    private void givenCache() {
        Cache cache = mock(Cache.class);
        when(cacheOps.cache(anyString())).thenReturn(cache);
    }

    private Object completeCallback(InvocationOnMock inv) {
        BasicCallback callback = inv.getArgument(1);
        if (callback != null) {
            callback.onSuccess();
        }
        return null;
    }

    private void assertConnectResponse(boolean sessionPresent) {
        await().atMost(2, TimeUnit.SECONDS).until(() -> !sentResponses.isEmpty());
        ClientSessionEventResponseProto response = sentResponses.get(0);
        Assert.assertTrue(response.getSuccess());
        Assert.assertEquals(sessionPresent, response.getSessionPresent());
    }

    private SessionInfo persistentAppSession(String clientId, boolean cleanStart) {
        ClientInfo clientInfo = ClientSessionInfoFactory.getClientInfo(clientId, ClientType.APPLICATION);
        return ClientSessionInfoFactory.getSessionInfo(cleanStart, SERVICE_ID_HEADER, clientInfo)
                .toBuilder().sessionExpiryInterval(100).build();
    }

    private ClientSessionInfo disconnectedSession(String clientId, ClientType type, boolean cleanStart, int expiry, long disconnectedAgoMs) {
        return ClientSessionInfoFactory.getClientSessionInfo(clientId, type, cleanStart)
                .toBuilder()
                .connected(false)
                .sessionExpiryInterval(expiry)
                .disconnectedAt(System.currentTimeMillis() - disconnectedAgoMs)
                .build();
    }

    private SessionInfo deviceSession(String clientId, boolean cleanStart) {
        ClientInfo clientInfo = ClientSessionInfoFactory.getClientInfo(clientId, ClientType.DEVICE);
        return ClientSessionInfoFactory.getSessionInfo(cleanStart, SERVICE_ID_HEADER, clientInfo);
    }

    private ConnectionRequestInfo expiredReq() {
        return new ConnectionRequestInfo(UUID.randomUUID(), 0, "responseTopic");
    }

    private ConnectionRequestInfo req() {
        return new ConnectionRequestInfo(UUID.randomUUID(), System.currentTimeMillis(), "responseTopic");
    }

    private ClientCallback noopCallback() {
        return new ClientCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailure(Throwable e) {
            }
        };
    }
}
