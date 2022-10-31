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
package org.thingsboard.mqtt.broker.service.mqtt.persistence;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.PublishMsgProto;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationMsgQueuePublisher;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationPersistenceProcessor;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.DevicePersistenceProcessor;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.queue.DeviceMsgQueuePublisher;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MsgPersistenceManagerImplTest {

    static final String SERVICE_ID = "serviceId";

    ClientSessionCtx ctx;
    SessionInfo sessionInfo;
    ClientInfo clientInfo;

    GenericClientSessionCtxManager genericClientSessionCtxManager;
    ApplicationMsgQueuePublisher applicationMsgQueuePublisher;
    ApplicationPersistenceProcessor applicationPersistenceProcessor;
    DeviceMsgQueuePublisher deviceMsgQueuePublisher;
    DevicePersistenceProcessor devicePersistenceProcessor;
    ClientLogger clientLogger;
    MsgPersistenceManagerImpl msgPersistenceManager;

    @Before
    public void setUp() throws Exception {
        genericClientSessionCtxManager = mock(GenericClientSessionCtxManager.class);
        applicationMsgQueuePublisher = mock(ApplicationMsgQueuePublisher.class);
        applicationPersistenceProcessor = mock(ApplicationPersistenceProcessor.class);
        deviceMsgQueuePublisher = mock(DeviceMsgQueuePublisher.class);
        devicePersistenceProcessor = mock(DevicePersistenceProcessor.class);
        clientLogger = mock(ClientLogger.class);

        msgPersistenceManager = spy(new MsgPersistenceManagerImpl(
                genericClientSessionCtxManager, applicationMsgQueuePublisher, applicationPersistenceProcessor,
                deviceMsgQueuePublisher, devicePersistenceProcessor, clientLogger));

        ctx = mock(ClientSessionCtx.class);

        sessionInfo = mock(SessionInfo.class);
        when(ctx.getSessionInfo()).thenReturn(sessionInfo);

        clientInfo = mock(ClientInfo.class);
        when(sessionInfo.getClientInfo()).thenReturn(clientInfo);
    }

    @Test
    public void testProcessPublish() {
        PublishMsgProto publishMsgProto = PublishMsgProto.getDefaultInstance();
        List<Subscription> subscriptions = List.of(
                createSubscription("topic1", 1, "devClientId1", ClientType.DEVICE),
                createSubscription("topic2", 2, "devClientId2", ClientType.DEVICE),
                createSubscription("topic3", 1, "appClientId3", ClientType.APPLICATION),
                createSubscription("topic4", 0, "appClientId4", ClientType.APPLICATION)
        );

        msgPersistenceManager.processPublish(publishMsgProto, subscriptions, null);

        ArgumentCaptor<String> deviceMsgQueuePublisherCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> applicationMsgQueuePublisherCaptor = ArgumentCaptor.forClass(String.class);

        verify(deviceMsgQueuePublisher, times(2)).sendMsg(
                deviceMsgQueuePublisherCaptor.capture(), eq(publishMsgProto), any());
        verify(applicationMsgQueuePublisher, times(2)).sendMsg(
                applicationMsgQueuePublisherCaptor.capture(), eq(publishMsgProto), any());

        String lastDeviceClientId = deviceMsgQueuePublisherCaptor.getValue();
        assertEquals("devClientId2", lastDeviceClientId);

        String lastApplicationClientId = applicationMsgQueuePublisherCaptor.getValue();
        assertEquals("appClientId4", lastApplicationClientId);
    }

    private Subscription createSubscription(String topicFilter, int qos, String clientId, ClientType type) {
        return new Subscription(topicFilter, qos, ClientSessionInfoFactory.getClientSession(true, getSessionInfo(clientId, type)));
    }

    private SessionInfo getSessionInfo(String clientId, ClientType clientType) {
        return ClientSessionInfoFactory.getSessionInfo(
                SERVICE_ID,
                ClientSessionInfoFactory.getClientInfo(clientId, clientType),
                ClientSessionInfoFactory.getConnectionInfo());
    }

    @Test
    public void testStartProcessingPersistedMessages() {
        ClientActorStateInfo actorState = mock(ClientActorStateInfo.class);
        when(actorState.getCurrentSessionCtx()).thenReturn(ctx);

        when(clientInfo.getType()).thenReturn(ClientType.APPLICATION);
        msgPersistenceManager.startProcessingPersistedMessages(actorState, false);
        verify(applicationPersistenceProcessor, times(1)).startProcessingPersistedMessages(eq(actorState));

        when(clientInfo.getType()).thenReturn(ClientType.DEVICE);
        msgPersistenceManager.startProcessingPersistedMessages(actorState, false);
        verify(devicePersistenceProcessor, times(1)).startProcessingPersistedMessages(eq(ctx));
        verify(devicePersistenceProcessor, times(1)).clearPersistedMsgs(any());

        //wantedNumberOfInvocations = 2 since we call msgPersistenceManager.startProcessingPersistedMessages 2 times
        verify(genericClientSessionCtxManager, times(2)).resendPersistedPubRelMessages(eq(ctx));
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
        when(clientInfo.getType()).thenReturn(ClientType.APPLICATION);
        msgPersistenceManager.clearPersistedMessages(clientInfo);
        verify(applicationPersistenceProcessor, times(1)).clearPersistedMsgs(any());

        when(clientInfo.getType()).thenReturn(ClientType.DEVICE);
        msgPersistenceManager.clearPersistedMessages(clientInfo);
        verify(devicePersistenceProcessor, times(1)).clearPersistedMsgs(any());

        //wantedNumberOfInvocations = 2 since we call msgPersistenceManager.clearPersistedMessages 2 times
        verify(genericClientSessionCtxManager, times(2)).clearAwaitingQoS2Packets(any());
    }

    @Test
    public void testProcessPubAck() {
        when(clientInfo.getType()).thenReturn(ClientType.APPLICATION);
        msgPersistenceManager.processPubAck(ctx, 1);
        verify(applicationPersistenceProcessor, times(1)).processPubAck(any(), eq(1));

        when(clientInfo.getType()).thenReturn(ClientType.DEVICE);
        msgPersistenceManager.processPubAck(ctx, 1);
        verify(devicePersistenceProcessor, times(1)).processPubAck(any(), eq(1));
    }

    @Test
    public void testProcessPubRec() {
        when(clientInfo.getType()).thenReturn(ClientType.APPLICATION);
        msgPersistenceManager.processPubRec(ctx, 1);
        verify(applicationPersistenceProcessor, times(1)).processPubRec(eq(ctx), eq(1));

        when(clientInfo.getType()).thenReturn(ClientType.DEVICE);
        msgPersistenceManager.processPubRec(ctx, 1);
        verify(devicePersistenceProcessor, times(1)).processPubRec(any(), eq(1));
    }

    @Test
    public void testProcessPubComp() {
        when(clientInfo.getType()).thenReturn(ClientType.APPLICATION);
        msgPersistenceManager.processPubComp(ctx, 1);
        verify(applicationPersistenceProcessor, times(1)).processPubComp(any(), eq(1));

        when(clientInfo.getType()).thenReturn(ClientType.DEVICE);
        msgPersistenceManager.processPubComp(ctx, 1);
        verify(devicePersistenceProcessor, times(1)).processPubComp(any(), eq(1));
    }
}