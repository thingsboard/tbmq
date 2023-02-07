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
package org.thingsboard.mqtt.broker.actors.client.service.disconnect;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttVersion;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.actors.client.state.QueuedMqttMessages;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.thingsboard.mqtt.broker.util.MqttReasonCode.MESSAGE_RATE_TOO_HIGH;

@Slf4j
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = DisconnectServiceImpl.class)
public class DisconnectServiceImplTest {

    @MockBean
    KeepAliveService keepAliveService;
    @MockBean
    LastWillService lastWillService;
    @MockBean
    ClientSessionCtxService clientSessionCtxService;
    @MockBean
    MsgPersistenceManager msgPersistenceManager;
    @MockBean
    ClientSessionEventService clientSessionEventService;
    @MockBean
    RateLimitService rateLimitService;
    @MockBean
    MqttMessageGenerator mqttMessageGenerator;
    @MockBean
    AuthorizationRuleService authorizationRuleService;

    @SpyBean
    DisconnectServiceImpl disconnectService;

    ClientSessionCtx ctx;
    ClientActorStateInfo clientActorState;
    QueuedMqttMessages queuedMqttMessages;

    @Before
    public void setUp() {
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
    public void testDisconnectFail() {
        when(ctx.getSessionInfo()).thenReturn(null);

        MqttDisconnectMsg disconnectMsg = newDisconnectMsg(new DisconnectReason(DisconnectReasonType.ON_DISCONNECT_MSG));
        disconnectService.disconnect(clientActorState, disconnectMsg);

        verify(disconnectService, never()).clearClientSession(clientActorState, disconnectMsg, null);
        verify(disconnectService, never()).notifyClientDisconnected(clientActorState, 0);
        verify(disconnectService, never()).closeChannel(ctx);
    }

    @Test
    public void testDisconnect() {
        MqttDisconnectMsg disconnectMsg = newDisconnectMsg(new DisconnectReason(DisconnectReasonType.ON_DISCONNECT_MSG));
        disconnectService.disconnect(clientActorState, disconnectMsg);

        verify(disconnectService, times(1)).clearClientSession(clientActorState, disconnectMsg, null);
        verify(disconnectService, times(1)).notifyClientDisconnected(clientActorState, null);
        verify(disconnectService, times(1)).closeChannel(ctx);
    }

    @Test
    public void testClearClientSession() {
        MqttDisconnectMsg disconnectMsg = newDisconnectMsg(new DisconnectReason(DisconnectReasonType.ON_DISCONNECT_MSG));
        disconnectService.clearClientSession(clientActorState, disconnectMsg, null);

        verify(queuedMqttMessages, times(1)).clear();
        verify(keepAliveService, times(1)).unregisterSession(any());
        verify(lastWillService, times(1)).removeAndExecuteLastWillIfNeeded(any(), anyBoolean(), anyBoolean(), any());
        verify(clientSessionCtxService, times(1)).unregisterSession(any());
    }

    @Test
    public void testProcessPersistenceDisconnect() {
        ClientInfo clientInfo = mock(ClientInfo.class);
        disconnectService.processPersistenceDisconnect(ctx, clientInfo, UUID.randomUUID());

        verify(msgPersistenceManager, times(1)).stopProcessingPersistedMessages(any());
        verify(msgPersistenceManager, times(1)).saveAwaitingQoS2Packets(any());
    }

    @Test
    public void testSendDisconnectForMqtt5OnError() {
        ChannelHandlerContext handlerContext = mock(ChannelHandlerContext.class);
        when(ctx.getChannel()).thenReturn(handlerContext);
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);
        disconnectService.disconnect(clientActorState, newDisconnectMsg(new DisconnectReason(DisconnectReasonType.ON_RATE_LIMITS)));

        verify(mqttMessageGenerator, times(1)).createDisconnectMsg(MESSAGE_RATE_TOO_HIGH);
        verify(disconnectService, times(1)).closeChannel(ctx);
    }

    private MqttDisconnectMsg newDisconnectMsg(DisconnectReason reason) {
        return new MqttDisconnectMsg(UUID.randomUUID(), reason);
    }
}