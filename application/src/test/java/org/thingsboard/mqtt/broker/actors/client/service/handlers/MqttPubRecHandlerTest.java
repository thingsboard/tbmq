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

import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPubRecMsg;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.retransmission.RetransmissionService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MqttPubRecHandlerTest {

    MsgPersistenceManager msgPersistenceManager;
    RetransmissionService retransmissionService;
    MqttMessageGenerator mqttMessageGenerator;
    MqttPubRecHandler mqttPubRecHandler;
    ClientSessionCtx ctx;

    @Before
    public void setUp() {
        msgPersistenceManager = mock(MsgPersistenceManager.class);
        retransmissionService = mock(RetransmissionService.class);
        mqttMessageGenerator = mock(MqttMessageGenerator.class);
        mqttPubRecHandler = spy(new MqttPubRecHandler(msgPersistenceManager, retransmissionService, mqttMessageGenerator));

        ctx = mock(ClientSessionCtx.class);
    }

    @Test
    public void testProcessPersistent() {
        when(ctx.getSessionInfo()).thenReturn(getSessionInfo(false, 1));
        mqttPubRecHandler.process(ctx, newMqttPubRecMsg(MqttReasonCodes.PubRec.SUCCESS));
        verify(msgPersistenceManager, times(1)).processPubRec(eq(ctx), eq(1));
    }

    @Test
    public void testProcessNonPersistent() {
        when(ctx.getSessionInfo()).thenReturn(getSessionInfo(true, 0));

        mqttPubRecHandler.process(ctx, newMqttPubRecMsg(MqttReasonCodes.PubRec.SUCCESS));
        verify(mqttMessageGenerator, times(1)).createPubRelMsg(eq(1), eq(null));
        verify(retransmissionService, times(1)).onPubRecReceived(eq(ctx), any());
    }

    @Test
    public void givenSuccessReasonCode_whenCheckIfReasonCodeFailure_thenReturnFalse() {
        boolean result = mqttPubRecHandler.reasonCodeFailure(newMqttPubRecMsg(MqttReasonCodes.PubRec.SUCCESS));
        assertFalse(result);
    }

    @Test
    public void givenFailureReasonCode_whenCheckIfReasonCodeFailure_thenReturnTrue() {
        boolean result = mqttPubRecHandler.reasonCodeFailure(newMqttPubRecMsg(MqttReasonCodes.PubRec.UNSPECIFIED_ERROR));
        assertTrue(result);
    }

    @Test
    public void givenOtherFailureReasonCode_whenCheckIfReasonCodeFailure_thenReturnTrue() {
        boolean result = mqttPubRecHandler.reasonCodeFailure(newMqttPubRecMsg(MqttReasonCodes.PubRec.PAYLOAD_FORMAT_INVALID));
        assertTrue(result);
    }

    private SessionInfo getSessionInfo(boolean cleanStart, int sessionExpiryInterval) {
        return SessionInfo.builder().cleanStart(cleanStart).sessionExpiryInterval(sessionExpiryInterval).build();
    }

    private MqttPubRecMsg newMqttPubRecMsg(MqttReasonCodes.PubRec reasonCode) {
        return new MqttPubRecMsg(UUID.randomUUID(), 1, MqttProperties.NO_PROPERTIES, reasonCode);
    }
}
