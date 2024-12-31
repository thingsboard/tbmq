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
package org.thingsboard.mqtt.broker.actors.client.service.connect;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.Cache;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.TbActorRef;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionAcceptedMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttConnectMsg;
import org.thingsboard.mqtt.broker.actors.client.service.MqttMessageHandlerImpl;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.actors.client.state.QueuedMqttMessages;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.CacheNameResolver;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.flow.control.FlowControlService;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.validation.PublishMsgValidationService;
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.TopicAliasCtx;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.UUID;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_CLIENT_IDENTIFIER_NOT_VALID;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED_5;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_QUOTA_EXCEEDED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_TOPIC_NAME_INVALID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory.getClientInfo;
import static org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory.getConnectionInfo;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = ConnectServiceImpl.class)
public class ConnectServiceImplTest {

    static final String SERVICE_ID = "serviceId";

    @MockBean
    ClientMqttActorManager clientMqttActorManager;
    @MockBean
    MqttMessageGenerator mqttMessageGenerator;
    @MockBean
    ClientSessionEventService clientSessionEventService;
    @MockBean
    KeepAliveService keepAliveService;
    @MockBean
    ServiceInfoProvider serviceInfoProvider;
    @MockBean
    LastWillService lastWillService;
    @MockBean
    ClientSessionCtxService clientSessionCtxService;
    @MockBean
    MsgPersistenceManager msgPersistenceManager;
    @MockBean
    MqttMessageHandlerImpl messageHandler;
    @MockBean
    ClientSubscriptionCache clientSubscriptionCache;
    @MockBean
    RateLimitService rateLimitService;
    @MockBean
    FlowControlService flowControlService;
    @MockBean
    PublishMsgValidationService publishMsgValidationService;
    @MockBean
    CacheNameResolver cacheNameResolver;

    @SpyBean
    ConnectServiceImpl connectService;

    ClientSessionCtx ctx;
    ChannelHandlerContext channelHandlerContext;
    ClientActorStateInfo actorState;
    SessionInfo sessionInfo;

    @Before
    public void setUp() throws Exception {
        when(serviceInfoProvider.getServiceId()).thenReturn(SERVICE_ID);

        ctx = mock(ClientSessionCtx.class);

        channelHandlerContext = mock(ChannelHandlerContext.class);
        when(ctx.getChannel()).thenReturn(channelHandlerContext);

        sessionInfo = mock(SessionInfo.class);
        when(ctx.getSessionInfo()).thenReturn(sessionInfo);
        when(sessionInfo.isPersistent()).thenReturn(true);

        actorState = mock(ClientActorStateInfo.class);
        when(actorState.getCurrentSessionCtx()).thenReturn(ctx);

        when(ctx.getTopicAliasCtx()).thenReturn(new TopicAliasCtx(false, 0));
    }

    @After
    public void tearDown() {
    }

    @Test
    public void givenConnectionAcceptedMsg_whenAcceptConnection_thenVerifyExecutions() {
        TbActorRef actorRef = mock(TbActorRef.class);
        Cache cache = mock(Cache.class);

        QueuedMqttMessages queuedMqttMessages = mock(QueuedMqttMessages.class);
        when(actorState.getQueuedMessages()).thenReturn(queuedMqttMessages);
        when(cacheNameResolver.getCache(eq(CacheConstants.CLIENT_MQTT_VERSION_CACHE))).thenReturn(cache);
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);

        PublishMsg publishMsg = PublishMsg.builder().build();
        ConnectionAcceptedMsg connectionAcceptedMsg = getConnectionAcceptedMsg(publishMsg);
        connectService.acceptConnection(actorState, connectionAcceptedMsg, actorRef);

