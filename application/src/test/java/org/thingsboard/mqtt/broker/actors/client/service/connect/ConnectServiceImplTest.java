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
package org.thingsboard.mqtt.broker.actors.client.service.connect;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.TbActorRef;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionAcceptedMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttConnectMsg;
import org.thingsboard.mqtt.broker.actors.client.service.MqttMessageHandlerImpl;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.actors.client.state.QueuedMqttMessages;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.UUID;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    }

    @Test
    public void testAcceptConnection() {
        TbActorRef actorRef = mock(TbActorRef.class);

        QueuedMqttMessages queuedMqttMessages = mock(QueuedMqttMessages.class);
        when(actorState.getQueuedMessages()).thenReturn(queuedMqttMessages);

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
    public void testRefuseConnection() {
        connectService.refuseConnection(ctx, null);

        verify(mqttMessageGenerator, times(1)).createMqttConnAckMsg(eq(CONNECTION_REFUSED_SERVER_UNAVAILABLE));
        verify(channelHandlerContext, times(1)).writeAndFlush(any());
        verify(clientMqttActorManager, times(1)).disconnect(any(), any());
    }

    @Test
    public void testGetKeepAliveSeconds() {
        connectService.setMaxServerKeepAlive(50);

        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);
        int keepAliveSeconds = connectService.getKeepAliveSeconds(actorState, new MqttConnectMsg(
                UUID.randomUUID(), "id", false, 100, null
        ));
        Assert.assertEquals(50, keepAliveSeconds);

        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_3_1_1);
        keepAliveSeconds = connectService.getKeepAliveSeconds(actorState, new MqttConnectMsg(
                UUID.randomUUID(), "id", false, 100, null
        ));
        Assert.assertEquals(100, keepAliveSeconds);
    }

    @Test(expected = MqttException.class)
    public void testValidate() {
        MqttConnectMsg connectMsg = getMqttConnectMsg(UUID.randomUUID(), "");
        connectService.validate(ctx, connectMsg);

        verify(channelHandlerContext, times(1)).close();
    }

    @Test
    public void testGetSessionInfo() {
        UUID sessionId = UUID.randomUUID();
        String clientId = "clientId";
        MqttConnectMsg msg = getMqttConnectMsg(sessionId, clientId);
        SessionInfo actualSessionInfo = connectService.getSessionInfo(msg, sessionId, clientId, ClientType.DEVICE, 0, "localhost");

        SessionInfo expectedSessionInfo = ClientSessionInfoFactory.getSessionInfo(
                sessionId,
                false,
                SERVICE_ID,
                getClientInfo(clientId),
                getConnectionInfo(1000, actualSessionInfo.getConnectionInfo().getConnectedAt()), 0);

        Assert.assertEquals(expectedSessionInfo, actualSessionInfo);
    }

    private ConnectionAcceptedMsg getConnectionAcceptedMsg(PublishMsg publishMsg) {
        return new ConnectionAcceptedMsg(UUID.randomUUID(), false, publishMsg, 1000);
    }

    private MqttConnectMsg getMqttConnectMsg(UUID sessionId, String clientId) {
        return new MqttConnectMsg(sessionId, clientId, false, 1000, null);
    }
}
