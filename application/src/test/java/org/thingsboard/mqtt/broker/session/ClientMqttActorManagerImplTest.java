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
package org.thingsboard.mqtt.broker.session;

import org.junit.Before;
import org.junit.Test;
import org.thingsboard.mqtt.broker.actors.ActorSystemContext;
import org.thingsboard.mqtt.broker.actors.TbActorId;
import org.thingsboard.mqtt.broker.actors.TbActorRef;
import org.thingsboard.mqtt.broker.actors.TbActorSystem;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ClientMqttActorManagerImplTest {

    private static final String CLIENT_ID = "testClient";

    private TbActorRef actorRef;
    private TbActorSystem actorSystem;
    private ClientMqttActorManagerImpl manager;

    @Before
    public void setUp() {
        actorRef = mock(TbActorRef.class);
        actorSystem = mock(TbActorSystem.class);
        when(actorSystem.getActor(any(TbActorId.class))).thenReturn(actorRef);
        manager = new ClientMqttActorManagerImpl(mock(ActorSystemContext.class), actorSystem);
    }

    @Test
    public void givenClientInitiatedDisconnectReasons_whenDisconnect_thenRoutedToNormalPriorityQueue() {
        for (DisconnectReasonType type : DisconnectReasonType.CLIENT_INITIATED_IN_STREAM) {
            reset(actorRef);
            MqttDisconnectMsg msg = new MqttDisconnectMsg(UUID.randomUUID(), new DisconnectReason(type));

            manager.disconnect(CLIENT_ID, msg);

            verify(actorRef).tell(msg);
            verify(actorRef, never()).tellWithHighPriority(any());
        }
    }

    @Test
    public void givenServerInitiatedDisconnectReasons_whenDisconnect_thenRoutedToHighPriorityQueue() {
        Set<DisconnectReasonType> serverInitiated = EnumSet.allOf(DisconnectReasonType.class);
        serverInitiated.removeAll(DisconnectReasonType.CLIENT_INITIATED_IN_STREAM);

        for (DisconnectReasonType type : serverInitiated) {
            reset(actorRef);
            MqttDisconnectMsg msg = new MqttDisconnectMsg(UUID.randomUUID(), new DisconnectReason(type));

            manager.disconnect(CLIENT_ID, msg);

            verify(actorRef).tellWithHighPriority(msg);
            verify(actorRef, never()).tell(any());
        }
    }

    @Test
    public void givenMissingActor_whenDisconnect_thenNoInteractions() {
        when(actorSystem.getActor(any(TbActorId.class))).thenReturn(null);
        for (DisconnectReasonType type : Arrays.asList(
                DisconnectReasonType.ON_DISCONNECT_MSG,
                DisconnectReasonType.ON_ERROR,
                DisconnectReasonType.ON_CHANNEL_CLOSED)) {
            MqttDisconnectMsg msg = new MqttDisconnectMsg(UUID.randomUUID(), new DisconnectReason(type));
            manager.disconnect(CLIENT_ID, msg);
        }
        verify(actorRef, never()).tell(any());
        verify(actorRef, never()).tellWithHighPriority(any());
    }
}
