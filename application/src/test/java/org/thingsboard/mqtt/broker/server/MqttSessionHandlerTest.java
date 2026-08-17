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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.handler.ssl.NotSslRecordException;
import io.netty.util.Attribute;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.exception.ProtocolViolationException;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.limits.ThroughputQuotaService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.stats.ConnectionStats;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MqttSessionHandlerTest {

    static final InetSocketAddress REMOTE = new InetSocketAddress("10.0.0.7", 51000);

    MqttMessageGenerator mqttMessageGenerator;
    ConnectionStats connectionStats;
    MqttSessionHandler handler;
    ChannelHandlerContext ctx;
    Channel channel;
    Attribute<InetSocketAddress> addressAttr;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        mqttMessageGenerator = mock(MqttMessageGenerator.class);
        StatsManager statsManager = mock(StatsManager.class);
        connectionStats = mock(ConnectionStats.class);
        when(statsManager.getConnectionStats()).thenReturn(connectionStats);
        MqttHandlerCtx handlerCtx = new MqttHandlerCtx(
                mock(ClientMqttActorManager.class),
                mock(ClientLogger.class),
                mock(RateLimitService.class),
                mqttMessageGenerator,
                mock(ThroughputQuotaService.class),
                mock(TbMessageStatsReportClient.class),
                statsManager);
        handlerCtx.setMaxInFlightMsgs(1000);
        handler = new MqttSessionHandler(handlerCtx, null, BrokerConstants.TCP);

        ctx = mock(ChannelHandlerContext.class);
        channel = mock(Channel.class);
        addressAttr = mock(Attribute.class);
        when(ctx.channel()).thenReturn(channel);
        when(channel.attr(MqttSessionHandler.ADDRESS)).thenReturn(addressAttr);
    }

    // --- close-origin token: a short, stable classification (not prose) so logs stay greppable
    //     and the assertion tracks the value rather than the exact wording. ---

    @Test
    public void connectionCloseOrigin_brokerCloseRecorded_isTbmqToken() {
        assertThat(MqttSessionHandler.connectionCloseOrigin(true)).isEqualTo("TBMQ");
    }

    @Test
    public void connectionCloseOrigin_noBrokerClose_isPeerOrNetworkToken() {
        assertThat(MqttSessionHandler.connectionCloseOrigin(false)).isEqualTo("peer-or-network");
    }

    // --- remote-address resolution used by exceptionCaught's `address != null ? address : getAddress(ctx)` ---

    @Test
    public void getAddress_returnsChannelAttribute_whenPresent() {
        when(addressAttr.get()).thenReturn(REMOTE);
        assertThat(handler.getAddress(ctx)).isSameAs(REMOTE);
    }

    @Test
    public void getAddress_fallsBackToRemoteAddress_whenAttributeAbsent() {
        when(addressAttr.get()).thenReturn(null);
        InetSocketAddress fallback = new InetSocketAddress("192.168.1.9", 61000);
        when(channel.remoteAddress()).thenReturn(fallback);
        assertThat(handler.getAddress(ctx)).isSameAs(fallback);
    }

    // --- the enriched IOException WARN line: identifies the exception class and attributes the close.
    //     A fresh session has no broker-side close recorded, so the reset reads as peer-or-network,
    //     and the remote address is resolved via the getAddress fallback (handler.address is still null). ---

    @Test
    public void exceptionCaught_ioException_logsEnrichedWarnWithPeerOriginToken() {
        when(addressAttr.get()).thenReturn(REMOTE);
        // Give the session a channel so the trailing disconnect()->closeChannel() tears down cleanly
        // instead of hitting (and swallowing) an NPE on the not-yet-captured channel.
        ((ClientSessionCtx) ReflectionTestUtils.getField(handler, "clientSessionCtx")).setChannel(ctx);
        Logger logger = (Logger) LoggerFactory.getLogger(MqttSessionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            handler.exceptionCaught(ctx, new IOException("Connection reset"));

            assertThat(appender.list)
                    .as("IOException must log a single WARN identifying the exception, reset and close origin")
                    .anyMatch(e -> e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("IOException")
                            && e.getFormattedMessage().contains("Connection reset")
                            && e.getFormattedMessage().contains("closed-by=peer-or-network")
                            && e.getFormattedMessage().contains(REMOTE.toString()));
        } finally {
            logger.detachAppender(appender);
        }
    }

    // --- connAckAndCloseCtx must send the CONNACK and then close via ClientSessionCtx.closeChannel()
    //     (which records the broker-side close). Driven through the reachable "unsupported auth method" path. ---

    @Test
    public void channelRead_unsupportedAuthMethod_writesConnAckThenClosesChannel() {
        when(addressAttr.get()).thenReturn(REMOTE);
        MqttConnAckMessage connAck = mock(MqttConnAckMessage.class);
        when(mqttMessageGenerator.createMqttConnAckMsg(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_AUTHENTICATION_METHOD))
                .thenReturn(connAck);

        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.StringProperty(BrokerConstants.AUTHENTICATION_METHOD_PROP_ID, "UNSUPPORTED"));
        MqttConnectMessage connect = MqttMessageBuilders.connect()
                .clientId("client")
                .protocolVersion(MqttVersion.MQTT_5)
                .properties(properties)
                .build();

        handler.channelRead(ctx, connect);

        verify(ctx).writeAndFlush(connAck);
        verify(ctx).close(); // closeChannel() is the single chokepoint that records the broker-side close
    }

    // --- connectionError counting: a pre-establishment failure (clientId == null) is counted once as a
    //     connection error, however the exception is classified; a post-session error (clientId != null) is
    //     a disconnect (clientDisconnects), so connectionError must NOT be counted. ---

    @Test
    public void givenNoClientId_whenSslHandshakeException_thenConnectionErrorCounted() {
        handler.exceptionCaught(ctx, new RuntimeException("outer", new SSLHandshakeException("bad cert")));
        verify(connectionStats).onConnectionError();
    }

    @Test
    public void givenNoClientId_whenNotSslRecordException_thenConnectionErrorCounted() {
        handler.exceptionCaught(ctx, new RuntimeException("outer", new NotSslRecordException("plaintext")));
        verify(connectionStats).onConnectionError();
    }

    @Test
    public void givenNoClientId_whenIOException_thenConnectionErrorCounted() {
        handler.exceptionCaught(ctx, new IOException("reset"));
        verify(connectionStats).onConnectionError();
    }

    @Test
    public void givenNoClientId_whenProtocolViolationException_thenConnectionErrorCounted() {
        handler.exceptionCaught(ctx, new ProtocolViolationException("bad"));
        verify(connectionStats).onConnectionError();
    }

    @Test
    public void givenNoClientId_whenUnknownException_thenConnectionErrorCounted() {
        handler.exceptionCaught(ctx, new RuntimeException("boom"));
        verify(connectionStats).onConnectionError();
    }

    @Test
    public void givenEstablishedClientId_whenException_thenConnectionErrorNotCounted() {
        ReflectionTestUtils.setField(handler, "clientId", "client-a");
        handler.exceptionCaught(ctx, new IOException("reset"));
        verify(connectionStats, never()).onConnectionError();
    }
}
