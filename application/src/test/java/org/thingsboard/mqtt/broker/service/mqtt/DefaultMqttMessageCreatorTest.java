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
package org.thingsboard.mqtt.broker.service.mqtt;

import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionAcceptedMsg;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.stats.ConnectionStats;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.TopicAliasCtx;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class DefaultMqttMessageCreatorTest {

    ConnectionStats connectionStats;
    DefaultMqttMessageCreator mqttMessageCreator;

    @Before
    public void setUp() throws Exception {
        StatsManager statsManager = mock(StatsManager.class);
        connectionStats = mock(ConnectionStats.class);
        given(statsManager.getConnectionStats()).willReturn(connectionStats);
        mqttMessageCreator = new DefaultMqttMessageCreator(statsManager);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void givenServerResponseInfoNull_whenRequestResponseInfoIsZero_thenResponseInfoIsNotReturned() {
        mqttMessageCreator.setServerResponseInfo(null);
        String responseInfo = mqttMessageCreator.getResponseInfo(0);
        Assert.assertNull(responseInfo);
    }

    @Test
    public void givenServerResponseInfoNotNull_whenRequestResponseInfoIsZero_thenResponseInfoIsNotReturned() {
        mqttMessageCreator.setServerResponseInfo("test/");
        String responseInfo = mqttMessageCreator.getResponseInfo(0);
        Assert.assertNull(responseInfo);
    }

    @Test
    public void givenServerResponseInfoNotNull_whenRequestResponseInfoIsSet_thenResponseInfoIsReturned() {
        mqttMessageCreator.setServerResponseInfo("test/");
        String responseInfo = mqttMessageCreator.getResponseInfo(1);
        Assert.assertEquals("test/", responseInfo);
    }

    @Test
    public void givenMqttConnAckMsg_whenEnhancedAuthIsNull_thenMqttConnAckMsgDoesNotContainAuthMethod() {
        ClientActorStateInfo clientActorState = mockAcceptedActorState();

        ConnectionAcceptedMsg connectionAcceptedMsg = new ConnectionAcceptedMsg(UUID.randomUUID(), true, null, MqttProperties.NO_PROPERTIES);
        MqttConnAckMessage msg = mqttMessageCreator.createMqttConnAckMsg(clientActorState, connectionAcceptedMsg);

        Assert.assertNull(msg.variableHeader().properties().getProperty(BrokerConstants.AUTHENTICATION_METHOD_PROP_ID));
    }

    @Test
    public void givenRefusalReturnCode_whenCreateConnAck_thenConnectionRefusedCounted() {
        mqttMessageCreator.createMqttConnAckMsg(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);

        verify(connectionStats).onConnectionRefused(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
        verify(connectionStats, Mockito.never()).onConnectionAccepted();
    }

    @Test
    public void givenAcceptedReturnCode_whenCreateConnAckSingleArg_thenConnectionAcceptedCounted() {
        mqttMessageCreator.createMqttConnAckMsg(MqttConnectReturnCode.CONNECTION_ACCEPTED);

        verify(connectionStats).onConnectionAccepted();
        verify(connectionStats, Mockito.never()).onConnectionRefused(Mockito.any());
    }

    @Test
    public void givenAcceptedConnection_whenCreateConnAck_thenConnectionAcceptedCounted() {
        ClientActorStateInfo clientActorState = mockAcceptedActorState();

        ConnectionAcceptedMsg connectionAcceptedMsg =
                new ConnectionAcceptedMsg(UUID.randomUUID(), true, null, MqttProperties.NO_PROPERTIES);
        mqttMessageCreator.createMqttConnAckMsg(clientActorState, connectionAcceptedMsg);

        verify(connectionStats).onConnectionAccepted();
        verify(connectionStats, Mockito.never()).onConnectionRefused(Mockito.any());
    }

    private ClientActorStateInfo mockAcceptedActorState() {
        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        ClientActorStateInfo clientActorState = mock(ClientActorStateInfo.class);
        given(clientActorState.getCurrentSessionCtx()).willReturn(ctx);
        SessionInfo sessionInfo = mock(SessionInfo.class);
        given(ctx.getSessionInfo()).willReturn(sessionInfo);
        given(ctx.getInitializerName()).willReturn("TCP");
        TopicAliasCtx topicAliasCtx = mock(TopicAliasCtx.class);
        given(ctx.getTopicAliasCtx()).willReturn(topicAliasCtx);
        given(topicAliasCtx.getMaxTopicAlias()).willReturn(1);
        return clientActorState;
    }

}
