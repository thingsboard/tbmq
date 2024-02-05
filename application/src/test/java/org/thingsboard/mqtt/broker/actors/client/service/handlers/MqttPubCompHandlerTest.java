/**
 * Copyright © 2016-2024 The Thingsboard Authors
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

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class MqttPubCompHandlerTest {

    MsgPersistenceManager msgPersistenceManager;
    RetransmissionService retransmissionService;
    MqttPubCompHandler mqttPubCompHandler;

    @Before
    public void setUp() {
        msgPersistenceManager = mock(MsgPersistenceManager.class);
        retransmissionService = mock(RetransmissionService.class);
        mqttPubCompHandler = spy(new MqttPubCompHandler(msgPersistenceManager, retransmissionService));
    }

    @Test
    public void testProcessPersistent() {
        ClientSessionCtx ctx = new ClientSessionCtx(UUID.randomUUID(), null, 1);
        ctx.setSessionInfo(SessionInfo.builder().sessionExpiryInterval(1).build());
        mqttPubCompHandler.process(ctx, 1);
        verify(msgPersistenceManager, times(1)).processPubComp(ctx, 1);
        verify(retransmissionService, times(1)).onPubCompReceived(ctx, 1);
    }

    @Test
    public void testProcess() {
        ClientSessionCtx ctx = new ClientSessionCtx(UUID.randomUUID(), null, 1);
        ctx.setSessionInfo(SessionInfo.builder().cleanStart(true).sessionExpiryInterval(0).build());
        mqttPubCompHandler.process(ctx, 1);
        verify(retransmissionService, times(1)).onPubCompReceived(ctx, 1);
    }
}
