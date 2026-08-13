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
package org.thingsboard.mqtt.broker.service.mqtt.persistence;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.common.DefaultTbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.limits.ThroughputQuotaService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationMsgQueuePublisher;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationPersistenceProcessor;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.DevicePersistenceProcessor;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.queue.DeviceMsgQueuePublisher;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.integration.IntegrationMsgQueuePublisher;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgWithId;
import org.thingsboard.mqtt.broker.service.processing.data.PersistentMsgSubscriptions;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = MsgPersistenceManagerImpl.class)
public class MsgPersistenceManagerImplTest {

    static final String SERVICE_ID = "serviceId";

    ClientSessionCtx ctx;
    SessionInfo sessionInfo;
    ClientInfo clientInfo;

    @MockitoBean
    GenericClientSessionCtxManager genericClientSessionCtxManager;
    @MockitoBean
    ApplicationMsgQueuePublisher applicationMsgQueuePublisher;
    @MockitoBean
    ApplicationPersistenceProcessor applicationPersistenceProcessor;
    @MockitoBean
    DeviceMsgQueuePublisher deviceMsgQueuePublisher;
    @MockitoBean
    DevicePersistenceProcessor devicePersistenceProcessor;
    @MockitoBean
    ClientLogger clientLogger;
    @MockitoBean
    RateLimitService rateLimitService;
    @MockitoBean
    ThroughputQuotaService throughputQuotaService;
    @MockitoBean
    IntegrationMsgQueuePublisher integrationMsgQueuePublisher;
    @MockitoBean
    TbMessageStatsReportClient tbMessageStatsReportClient;
    @MockitoSpyBean
    MsgPersistenceManagerImpl msgPersistenceManager;

