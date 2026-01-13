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
package org.thingsboard.mqtt.broker.service.mqtt.persistence;

import io.netty.channel.ChannelHandlerContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.GenericClientSessionCtx;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.dao.client.GenericClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.session.AwaitingPubRelPacketsCtx;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GenericClientSessionCtxManagerImplTest {

    GenericClientSessionCtxService genericClientSessionCtxService;
    ClientSessionCtxService clientSessionCtxService;
    MqttMessageGenerator mqttMessageGenerator;
    GenericClientSessionCtxManagerImpl genericClientSessionCtxManager;

    ClientSessionCtx ctx;
    AwaitingPubRelPacketsCtx awaitingPubRelPacketsCtx;

    @Before
    public void setUp() throws Exception {
        genericClientSessionCtxService = mock(GenericClientSessionCtxService.class);
        clientSessionCtxService = mock(ClientSessionCtxService.class);
        mqttMessageGenerator = mock(MqttMessageGenerator.class);
        genericClientSessionCtxManager = spy(new GenericClientSessionCtxManagerImpl(
                genericClientSessionCtxService, clientSessionCtxService, mqttMessageGenerator));

        ctx = mock(ClientSessionCtx.class);

        awaitingPubRelPacketsCtx = mock(AwaitingPubRelPacketsCtx.class);
        when(ctx.getAwaitingPubRelPacketsCtx()).thenReturn(awaitingPubRelPacketsCtx);
    }

    @Test
    public void testResendPersistedPubRelMessages() {
        ChannelHandlerContext channelHandlerContext = mock(ChannelHandlerContext.class);
        when(ctx.getChannel()).thenReturn(channelHandlerContext);

        GenericClientSessionCtx clientSessionCtx = getClientSessionCtx();
        when(genericClientSessionCtxService.findGenericClientSessionCtx(any())).thenReturn(Optional.of(clientSessionCtx));

        AwaitingPubRelPacketsCtx awaitingPubRelPacketsCtx = new AwaitingPubRelPacketsCtx();
        when(ctx.getAwaitingPubRelPacketsCtx()).thenReturn(awaitingPubRelPacketsCtx);

        genericClientSessionCtxManager.resendPersistedPubRelMessages(ctx);

        Collection<AwaitingPubRelPacketsCtx.QoS2PubRelPacketInfo> awaitingPacketIds = awaitingPubRelPacketsCtx.getAwaitingPackets();
        Assertions.assertTrue(
                awaitingPacketIds.contains(getQos2PacketInfo(1)) &&
                        awaitingPacketIds.contains(getQos2PacketInfo(2)) &&
                        awaitingPacketIds.contains(getQos2PacketInfo(3))
        );

        verify(channelHandlerContext, times(3)).write(any());
        verify(channelHandlerContext).flush();
    }

    @Test
    public void testSaveAwaitingQoS2Packets() {
        when(awaitingPubRelPacketsCtx.getAwaitingPackets()).thenReturn(Collections.emptyList());

        genericClientSessionCtxManager.saveAwaitingQoS2Packets(ctx);
        verify(genericClientSessionCtxService, times(1)).saveGenericClientSessionCtx(any());
    }

    @Test
    public void testClearAwaitingQoS2Packets() {
        genericClientSessionCtxManager.clearAwaitingQoS2Packets("id");
        verify(genericClientSessionCtxService, times(1)).deleteGenericClientSessionCtx(eq("id"));
    }

    @Test
    public void testDestroyDoNothing() {
        when(clientSessionCtxService.getAllClientSessionCtx()).thenReturn(Collections.emptyList());

        genericClientSessionCtxManager.destroy();

        verify(genericClientSessionCtxService, never()).saveAllGenericClientSessionCtx(any());
    }

    @Test
    public void testDestroy() {
        when(clientSessionCtxService.getAllClientSessionCtx()).thenReturn(List.of(ctx));

        SessionInfo sessionInfo = mock(SessionInfo.class);
        when(ctx.getSessionInfo()).thenReturn(sessionInfo);
        when(sessionInfo.isPersistent()).thenReturn(true);

        when(awaitingPubRelPacketsCtx.getAwaitingPackets()).thenReturn(Collections.emptyList());

        genericClientSessionCtxManager.destroy();

        verify(genericClientSessionCtxService, times(1)).saveAllGenericClientSessionCtx(any());
    }

    private AwaitingPubRelPacketsCtx.QoS2PubRelPacketInfo getQos2PacketInfo(int packetId) {
        return new AwaitingPubRelPacketsCtx.QoS2PubRelPacketInfo(packetId, true);
    }

    private GenericClientSessionCtx getClientSessionCtx() {
        return GenericClientSessionCtx
                .builder()
                .clientId("clientId")
                .lastUpdatedTime(System.currentTimeMillis())
                .qos2PublishPacketIds(Set.of(1, 2, 3))
                .build();
    }
}