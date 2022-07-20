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
package org.thingsboard.mqtt.broker.actors.client.service.disconnect;

import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.actors.client.state.QueuedMqttMessages;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisconnectServiceImplTest {

    KeepAliveService keepAliveService;
    LastWillService lastWillService;
    ClientSessionCtxService clientSessionCtxService;
    MsgPersistenceManager msgPersistenceManager;
    ClientSessionEventService clientSessionEventService;

    DisconnectServiceImpl disconnectService;

    ClientSessionCtx ctx;
    ClientActorStateInfo clientActorState;
    QueuedMqttMessages queuedMqttMessages;

    @BeforeEach
    void setUp() {
        keepAliveService = mock(KeepAliveService.class);
        lastWillService = mock(LastWillService.class);
        clientSessionCtxService = mock(ClientSessionCtxService.class);
        msgPersistenceManager = mock(MsgPersistenceManager.class);
        clientSessionEventService = mock(ClientSessionEventService.class);

        disconnectService = spy(new DisconnectServiceImpl(keepAliveService, lastWillService, clientSessionCtxService,
                msgPersistenceManager, clientSessionEventService));

        ctx = mock(ClientSessionCtx.class);

        clientActorState = mock(ClientActorStateInfo.class);
        when(clientActorState.getCurrentSessionCtx()).thenReturn(ctx);

        SessionInfo sessionInfo = mock(SessionInfo.class);
        when(ctx.getSessionInfo()).thenReturn(sessionInfo);

        ClientInfo clientInfo = mock(ClientInfo.class);
        when(sessionInfo.getClientInfo()).thenReturn(clientInfo);

        queuedMqttMessages = mock(QueuedMqttMessages.class);
        when(clientActorState.getQueuedMessages()).thenReturn(queuedMqttMessages);
    }

    @Test
    void testDisconnectFail() {
        when(ctx.getSessionInfo()).thenReturn(null);

        disconnectService.disconnect(clientActorState, new DisconnectReason(DisconnectReasonType.ON_DISCONNECT_MSG));

        verify(disconnectService, never()).clearClientSession(clientActorState, DisconnectReasonType.ON_DISCONNECT_MSG);
        verify(disconnectService, never()).notifyClientDisconnected(clientActorState);
        verify(disconnectService, never()).closeChannel(ctx);
    }

    @Test
    void testDisconnect() {
        disconnectService.disconnect(clientActorState, new DisconnectReason(DisconnectReasonType.ON_DISCONNECT_MSG));

        verify(disconnectService, times(1)).clearClientSession(clientActorState, DisconnectReasonType.ON_DISCONNECT_MSG);
        verify(disconnectService, times(1)).notifyClientDisconnected(clientActorState);
        verify(disconnectService, times(1)).closeChannel(ctx);
    }

    @Test
    void testCloseChannel() {
        ChannelHandlerContext channelHandlerContext = mock(ChannelHandlerContext.class);
        when(ctx.getChannel()).thenReturn(channelHandlerContext);

        disconnectService.closeChannel(ctx);

        verify(channelHandlerContext, times(1)).close();
    }

    @Test
    void testClearClientSession() {
        disconnectService.clearClientSession(clientActorState, DisconnectReasonType.ON_DISCONNECT_MSG);

        verify(queuedMqttMessages, times(1)).clear();
        verify(keepAliveService, times(1)).unregisterSession(any());
        verify(lastWillService, times(1)).removeAndExecuteLastWillIfNeeded(any(), anyBoolean());
        verify(clientSessionCtxService, times(1)).unregisterSession(any());
    }

    @Test
    void testProcessPersistenceDisconnect() {
        ClientInfo clientInfo = mock(ClientInfo.class);
        disconnectService.processPersistenceDisconnect(ctx, clientInfo, UUID.randomUUID());

        verify(msgPersistenceManager, times(1)).stopProcessingPersistedMessages(any());
        verify(msgPersistenceManager, times(1)).saveAwaitingQoS2Packets(any());
    }
}