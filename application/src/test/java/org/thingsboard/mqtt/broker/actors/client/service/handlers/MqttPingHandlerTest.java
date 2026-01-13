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
package org.thingsboard.mqtt.broker.actors.client.service.handlers;

import io.netty.channel.ChannelHandlerContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class MqttPingHandlerTest {

    MqttMessageGenerator mqttMessageGenerator;
    MqttPingHandler mqttPingHandler;

    @Before
    public void setUp() {
        mqttMessageGenerator = mock(MqttMessageGenerator.class);
        mqttPingHandler = spy(new MqttPingHandler(mqttMessageGenerator));
    }

    @Test
    public void testProcess() {
        ClientSessionCtx ctx = mock(ClientSessionCtx.class);
        ChannelHandlerContext channelHandlerContext = mock(ChannelHandlerContext.class);
        doReturn(channelHandlerContext).when(ctx).getChannel();
        mqttPingHandler.process(ctx);
        verify(mqttMessageGenerator, times(1)).createPingRespMsg();
    }
}