    @Before
    public void setUp() throws Exception {
        ctx = mock(ClientSessionCtx.class);
        sessionInfo = mock(SessionInfo.class);
        clientInfo = mock(ClientInfo.class);
        // grant every charge in full unless a test says otherwise: a bare mock would return 0 and silently
        // truncate every fan-out, making unrelated tests fail (or pass) for the wrong reason
        lenient().when(throughputQuotaService.tryConsumeOutgoingBlocking(anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void testProcessPublish() {
        when(rateLimitService.isDevicePersistedMsgsLimitEnabled()).thenReturn(false);

        PublishMsgProto publishMsgProto = PublishMsgProto.getDefaultInstance();
        PublishMsgWithId publishMsgWithId = new PublishMsgWithId(UUID.randomUUID(), publishMsgProto, new DefaultTbQueueMsgHeaders());
        PersistentMsgSubscriptions persistentMsgSubscriptions = new PersistentMsgSubscriptions(
                false,
                List.of(
                        createSubscription("topic1", 1, "devClientId1", ClientType.DEVICE),
                        createSubscription("topic2", 2, "devClientId2", ClientType.DEVICE)
                ),
                List.of(
                        createSubscription("topic3", 1, "appClientId3", ClientType.APPLICATION),
                        createSubscription("topic4", 2, "appClientId4", ClientType.APPLICATION)
                ),
                Collections.emptySet(),
                Collections.emptyList()
        );

        msgPersistenceManager.processPublish(publishMsgWithId, persistentMsgSubscriptions, null);

        ArgumentCaptor<String> deviceMsgQueuePublisherCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> applicationMsgQueuePublisherCaptor = ArgumentCaptor.forClass(String.class);

        verify(deviceMsgQueuePublisher, times(2)).sendMsg(
                deviceMsgQueuePublisherCaptor.capture(), any(), any());
        verify(applicationMsgQueuePublisher, times(2)).sendMsg(
                applicationMsgQueuePublisherCaptor.capture(), any(), any());

        String lastDeviceClientId = deviceMsgQueuePublisherCaptor.getValue();
        assertEquals("devClientId2", lastDeviceClientId);

        String lastApplicationClientId = applicationMsgQueuePublisherCaptor.getValue();
        assertEquals("appClientId4", lastApplicationClientId);
    }

    @Test
    public void given1TokenAvailable_whenApplyDevicePersistedMsgsLimit_thenAdmit1AndSettleTheRest() {
        PublishMsgCallback callbackWrapper = mock(PublishMsgCallback.class);

        List<Subscription> subscriptions = List.of(
                createSubscription("tf1", 1, "client1", ClientType.DEVICE),
                createSubscription("tf2", 2, "client2", ClientType.DEVICE),
                createSubscription("tf3", 1, "client3", ClientType.DEVICE)
        );

        when(rateLimitService.tryConsumeDevicePersistedMsgs(anyLong())).thenReturn(1L);

        List<Subscription> admitted = msgPersistenceManager.applyDevicePersistedMsgsLimit(subscriptions, callbackWrapper);

        assertEquals(subscriptions.subList(0, 1), admitted);
        verify(callbackWrapper).onBatchSuccess(eq(2));
        verify(tbMessageStatsReportClient).reportDroppedMsgs(eq(2));
    }

    @Test
    public void given0TokenAvailable_whenApplyDevicePersistedMsgsLimit_thenAdmitNothingAndSettleAll() {
        PublishMsgCallback callbackWrapper = mock(PublishMsgCallback.class);

        List<Subscription> subscriptions = List.of(
                createSubscription("tf1", 1, "client1", ClientType.DEVICE),
                createSubscription("tf2", 2, "client2", ClientType.DEVICE),
                createSubscription("tf3", 1, "client3", ClientType.DEVICE)
        );

        when(rateLimitService.tryConsumeDevicePersistedMsgs(anyLong())).thenReturn(0L);

        List<Subscription> admitted = msgPersistenceManager.applyDevicePersistedMsgsLimit(subscriptions, callbackWrapper);

        assertEquals(List.of(), admitted);
        verify(callbackWrapper).onBatchSuccess(eq(3));
        verify(tbMessageStatsReportClient).reportDroppedMsgs(eq(3));
    }

    @Test
    public void givenAllTokensAvailable_whenApplyDevicePersistedMsgsLimit_thenAdmitAllAndSettleNothing() {
        PublishMsgCallback callbackWrapper = mock(PublishMsgCallback.class);

        List<Subscription> subscriptions = List.of(
                createSubscription("tf1", 1, "client1", ClientType.DEVICE),
                createSubscription("tf2", 2, "client2", ClientType.DEVICE),
                createSubscription("tf3", 1, "client3", ClientType.DEVICE)
        );

        when(rateLimitService.tryConsumeDevicePersistedMsgs(anyLong())).thenReturn(3L);

        List<Subscription> admitted = msgPersistenceManager.applyDevicePersistedMsgsLimit(subscriptions, callbackWrapper);

        assertEquals(subscriptions, admitted);
        verify(callbackWrapper, never()).onSuccess();
        verify(callbackWrapper, never()).onBatchSuccess(anyInt());
        verify(tbMessageStatsReportClient, never()).reportDroppedMsgs(anyInt());
    }

    @Test
    public void givenIntegrationSubscriptions_whenQuotaGrantsAll_thenSendAllAndReportNoDrops() {
        PublishMsgCallback callbackWrapper = mock(PublishMsgCallback.class);

        List<Subscription> subscriptions = List.of(
                createSubscription("tf1", 1, "int1", ClientType.INTEGRATION),
                createSubscription("tf2", 2, "int2", ClientType.INTEGRATION),
                createSubscription("tf3", 1, "int3", ClientType.INTEGRATION)
        );
        PublishMsgProto publishMsgProto = PublishMsgProto.getDefaultInstance();
        PublishMsgWithId publishMsgWithId = new PublishMsgWithId(UUID.randomUUID(), publishMsgProto, new DefaultTbQueueMsgHeaders());

        when(throughputQuotaService.tryConsumeOutgoingBlocking(3)).thenReturn(3);

        msgPersistenceManager.processIntegrationSubscriptionsWithThroughputQuota(subscriptions, publishMsgWithId, callbackWrapper);

        verify(integrationMsgQueuePublisher, times(3)).sendMsg(any(), any(), any());
        verify(callbackWrapper, never()).onSuccess();
        verify(tbMessageStatsReportClient, never()).reportDroppedMsgs(anyInt());
    }

    @Test
    public void givenIntegrationSubscriptions_whenQuotaGrantsPartially_thenSendGrantedPrefixAndSettleRest() {
        PublishMsgCallback callbackWrapper = mock(PublishMsgCallback.class);

        List<Subscription> subscriptions = List.of(
                createSubscription("tf1", 1, "int1", ClientType.INTEGRATION),
                createSubscription("tf2", 2, "int2", ClientType.INTEGRATION),
                createSubscription("tf3", 1, "int3", ClientType.INTEGRATION)
        );
        PublishMsgProto publishMsgProto = PublishMsgProto.getDefaultInstance();
        PublishMsgWithId publishMsgWithId = new PublishMsgWithId(UUID.randomUUID(), publishMsgProto, new DefaultTbQueueMsgHeaders());

        when(throughputQuotaService.tryConsumeOutgoingBlocking(3)).thenReturn(1);

        msgPersistenceManager.processIntegrationSubscriptionsWithThroughputQuota(subscriptions, publishMsgWithId, callbackWrapper);

        ArgumentCaptor<String> clientIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(integrationMsgQueuePublisher, times(1)).sendMsg(clientIdCaptor.capture(), any(), any());
        assertEquals("int1", clientIdCaptor.getValue());
        verify(callbackWrapper).onBatchSuccess(eq(2));
        verify(tbMessageStatsReportClient).reportDroppedMsgs(eq(2));
    }

    @Test
    public void givenIntegrationSubscriptions_whenQuotaGrantsNothing_thenDropAll() {
        PublishMsgCallback callbackWrapper = mock(PublishMsgCallback.class);

        List<Subscription> subscriptions = List.of(
                createSubscription("tf1", 1, "int1", ClientType.INTEGRATION),
                createSubscription("tf2", 2, "int2", ClientType.INTEGRATION),
                createSubscription("tf3", 1, "int3", ClientType.INTEGRATION)
        );
        PublishMsgProto publishMsgProto = PublishMsgProto.getDefaultInstance();
        PublishMsgWithId publishMsgWithId = new PublishMsgWithId(UUID.randomUUID(), publishMsgProto, new DefaultTbQueueMsgHeaders());

        when(throughputQuotaService.tryConsumeOutgoingBlocking(3)).thenReturn(0);

        msgPersistenceManager.processIntegrationSubscriptionsWithThroughputQuota(subscriptions, publishMsgWithId, callbackWrapper);

        verify(integrationMsgQueuePublisher, never()).sendMsg(any(), any(), any());
        verify(callbackWrapper).onBatchSuccess(eq(3));
        verify(tbMessageStatsReportClient).reportDroppedMsgs(eq(3));
    }

    @Test
    public void givenIntegrationSubscriptions_whenProcessPublish_thenChargeQuotaAndSend() {
        when(rateLimitService.isDevicePersistedMsgsLimitEnabled()).thenReturn(false);
        when(throughputQuotaService.tryConsumeOutgoingBlocking(2)).thenReturn(2);

        PublishMsgProto publishMsgProto = PublishMsgProto.getDefaultInstance();
        PublishMsgWithId publishMsgWithId = new PublishMsgWithId(UUID.randomUUID(), publishMsgProto, new DefaultTbQueueMsgHeaders());
        PersistentMsgSubscriptions persistentMsgSubscriptions = new PersistentMsgSubscriptions(
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptySet(),
                List.of(
                        createSubscription("tf1", 1, "int1", ClientType.INTEGRATION),
                        createSubscription("tf2", 2, "int2", ClientType.INTEGRATION)
                )
        );

        msgPersistenceManager.processPublish(publishMsgWithId, persistentMsgSubscriptions, mock(PublishMsgCallback.class));

        verify(throughputQuotaService).tryConsumeOutgoingBlocking(eq(2));
        verify(integrationMsgQueuePublisher, times(2)).sendMsg(any(), any(), any());
    }

    @Test
    public void givenDeviceLimitTruncates_whenProcessPublish_thenQuotaIsChargedForSurvivorsOnly() {
        when(rateLimitService.isDevicePersistedMsgsLimitEnabled()).thenReturn(true);
        when(rateLimitService.tryConsumeDevicePersistedMsgs(3)).thenReturn(2L);

        PublishMsgWithId publishMsgWithId = new PublishMsgWithId(
                UUID.randomUUID(), PublishMsgProto.getDefaultInstance(), new DefaultTbQueueMsgHeaders());
        PersistentMsgSubscriptions subscriptions = new PersistentMsgSubscriptions(
                false,
                List.of(
                        createSubscription("topic1", 1, "devClientId1", ClientType.DEVICE),
                        createSubscription("topic2", 1, "devClientId2", ClientType.DEVICE),
                        createSubscription("topic3", 1, "devClientId3", ClientType.DEVICE)
                ),
                Collections.emptyList(),
                Collections.emptySet(),
                Collections.emptyList()
        );

        msgPersistenceManager.processPublish(publishMsgWithId, subscriptions, mock(PublishMsgCallback.class));

        // 2 survived the device limit, so the quota must be charged 2 - never the original 3
        verify(throughputQuotaService).tryConsumeOutgoingBlocking(2);
        verify(throughputQuotaService, never()).tryConsumeOutgoingBlocking(3);
        verify(deviceMsgQueuePublisher, times(2)).sendMsg(any(), any(), any());
    }

    @Test
    public void givenDeviceLimitDropsAll_whenProcessPublish_thenQuotaIsNotChargedAtAll() {
        when(rateLimitService.isDevicePersistedMsgsLimitEnabled()).thenReturn(true);
        when(rateLimitService.tryConsumeDevicePersistedMsgs(2)).thenReturn(0L);

        PublishMsgWithId publishMsgWithId = new PublishMsgWithId(
                UUID.randomUUID(), PublishMsgProto.getDefaultInstance(), new DefaultTbQueueMsgHeaders());
        PersistentMsgSubscriptions subscriptions = new PersistentMsgSubscriptions(
                false,
                List.of(
                        createSubscription("topic1", 1, "devClientId1", ClientType.DEVICE),
                        createSubscription("topic2", 1, "devClientId2", ClientType.DEVICE)
                ),
                Collections.emptyList(),
                Collections.emptySet(),
                Collections.emptyList()
        );

        msgPersistenceManager.processPublish(publishMsgWithId, subscriptions, mock(PublishMsgCallback.class));

        verify(throughputQuotaService, never()).tryConsumeOutgoingBlocking(anyInt());
        verify(deviceMsgQueuePublisher, never()).sendMsg(any(), any(), any());
        verify(tbMessageStatsReportClient).reportDroppedMsgs(2);
    }

    @Test
    public void givenPartialQuotaGrant_whenProcessPublish_thenPersistsOnlyGrantedDeviceSubscriptions() {
        when(rateLimitService.isDevicePersistedMsgsLimitEnabled()).thenReturn(false);
        when(throughputQuotaService.tryConsumeOutgoingBlocking(2)).thenReturn(1);

        PublishMsgWithId publishMsgWithId = new PublishMsgWithId(
                UUID.randomUUID(), PublishMsgProto.getDefaultInstance(), new DefaultTbQueueMsgHeaders());
        PersistentMsgSubscriptions subscriptions = new PersistentMsgSubscriptions(
                false,
                List.of(
                        createSubscription("topic1", 1, "devClientId1", ClientType.DEVICE),
                        createSubscription("topic2", 1, "devClientId2", ClientType.DEVICE)
                ),
                Collections.emptyList(),
                Collections.emptySet(),
                Collections.emptyList()
        );

        msgPersistenceManager.processPublish(publishMsgWithId, subscriptions, mock(PublishMsgCallback.class));

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(deviceMsgQueuePublisher, times(1)).sendMsg(sent.capture(), any(), any());
        assertEquals(List.of("devClientId1"), sent.getAllValues());
        verify(tbMessageStatsReportClient).reportDroppedMsgs(1);
    }

    @Test
    public void givenPartialQuotaGrant_whenProcessPublish_thenPersistsOnlyGrantedAppSubscriptions() {
        when(rateLimitService.isDevicePersistedMsgsLimitEnabled()).thenReturn(false);
        when(throughputQuotaService.tryConsumeOutgoingBlocking(3)).thenReturn(2);

        PublishMsgWithId publishMsgWithId = new PublishMsgWithId(
                UUID.randomUUID(), PublishMsgProto.getDefaultInstance(), new DefaultTbQueueMsgHeaders());
        PersistentMsgSubscriptions subscriptions = new PersistentMsgSubscriptions(
                false,
                Collections.emptyList(),
                List.of(
                        createSubscription("topic1", 1, "appClientId1", ClientType.APPLICATION),
                        createSubscription("topic2", 1, "appClientId2", ClientType.APPLICATION),
                        createSubscription("topic3", 1, "appClientId3", ClientType.APPLICATION)
                ),
                Collections.emptySet(),
                Collections.emptyList()
        );

        msgPersistenceManager.processPublish(publishMsgWithId, subscriptions, mock(PublishMsgCallback.class));

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(applicationMsgQueuePublisher, times(2)).sendMsg(sent.capture(), any(), any());
        assertEquals(List.of("appClientId1", "appClientId2"), sent.getAllValues());
        verify(tbMessageStatsReportClient).reportDroppedMsgs(1);
    }

    @Test
    public void givenPartialQuotaGrant_whenProcessPublish_thenDroppedSubscriptionsStillSettleTheCallback() {
        when(rateLimitService.isDevicePersistedMsgsLimitEnabled()).thenReturn(false);
        when(throughputQuotaService.tryConsumeOutgoingBlocking(3)).thenReturn(1);

        PublishMsgWithId publishMsgWithId = new PublishMsgWithId(
                UUID.randomUUID(), PublishMsgProto.getDefaultInstance(), new DefaultTbQueueMsgHeaders());
        PersistentMsgSubscriptions subscriptions = new PersistentMsgSubscriptions(
                false,
                Collections.emptyList(),
                List.of(
                        createSubscription("topic1", 1, "appClientId1", ClientType.APPLICATION),
                        createSubscription("topic2", 1, "appClientId2", ClientType.APPLICATION),
                        createSubscription("topic3", 1, "appClientId3", ClientType.APPLICATION)
                ),
                Collections.emptySet(),
                Collections.emptyList()
        );

        // the wrapper's callbackCount is computed before the quota trims the fan-out, so the 2 dropped
        // subscriptions each owe a completion. Only if all 3 land does the terminal callback fire.
        PublishMsgCallback terminal = mock(PublishMsgCallback.class);
        doAnswer(invocation -> {
            ((PublishMsgCallback) invocation.getArgument(2)).onSuccess();
            return null;
        }).when(applicationMsgQueuePublisher).sendMsg(any(), any(), any());

        msgPersistenceManager.processPublish(publishMsgWithId, subscriptions, terminal);

        verify(terminal).onSuccess();
    }

    @Test
    public void givenZeroQuotaGrant_whenProcessPublish_thenPersistsNothingAndReportsAllDropped() {
        when(rateLimitService.isDevicePersistedMsgsLimitEnabled()).thenReturn(false);
        when(throughputQuotaService.tryConsumeOutgoingBlocking(2)).thenReturn(0);

        PublishMsgWithId publishMsgWithId = new PublishMsgWithId(
                UUID.randomUUID(), PublishMsgProto.getDefaultInstance(), new DefaultTbQueueMsgHeaders());
        PersistentMsgSubscriptions subscriptions = new PersistentMsgSubscriptions(
                false,
                Collections.emptyList(),
                List.of(
                        createSubscription("topic1", 1, "appClientId1", ClientType.APPLICATION),
                        createSubscription("topic2", 1, "appClientId2", ClientType.APPLICATION)
                ),
                Collections.emptySet(),
                Collections.emptyList()
        );

        msgPersistenceManager.processPublish(publishMsgWithId, subscriptions, mock(PublishMsgCallback.class));

        verify(applicationMsgQueuePublisher, never()).sendMsg(any(), any(), any());
        verify(tbMessageStatsReportClient).reportDroppedMsgs(2);
    }

    @Test
    public void givenPartialQuotaGrant_whenProcessPublish_thenPersistsOnlyGrantedSharedTopics() {
        when(rateLimitService.isDevicePersistedMsgsLimitEnabled()).thenReturn(false);
        when(throughputQuotaService.tryConsumeOutgoingBlocking(2)).thenReturn(1);

        PublishMsgWithId publishMsgWithId = new PublishMsgWithId(
                UUID.randomUUID(), PublishMsgProto.getDefaultInstance(), new DefaultTbQueueMsgHeaders());
        PersistentMsgSubscriptions subscriptions = new PersistentMsgSubscriptions(
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                Set.of(
                        createSubscription("sharedTopicA", 1, "appClientId1", ClientType.APPLICATION),
                        createSubscription("sharedTopicB", 1, "appClientId2", ClientType.APPLICATION)
                ),
                Collections.emptyList()
        );

        msgPersistenceManager.processPublish(publishMsgWithId, subscriptions, mock(PublishMsgCallback.class));

        // getUniqueSharedTopics groups into a HashMap, so which topic wins is not deterministic. The charge
        // count is what matters: exactly one of the two topics is persisted and the other is reported dropped.
        verify(applicationMsgQueuePublisher, times(1)).sendMsgToSharedTopic(any(), any(), any());
        verify(tbMessageStatsReportClient).reportDroppedMsgs(1);
    }

    @Test
    public void givenQuotaFailingOpen_whenProcessPublish_thenPersistsTheWholeFanOut() {
        when(rateLimitService.isDevicePersistedMsgsLimitEnabled()).thenReturn(false);
        // the service catches Redis failures internally and grants everything; this pins that the fan-out
        // honours a full grant rather than second-guessing it, so a Valkey outage never truncates
        when(throughputQuotaService.tryConsumeOutgoingBlocking(2)).thenReturn(2);

        PublishMsgWithId publishMsgWithId = new PublishMsgWithId(
                UUID.randomUUID(), PublishMsgProto.getDefaultInstance(), new DefaultTbQueueMsgHeaders());
        PersistentMsgSubscriptions subscriptions = new PersistentMsgSubscriptions(
                false,
                Collections.emptyList(),
                List.of(
                        createSubscription("topic1", 1, "appClientId1", ClientType.APPLICATION),
                        createSubscription("topic2", 1, "appClientId2", ClientType.APPLICATION)
                ),
                Collections.emptySet(),
                Collections.emptyList()
        );

        msgPersistenceManager.processPublish(publishMsgWithId, subscriptions, mock(PublishMsgCallback.class));

        verify(applicationMsgQueuePublisher, times(2)).sendMsg(any(), any(), any());
        verify(tbMessageStatsReportClient, never()).reportDroppedMsgs(anyInt());
    }

    @Test
    public void givenIntegrationSubscriptions_whenProcessPublish_thenChargesViaTheBlockingVariant() {
        when(rateLimitService.isDevicePersistedMsgsLimitEnabled()).thenReturn(false);
        when(throughputQuotaService.tryConsumeOutgoingBlocking(2)).thenReturn(1);

        PublishMsgWithId publishMsgWithId = new PublishMsgWithId(
                UUID.randomUUID(), PublishMsgProto.getDefaultInstance(), new DefaultTbQueueMsgHeaders());
        PersistentMsgSubscriptions subscriptions = new PersistentMsgSubscriptions(
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptySet(),
                List.of(
                        createSubscription("topic1", 1, "ieClientId1", ClientType.INTEGRATION),
                        createSubscription("topic2", 1, "ieClientId2", ClientType.INTEGRATION)
                )
        );

        msgPersistenceManager.processPublish(publishMsgWithId, subscriptions, mock(PublishMsgCallback.class));

        verify(throughputQuotaService).tryConsumeOutgoingBlocking(2);
        // the lease-capped variant must be gone: it would truncate a wide integration fan-out to blockSize
        verify(throughputQuotaService, never()).tryConsumeOutgoing(anyInt());
        verify(integrationMsgQueuePublisher, times(1)).sendMsg(any(), any(), any());
        verify(tbMessageStatsReportClient).reportDroppedMsgs(1);
    }

    @Test
    public void testProcessPublishWhenNoSubscriptions() {
        PublishMsgProto publishMsgProto = PublishMsgProto.getDefaultInstance();
        PublishMsgWithId publishMsgWithId = new PublishMsgWithId(UUID.randomUUID(), publishMsgProto, new DefaultTbQueueMsgHeaders());
        PersistentMsgSubscriptions persistentMsgSubscriptions = new PersistentMsgSubscriptions();

        PublishMsgCallback callback = mock(PublishMsgCallback.class);
        msgPersistenceManager.processPublish(publishMsgWithId, persistentMsgSubscriptions, callback);

        verify(deviceMsgQueuePublisher, times(0)).sendMsg(
                any(), any(), any());
        verify(applicationMsgQueuePublisher, times(0)).sendMsg(
                any(), any(), any());
    }

    private Subscription createSubscription(String topicFilter, int qos, String clientId, ClientType type) {
        return Subscription.newInstance(topicFilter, qos, ClientSessionInfoFactory.getClientSession(true, getSessionInfo(clientId, type)));
    }

    private SessionInfo getSessionInfo(String clientId, ClientType clientType) {
        return ClientSessionInfoFactory.getSessionInfo(SERVICE_ID, clientId, clientType);
    }

    @Test
    public void testStartProcessingPersistedMessages() {
        ClientActorStateInfo actorState = mock(ClientActorStateInfo.class);
        when(actorState.getCurrentSessionCtx()).thenReturn(ctx);

        when(ctx.getClientType()).thenReturn(ClientType.APPLICATION);
        msgPersistenceManager.startProcessingPersistedMessages(actorState);
        verify(applicationPersistenceProcessor, times(1)).startProcessingPersistedMessages(eq(actorState));

        when(ctx.getClientType()).thenReturn(ClientType.DEVICE);
        msgPersistenceManager.startProcessingPersistedMessages(actorState);
        verify(devicePersistenceProcessor, times(1)).startProcessingPersistedMessages(eq(ctx));

        //wantedNumberOfInvocations = 2 since we call msgPersistenceManager.startProcessingPersistedMessages 2 times
        verify(genericClientSessionCtxManager, times(2)).resendPersistedPubRelMessages(eq(ctx));
    }

    @Test
    public void testStartProcessingSharedSubscriptions() {
        Set<TopicSharedSubscription> subscriptions = Set.of(new TopicSharedSubscription("#", "g1"));

        when(ctx.getClientType()).thenReturn(ClientType.APPLICATION);
        msgPersistenceManager.startProcessingSharedSubscriptions(ctx, subscriptions);
        verify(applicationPersistenceProcessor, times(1)).startProcessingSharedSubscriptions(eq(ctx), eq(subscriptions));

        when(ctx.getClientType()).thenReturn(ClientType.DEVICE);
        msgPersistenceManager.startProcessingSharedSubscriptions(ctx, subscriptions);
        verify(devicePersistenceProcessor, times(1)).startProcessingSharedSubscriptions(eq(ctx), eq(subscriptions));
    }

    @Test
    public void testStopProcessingPersistedMessages() {
        when(clientInfo.getType()).thenReturn(ClientType.APPLICATION);
        msgPersistenceManager.stopProcessingPersistedMessages(clientInfo);
        verify(applicationPersistenceProcessor, times(1)).stopProcessingPersistedMessages(any());

        when(clientInfo.getType()).thenReturn(ClientType.DEVICE);
        msgPersistenceManager.stopProcessingPersistedMessages(clientInfo);
        verify(devicePersistenceProcessor, times(1)).stopProcessingPersistedMessages(any());
    }

    @Test
    public void testSaveAwaitingQoS2Packets() {
        msgPersistenceManager.saveAwaitingQoS2Packets(ctx);

        verify(genericClientSessionCtxManager, times(1)).saveAwaitingQoS2Packets(eq(ctx));
    }

    @Test
    public void testClearPersistedMessages() {
        msgPersistenceManager.clearPersistedMessages("test", ClientType.APPLICATION);
        verify(applicationPersistenceProcessor, times(1)).clearPersistedMessages(any());

        msgPersistenceManager.clearPersistedMessages("test", ClientType.DEVICE);
        verify(devicePersistenceProcessor, times(1)).clearPersistedMessages(any());

        //wantedNumberOfInvocations = 2 since we call msgPersistenceManager.clearPersistedMessages 2 times
        verify(genericClientSessionCtxManager, times(2)).clearAwaitingQoS2Packets(any());
    }

    @Test
    public void testProcessPubAck() {
        when(ctx.getClientType()).thenReturn(ClientType.APPLICATION);
        msgPersistenceManager.processPubAck(ctx, 1);
        verify(applicationPersistenceProcessor, times(1)).processPubAck(any(), eq(1));

        when(ctx.getClientType()).thenReturn(ClientType.DEVICE);
        msgPersistenceManager.processPubAck(ctx, 1);
        verify(devicePersistenceProcessor, times(1)).processPubAck(any(), eq(1));
    }

    @Test
    public void testProcessPubRec() {
        when(ctx.getClientType()).thenReturn(ClientType.APPLICATION);
        msgPersistenceManager.processPubRec(ctx, 1);
        verify(applicationPersistenceProcessor, times(1)).processPubRec(eq(ctx), eq(1));

        when(ctx.getClientType()).thenReturn(ClientType.DEVICE);
        msgPersistenceManager.processPubRec(ctx, 1);
        verify(devicePersistenceProcessor, times(1)).processPubRec(any(), eq(1));
    }

    @Test
    public void processPubRecNoPubRelDelivery() {
        when(ctx.getClientType()).thenReturn(ClientType.APPLICATION);
        msgPersistenceManager.processPubRecNoPubRelDelivery(ctx, 1);
        verify(applicationPersistenceProcessor, times(1)).processPubRecNoPubRelDelivery(any(), eq(1));

        when(ctx.getClientType()).thenReturn(ClientType.DEVICE);
        msgPersistenceManager.processPubRecNoPubRelDelivery(ctx, 1);
        verify(devicePersistenceProcessor, times(1)).processPubRecNoPubRelDelivery(any(), eq(1));
    }

    @Test
    public void testProcessPubComp() {
        when(ctx.getClientType()).thenReturn(ClientType.APPLICATION);
        msgPersistenceManager.processPubComp(ctx, 1);
        verify(applicationPersistenceProcessor, times(1)).processPubComp(any(), eq(1));

        when(ctx.getClientType()).thenReturn(ClientType.DEVICE);
        msgPersistenceManager.processPubComp(ctx, 1);
        verify(devicePersistenceProcessor, times(1)).processPubComp(any(), eq(1));
    }
}
