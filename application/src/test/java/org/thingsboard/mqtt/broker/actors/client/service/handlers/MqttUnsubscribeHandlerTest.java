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
package org.thingsboard.mqtt.broker.actors.client.service.handlers;

import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttUnsubscribeMsg;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttUnsubscribeHandlerTest {

    MqttMessageGenerator mqttMessageGenerator;
    ClientSubscriptionService clientSubscriptionService;
    MqttUnsubscribeHandler mqttUnsubscribeHandler;

    ClientSessionCtx ctx;

    @BeforeEach
    void setUp() {
        mqttMessageGenerator = mock(MqttMessageGenerator.class);
        clientSubscriptionService = mock(ClientSubscriptionService.class);
        mqttUnsubscribeHandler = spy(new MqttUnsubscribeHandler(mqttMessageGenerator, clientSubscriptionService));

        ctx = mock(ClientSessionCtx.class);
        ChannelHandlerContext handlerContext = mock(ChannelHandlerContext.class);
        when(ctx.getChannel()).thenReturn(handlerContext);
    }

    @Test
    void testProcess() {
        SessionInfo sessionInfo = mock(SessionInfo.class);
        when(ctx.getSessionInfo()).thenReturn(sessionInfo);
        ClientInfo clientInfo = mock(ClientInfo.class);
        when(sessionInfo.getClientInfo()).thenReturn(clientInfo);

        mqttUnsubscribeHandler.process(ctx, new MqttUnsubscribeMsg(UUID.randomUUID(), 1, List.of("topic")));

        verify(mqttMessageGenerator, times(1)).createUnSubAckMessage(eq(1));
        verify(clientSubscriptionService, times(1)).unsubscribeAndPersist(any(), any(), any());
    }
}