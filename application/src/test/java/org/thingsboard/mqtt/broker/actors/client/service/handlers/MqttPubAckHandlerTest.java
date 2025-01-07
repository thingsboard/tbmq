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
package org.thingsboard.mqtt.broker.actors.client.service.handlers;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.retransmission.RetransmissionService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class MqttPubAckHandlerTest {

    MsgPersistenceManager msgPersistenceManager;
    RetransmissionService retransmissionService;
    MqttPubAckHandler mqttPubAckHandler;

    @Before
    public void setUp() {
        msgPersistenceManager = mock(MsgPersistenceManager.class);
        retransmissionService = mock(RetransmissionService.class);
        mqttPubAckHandler = spy(new MqttPubAckHandler(msgPersistenceManager, retransmissionService));
    }

    @Test
    public void testProcessPersistent() {
        ClientSessionCtx ctx = new ClientSessionCtx();
        ctx.setSessionInfo(SessionInfo.builder().sessionExpiryInterval(1).build());
        mqttPubAckHandler.process(ctx, 1);
        verify(msgPersistenceManager, times(1)).processPubAck(ctx, 1);
        verify(retransmissionService, times(1)).onPubAckReceived(ctx, 1);
    }

    @Test
    public void testProcess() {
        ClientSessionCtx ctx = new ClientSessionCtx();
        ctx.setSessionInfo(SessionInfo.builder().cleanStart(true).sessionExpiryInterval(0).build());
        mqttPubAckHandler.process(ctx, 1);
        verify(retransmissionService, times(1)).onPubAckReceived(ctx, 1);
    }
}
