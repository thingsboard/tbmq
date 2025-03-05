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
package org.thingsboard.mqtt.broker.service.mqtt;

import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttProperties;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionAcceptedMsg;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.TopicAliasCtx;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DefaultMqttMessageCreatorTest {

    DefaultMqttMessageCreator mqttMessageCreator = new DefaultMqttMessageCreator();

    @Before
    public void setUp() throws Exception {
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
        // setup mock
        ClientSessionCtx ctx = mock(ClientSessionCtx.class);

        ClientActorStateInfo clientActorState = mock(ClientActorStateInfo.class);
        when(clientActorState.getCurrentSessionCtx()).thenReturn(ctx);

        SessionInfo sessionInfo = mock(SessionInfo.class);
        when(ctx.getSessionInfo()).thenReturn(sessionInfo);
        when(ctx.getInitializerName()).thenReturn("TCP");

        TopicAliasCtx topicAliasCtx = mock(TopicAliasCtx.class);
        when(ctx.getTopicAliasCtx()).thenReturn(topicAliasCtx);
        when(topicAliasCtx.getMaxTopicAlias()).thenReturn(1);

        when(ctx.getClientId()).thenReturn("testClient");

        // test
        ConnectionAcceptedMsg connectionAcceptedMsg = new ConnectionAcceptedMsg(UUID.randomUUID(), true, null, 0, MqttProperties.NO_PROPERTIES);
        MqttConnAckMessage msg = mqttMessageCreator.createMqttConnAckMsg(clientActorState, connectionAcceptedMsg);

        Assert.assertNull(msg.variableHeader().properties().getProperty(BrokerConstants.AUTHENTICATION_METHOD_PROP_ID));
    }

}