        verify(lastWillService, times(1)).saveLastWillMsg(any(), eq(publishMsg));
        verify(channelHandlerContext, times(1)).writeAndFlush(any());
        verify(clientSessionCtxService, times(1)).registerSession(eq(ctx));
        verify(msgPersistenceManager, times(1)).startProcessingPersistedMessages(eq(actorState), eq(false));
        verify(queuedMqttMessages, times(1)).process(any());
    }

    @Test
    public void givenClientSessionContext_whenRefuseConnection_thenVerifyExecutions() {
        connectService.refuseConnection(ctx, null);

        verify(mqttMessageGenerator, times(1)).createMqttConnAckMsg(eq(CONNECTION_REFUSED_SERVER_UNAVAILABLE));
        verify(channelHandlerContext, times(1)).writeAndFlush(any());
        verify(clientMqttActorManager, times(1)).disconnect(any(), any());
    }

    @Test
    public void givenMqtt5Client_whenGetKeepAliveSeconds_thenReturnExpectedValue() {
        connectService.setMaxServerKeepAlive(50);

        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);
        int keepAliveSeconds = connectService.getKeepAliveSeconds(actorState, new MqttConnectMsg(
                UUID.randomUUID(), "id", false, 100, null
        ));
        Assert.assertEquals(50, keepAliveSeconds);
    }

    @Test
    public void givenMqtt3Client_whenGetKeepAliveSeconds_thenReturnExpectedValue() {
        connectService.setMaxServerKeepAlive(50);

        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_3_1_1);
        int keepAliveSeconds = connectService.getKeepAliveSeconds(actorState, new MqttConnectMsg(
                UUID.randomUUID(), "id", false, 100, null
        ));
        Assert.assertEquals(100, keepAliveSeconds);
    }

    @Test
    public void givenPersistentClientWithoutClientId_whenCheckIfProceedConnection_thenConnectionRefused() {
        MqttConnectMsg connectMsg = getMqttConnectMsg(UUID.randomUUID(), "");
        boolean result = connectService.shouldProceedWithConnection(actorState, connectMsg, sessionInfo);
        Assert.assertFalse(result);

        verify(mqttMessageGenerator, times(1)).createMqttConnAckMsg(CONNECTION_REFUSED_IDENTIFIER_REJECTED);
        verify(clientMqttActorManager, times(1)).disconnect(any(), any());
    }

    @Test
    public void givenSessionsLimit_whenCheckIfProceedConnection_thenConnectionRefused() {
        when(actorState.getClientId()).thenReturn("testClient");
        when(rateLimitService.checkSessionsLimit("testClient")).thenReturn(false);

        MqttConnectMsg connectMsg = getMqttConnectMsg(UUID.randomUUID(), "testClient");
        boolean result = connectService.shouldProceedWithConnection(actorState, connectMsg, sessionInfo);
        Assert.assertFalse(result);

        verify(mqttMessageGenerator, times(1)).createMqttConnAckMsg(CONNECTION_REFUSED_SERVER_UNAVAILABLE);
        verify(clientMqttActorManager, times(1)).disconnect(any(), any());
    }

    @Test
    public void givenApplicationClientsLimit_whenCheckIfProceedConnection_thenConnectionRefused() {
        when(rateLimitService.checkSessionsLimit("testClient")).thenReturn(true);
        when(actorState.getClientId()).thenReturn("testClient");
        when(rateLimitService.checkApplicationClientsLimit(sessionInfo)).thenReturn(false);

        MqttConnectMsg connectMsg = getMqttConnectMsg(UUID.randomUUID(), "testClient");
        boolean result = connectService.shouldProceedWithConnection(actorState, connectMsg, sessionInfo);
        Assert.assertFalse(result);

        verify(mqttMessageGenerator, times(1)).createMqttConnAckMsg(CONNECTION_REFUSED_SERVER_UNAVAILABLE);
        verify(clientMqttActorManager, times(1)).disconnect(any(), any());
    }

    @Test
    public void givenLastWillMsgInvalid_whenCheckIfProceedConnection_thenConnectionRefused() {
        when(actorState.getClientId()).thenReturn("testClient");
        when(rateLimitService.checkSessionsLimit("testClient")).thenReturn(true);
        doThrow(DataValidationException.class).when(publishMsgValidationService).validatePubMsg(any(), any(), any());

        PublishMsg lastWillMsg = PublishMsg.builder().build();
        MqttConnectMsg connectMsg = getMqttConnectMsg(UUID.randomUUID(), "testClient", lastWillMsg);
        boolean result = connectService.shouldProceedWithConnection(actorState, connectMsg, sessionInfo);
        Assert.assertFalse(result);

        verify(mqttMessageGenerator, times(1)).createMqttConnAckMsg(CONNECTION_REFUSED_SERVER_UNAVAILABLE);
        verify(clientMqttActorManager, times(1)).disconnect(any(), any());
    }

    @Test
    public void givenLastWillMsgNotAuth_whenCheckIfProceedConnection_thenConnectionRefused() {
        when(actorState.getClientId()).thenReturn("testClient");
        when(rateLimitService.checkSessionsLimit("testClient")).thenReturn(true);
        when(publishMsgValidationService.validatePubMsg(any(), any(), any())).thenReturn(false);

        PublishMsg lastWillMsg = PublishMsg.builder().build();
        MqttConnectMsg connectMsg = getMqttConnectMsg(UUID.randomUUID(), "testClient", lastWillMsg);
        boolean result = connectService.shouldProceedWithConnection(actorState, connectMsg, sessionInfo);
        Assert.assertFalse(result);

        verify(mqttMessageGenerator, times(1)).createMqttConnAckMsg(CONNECTION_REFUSED_NOT_AUTHORIZED);
        verify(clientMqttActorManager, times(1)).disconnect(any(), any());
    }

    @Test
    public void givenPersistentClientWithoutClientIdMqtt5_whenCheckIfProceedConnection_thenConnectionRefused() {
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);

        MqttConnectMsg connectMsg = getMqttConnectMsg(UUID.randomUUID(), "");
        boolean result = connectService.shouldProceedWithConnection(actorState, connectMsg, sessionInfo);
        Assert.assertFalse(result);

        verify(mqttMessageGenerator, times(1)).createMqttConnAckMsg(CONNECTION_REFUSED_CLIENT_IDENTIFIER_NOT_VALID);
        verify(clientMqttActorManager, times(1)).disconnect(any(), any());
    }

    @Test
    public void givenSessionsLimitMqtt5_whenCheckIfProceedConnection_thenConnectionRefused() {
        when(actorState.getClientId()).thenReturn("testClient");
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);
        when(rateLimitService.checkSessionsLimit("testClient")).thenReturn(false);

        MqttConnectMsg connectMsg = getMqttConnectMsg(UUID.randomUUID(), "testClient");
        boolean result = connectService.shouldProceedWithConnection(actorState, connectMsg, sessionInfo);
        Assert.assertFalse(result);

        verify(mqttMessageGenerator, times(1)).createMqttConnAckMsg(CONNECTION_REFUSED_QUOTA_EXCEEDED);
        verify(clientMqttActorManager, times(1)).disconnect(any(), any());
    }

    @Test
    public void givenApplicationClientsLimitMqtt5_whenCheckIfProceedConnection_thenConnectionRefused() {
        when(rateLimitService.checkSessionsLimit("testClient")).thenReturn(true);
        when(actorState.getClientId()).thenReturn("testClient");
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);
        when(rateLimitService.checkApplicationClientsLimit(sessionInfo)).thenReturn(false);

        MqttConnectMsg connectMsg = getMqttConnectMsg(UUID.randomUUID(), "testClient");
        boolean result = connectService.shouldProceedWithConnection(actorState, connectMsg, sessionInfo);
        Assert.assertFalse(result);

        verify(mqttMessageGenerator, times(1)).createMqttConnAckMsg(CONNECTION_REFUSED_QUOTA_EXCEEDED);
        verify(clientMqttActorManager, times(1)).disconnect(any(), any());
    }

    @Test
    public void givenLastWillMsgInvalidMqtt5_whenCheckIfProceedConnection_thenConnectionRefused() {
        when(actorState.getClientId()).thenReturn("testClient");
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);
        when(rateLimitService.checkSessionsLimit("testClient")).thenReturn(true);
        doThrow(DataValidationException.class).when(publishMsgValidationService).validatePubMsg(any(), any(), any());

        PublishMsg lastWillMsg = PublishMsg.builder().build();
        MqttConnectMsg connectMsg = getMqttConnectMsg(UUID.randomUUID(), "testClient", lastWillMsg);
        boolean result = connectService.shouldProceedWithConnection(actorState, connectMsg, sessionInfo);
        Assert.assertFalse(result);

        verify(mqttMessageGenerator, times(1)).createMqttConnAckMsg(CONNECTION_REFUSED_TOPIC_NAME_INVALID);
        verify(clientMqttActorManager, times(1)).disconnect(any(), any());
    }

    @Test
    public void givenLastWillMsgNotAuthMqtt5_whenCheckIfProceedConnection_thenConnectionRefused() {
        when(actorState.getClientId()).thenReturn("testClient");
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);
        when(rateLimitService.checkSessionsLimit("testClient")).thenReturn(true);
        when(publishMsgValidationService.validatePubMsg(any(), any(), any())).thenReturn(false);

        PublishMsg lastWillMsg = PublishMsg.builder().build();
        MqttConnectMsg connectMsg = getMqttConnectMsg(UUID.randomUUID(), "testClient", lastWillMsg);
        boolean result = connectService.shouldProceedWithConnection(actorState, connectMsg, sessionInfo);
        Assert.assertFalse(result);

        verify(mqttMessageGenerator, times(1)).createMqttConnAckMsg(CONNECTION_REFUSED_NOT_AUTHORIZED_5);
        verify(clientMqttActorManager, times(1)).disconnect(any(), any());
    }

    @Test
    public void givenClientSessionDetails_whenGetSessionInfo_thenReturnExpectedResult() {
        UUID sessionId = UUID.randomUUID();
        String clientId = "clientId";
        MqttConnectMsg msg = getMqttConnectMsg(sessionId, clientId);
        SessionInfo actualSessionInfo = connectService.getSessionInfo(msg, sessionId, clientId, ClientType.DEVICE, 0, BrokerConstants.LOCAL_ADR);

        SessionInfo expectedSessionInfo = ClientSessionInfoFactory.getSessionInfo(
                sessionId,
                false,
                SERVICE_ID,
                getClientInfo(clientId),
                getConnectionInfo(1000, actualSessionInfo.getConnectionInfo().getConnectedAt()), 0);

        Assert.assertEquals(expectedSessionInfo, actualSessionInfo);
    }

    @Test
    public void givenConnectPacketForMqtt3Client_whenGetReceiveMaxValue_thenGetExpectedValue() {
        connectService.setMqtt3xReceiveMax(10);

        MqttConnectMsg mqttConnectMsg = getMqttConnectMsg(UUID.randomUUID(), "testClient");
        int receiveMaxValue = connectService.getReceiveMaxValue(mqttConnectMsg, ctx);
        Assert.assertEquals(10, receiveMaxValue);
    }

    @Test
    public void givenConnectPacketWithReceiveMaxForMqtt5Client_whenGetReceiveMaxValue_thenGetExpectedValue() {
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);
        connectService.setMqtt3xReceiveMax(10);

        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(MqttProperties.MqttPropertyType.RECEIVE_MAXIMUM.value(), 20));
        MqttConnectMsg mqttConnectMsg = getMqttConnectMsg(UUID.randomUUID(), "testClient", null, properties);

        int receiveMaxValue = connectService.getReceiveMaxValue(mqttConnectMsg, ctx);
        Assert.assertEquals(20, receiveMaxValue);
    }

    @Test
    public void givenConnectPacketWithoutReceiveMaxForMqtt5Client_whenGetReceiveMaxValue_thenGetExpectedValue() {
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);
        connectService.setMqtt3xReceiveMax(10);

        MqttConnectMsg mqttConnectMsg = getMqttConnectMsg(UUID.randomUUID(), "testClient", null, new MqttProperties());

        int receiveMaxValue = connectService.getReceiveMaxValue(mqttConnectMsg, ctx);
        Assert.assertEquals(BrokerConstants.DEFAULT_RECEIVE_MAXIMUM, receiveMaxValue);
    }

    private ConnectionAcceptedMsg getConnectionAcceptedMsg(PublishMsg publishMsg) {
        return new ConnectionAcceptedMsg(UUID.randomUUID(), false, publishMsg, 1000, new MqttProperties());
    }

    private MqttConnectMsg getMqttConnectMsg(UUID sessionId, String clientId) {
        return getMqttConnectMsg(sessionId, clientId, null);
    }

    private MqttConnectMsg getMqttConnectMsg(UUID sessionId, String clientId, PublishMsg lastWillMsg) {
        return getMqttConnectMsg(sessionId, clientId, lastWillMsg, MqttProperties.NO_PROPERTIES);
    }

    private MqttConnectMsg getMqttConnectMsg(UUID sessionId, String clientId, PublishMsg lastWillMsg, MqttProperties properties) {
        return new MqttConnectMsg(sessionId, clientId, false, 1000, lastWillMsg, properties);
    }
}
