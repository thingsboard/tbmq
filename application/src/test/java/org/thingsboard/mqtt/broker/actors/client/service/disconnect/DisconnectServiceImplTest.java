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
package org.thingsboard.mqtt.broker.actors.client.service.disconnect;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.netty.handler.codec.mqtt.MqttVersion;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.actors.client.state.QueuedMqttMessages;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.limits.RateLimitCacheService;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.flow.control.FlowControlService;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Slf4j
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = DisconnectServiceImpl.class)
public class DisconnectServiceImplTest {

    private static final String CLIENT_ID = "testClient";

    @MockitoBean
    KeepAliveService keepAliveService;
    @MockitoBean
    LastWillService lastWillService;
    @MockitoBean
    ClientSessionCtxService clientSessionCtxService;
    @MockitoBean
    MsgPersistenceManager msgPersistenceManager;
    @MockitoBean
    ClientSessionEventService clientSessionEventService;
    @MockitoBean
    RateLimitService rateLimitService;
    @MockitoBean
    MqttMessageGenerator mqttMessageGenerator;
    @MockitoBean
    AuthorizationRuleService authorizationRuleService;
    @MockitoBean
    FlowControlService flowControlService;
    @MockitoBean
    RateLimitCacheService rateLimitCacheService;
    @MockitoBean
    TbMessageStatsReportClient tbMessageStatsReportClient;

    @MockitoBean
    DisconnectServiceImpl disconnectService;

    ClientSessionCtx ctx;
    ClientActorStateInfo clientActorState;
    QueuedMqttMessages queuedMqttMessages;
    SessionInfo sessionInfo;

    @Before
    public void setUp() {
        ctx = mock(ClientSessionCtx.class);

        clientActorState = mock(ClientActorStateInfo.class);
        when(clientActorState.getCurrentSessionCtx()).thenReturn(ctx);

        sessionInfo = mock(SessionInfo.class);
        when(ctx.getSessionInfo()).thenReturn(sessionInfo);

        ClientInfo clientInfo = mock(ClientInfo.class);
        when(sessionInfo.getClientInfo()).thenReturn(clientInfo);

        queuedMqttMessages = mock(QueuedMqttMessages.class);
        when(clientActorState.getQueuedMessages()).thenReturn(queuedMqttMessages);

        when(ctx.getClientId()).thenReturn(CLIENT_ID);
    }

    @Test
    public void givenSessionInfoIsNull_whenDisconnectClient_thenCloseChannel() {
        when(ctx.getSessionInfo()).thenReturn(null);

        MqttDisconnectMsg disconnectMsg = newDisconnectMsg(new DisconnectReason(DisconnectReasonType.ON_DISCONNECT_MSG));
        disconnectService.disconnect(clientActorState, disconnectMsg);

        verify(disconnectService, never()).clearClientSession(clientActorState, disconnectMsg, -1);
        verify(disconnectService, never()).notifyClientDisconnected(clientActorState, 0);
        verify(disconnectService, times(1)).closeChannel(ctx);
    }

    @Test
    public void givenSessionInfoIsNotNull_whenDisconnectClient_thenDisconnectCleanlyAndCloseChannel() {
        MqttDisconnectMsg disconnectMsg = newDisconnectMsg(new DisconnectReason(DisconnectReasonType.ON_DISCONNECT_MSG));
        disconnectService.disconnect(clientActorState, disconnectMsg);

        verify(disconnectService, times(1)).clearClientSession(clientActorState, disconnectMsg, -1);
        verify(disconnectService, times(1)).notifyClientDisconnected(clientActorState, -1);
        verify(disconnectService, times(1)).closeChannel(ctx);
        verify(rateLimitService, times(1)).remove(eq(CLIENT_ID));
        verify(authorizationRuleService, times(1)).evict(eq(CLIENT_ID));
        verify(flowControlService, times(1)).removeFromMap(eq(CLIENT_ID));
        verify(tbMessageStatsReportClient).removeClient(eq(CLIENT_ID));
        verify(mqttMessageGenerator, never()).createDisconnectMsg(any());
    }

    @Test
    public void givenMqtt5Client_whenDisconnectClientByServer_thenSendDisconnectMsgAndCloseChannel() {
        ChannelHandlerContext handlerContext = mock(ChannelHandlerContext.class);
        when(ctx.getChannel()).thenReturn(handlerContext);
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);
        disconnectService.disconnect(clientActorState, newDisconnectMsg(new DisconnectReason(DisconnectReasonType.ON_RATE_LIMITS)));

        verify(mqttMessageGenerator, times(1)).createDisconnectMsg(MqttReasonCodes.Disconnect.MESSAGE_RATE_TOO_HIGH);
        verify(handlerContext, times(1)).writeAndFlush(any());
        verify(disconnectService, times(1)).closeChannel(ctx);
    }

    @Test
    public void givenNonPersistentClient_whenClearClientSession_thenAllResourcesTerminationDone() {
        when(sessionInfo.isPersistent()).thenReturn(false);

        MqttDisconnectMsg disconnectMsg = newDisconnectMsg(new DisconnectReason(DisconnectReasonType.ON_DISCONNECT_MSG));
        disconnectService.clearClientSession(clientActorState, disconnectMsg, -1);

        verify(queuedMqttMessages, times(1)).clear();
        verify(keepAliveService, times(1)).unregisterSession(any());
        verify(lastWillService, times(1)).removeAndExecuteLastWillIfNeeded(any(), anyBoolean(), anyBoolean(), eq(-1));
        verify(clientSessionCtxService, times(1)).unregisterSession(any());
    }

    @Test
    public void givenPersistentClient_whenClearClientSession_thenAllResourcesTerminationDone() {
        when(sessionInfo.isPersistent()).thenReturn(true);

        MqttDisconnectMsg disconnectMsg = newDisconnectMsg(new DisconnectReason(DisconnectReasonType.ON_DISCONNECT_MSG));
        disconnectService.clearClientSession(clientActorState, disconnectMsg, -1);

        verify(queuedMqttMessages, times(1)).clear();
        verify(keepAliveService, times(1)).unregisterSession(any());
        verify(lastWillService, times(1)).removeAndExecuteLastWillIfNeeded(any(), anyBoolean(), anyBoolean(), eq(-1));
        verify(clientSessionCtxService, times(1)).unregisterSession(any());
        verify(disconnectService, times(1)).processPersistenceDisconnect(any(), any(), any());
    }

    @Test
    public void givenClient_whenProcessPersistenceDisconnect_thenExecuteCleanDisconnect() {
        ClientInfo clientInfo = mock(ClientInfo.class);
        disconnectService.processPersistenceDisconnect(ctx, clientInfo, UUID.randomUUID());

        verify(msgPersistenceManager, times(1)).stopProcessingPersistedMessages(any());
        verify(msgPersistenceManager, times(1)).saveAwaitingQoS2Packets(any());
    }

    private MqttDisconnectMsg newDisconnectMsg(DisconnectReason reason) {
        return new MqttDisconnectMsg(UUID.randomUUID(), reason);
    }

}
