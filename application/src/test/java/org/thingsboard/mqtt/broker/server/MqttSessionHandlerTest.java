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
package org.thingsboard.mqtt.broker.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.NotSslRecordException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.exception.ProtocolViolationException;
import org.thingsboard.mqtt.broker.service.stats.ConnectionErrorType;
import org.thingsboard.mqtt.broker.service.stats.ConnectionStats;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.Silent.class)
public class MqttSessionHandlerTest {

    private ConnectionStats connectionStats;
    private MqttSessionHandler handler;

    @Before
    public void setUp() {
        MqttHandlerCtx ctx = mock(MqttHandlerCtx.class);
        StatsManager statsManager = mock(StatsManager.class);
        connectionStats = mock(ConnectionStats.class);
        given(ctx.getStatsManager()).willReturn(statsManager);
        given(statsManager.getConnectionStats()).willReturn(connectionStats);
        given(ctx.getActorManager()).willReturn(mock(ClientMqttActorManager.class));
        handler = new MqttSessionHandler(ctx, null, "TCP");
    }

    @Test
    public void givenNoClientId_whenSslHandshakeException_thenConnectionErrorSslHandshakeCounted() {
        Throwable cause = new RuntimeException("outer", new SSLHandshakeException("bad cert"));
        handler.exceptionCaught(mock(ChannelHandlerContext.class), cause);
        verify(connectionStats).onConnectionError(ConnectionErrorType.SSL_HANDSHAKE);
    }

    @Test
    public void givenNoClientId_whenNotSslRecordException_thenConnectionErrorNotSslRecordCounted() {
        Throwable cause = new RuntimeException("outer", new NotSslRecordException("plaintext"));
        handler.exceptionCaught(mock(ChannelHandlerContext.class), cause);
        verify(connectionStats).onConnectionError(ConnectionErrorType.NOT_SSL_RECORD);
    }

    @Test
    public void givenNoClientId_whenIOException_thenConnectionErrorIoCounted() {
        handler.exceptionCaught(mock(ChannelHandlerContext.class), new IOException("reset"));
        verify(connectionStats).onConnectionError(ConnectionErrorType.IO);
    }

    @Test
    public void givenNoClientId_whenProtocolViolationException_thenConnectionErrorProtocolViolationCounted() {
        handler.exceptionCaught(mock(ChannelHandlerContext.class), new ProtocolViolationException("bad"));
        verify(connectionStats).onConnectionError(ConnectionErrorType.PROTOCOL_VIOLATION);
    }

    @Test
    public void givenNoClientId_whenUnknownException_thenConnectionErrorOtherCounted() {
        handler.exceptionCaught(mock(ChannelHandlerContext.class), new RuntimeException("boom"));
        verify(connectionStats).onConnectionError(ConnectionErrorType.OTHER);
    }

    @Test
    public void givenEstablishedClientId_whenException_thenConnectionErrorNotCounted() {
        // A post-session channel error (clientId != null) is a disconnect (clientDisconnects), not a
        // connection-establishment error — connectionError must NOT be counted.
        ReflectionTestUtils.setField(handler, "clientId", "client-a");
        handler.exceptionCaught(mock(ChannelHandlerContext.class), new IOException("reset"));
        verify(connectionStats, never()).onConnectionError(any());
    }
}
