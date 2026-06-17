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
package org.thingsboard.mqtt.broker.server.ip;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.haproxy.HAProxyCommand;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;
import io.netty.handler.codec.haproxy.HAProxyProxiedProtocol;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.mqtt.broker.server.MqttSessionHandler;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

public class ProxyIpAddressHandlerTest {

    private EmbeddedChannel channel;

    @Before
    public void setUp() {
        channel = new EmbeddedChannel(new ProxyIpAddressHandler());
    }

    @After
    public void tearDown() {
        channel.finishAndReleaseAll();
    }

    // PROXY command with real client addresses (a relayed client connection).
    private static HAProxyMessage proxyMessage() {
        return new HAProxyMessage(HAProxyProtocolVersion.V2, HAProxyCommand.PROXY,
                HAProxyProxiedProtocol.TCP4, "203.0.113.7", "10.0.0.5", 51234, 8883);
    }

    // LOCAL command with no addresses — models a load balancer health-check connection (GH#322).
    private static HAProxyMessage localMessage() {
        return new HAProxyMessage(HAProxyProtocolVersion.V2, HAProxyCommand.LOCAL,
                HAProxyProxiedProtocol.UNKNOWN, null, null, 0, 0);
    }

    @Test
    public void givenProxyCommand_whenChannelRead_thenAddressSetAndHandlerRemoved() {
        channel.writeInbound(proxyMessage());

        InetSocketAddress addr = channel.attr(MqttSessionHandler.ADDRESS).get();
        assertThat(addr).isNotNull();
        assertThat(addr.getHostString()).isEqualTo("203.0.113.7");
        assertThat(addr.getPort()).isEqualTo(51234);
        assertThat(channel.isActive()).isTrue();
        assertThat(channel.pipeline().get(ProxyIpAddressHandler.class)).isNull();
    }

    // GH#322: a LOCAL command (e.g. HAProxy/Traefik health check) must NOT close the connection.
    // Before the fix the handler called ctx.close() here, failing TLS health checks and taking the
    // whole backend DOWN. Per the PROXY protocol spec the receiver must accept it and use the real
    // socket endpoints (left to MqttSessionHandler#getAddress, so ADDRESS stays unset here).
    @Test
    public void givenLocalCommand_whenChannelRead_thenConnectionStaysOpenAndHandlerRemoved() {
        channel.writeInbound(localMessage());

        assertThat(channel.isActive()).isTrue();
        assertThat(channel.attr(MqttSessionHandler.ADDRESS).get()).isNull();
        assertThat(channel.pipeline().get(ProxyIpAddressHandler.class)).isNull();
    }

    // After a LOCAL command the handler removes itself, so subsequent bytes (e.g. the TLS ClientHello
    // of the health check) flow downstream instead of hitting the "unexpected msg" branch and closing.
    @Test
    public void givenLocalCommandThenBytes_whenChannelRead_thenBytesForwardedAndConnectionStaysOpen() {
        channel.writeInbound(localMessage());
        ByteBuf tlsClientHelloBytes = Unpooled.wrappedBuffer(new byte[]{0x16, 0x03, 0x01, 0x00, 0x05});
        channel.writeInbound(tlsClientHelloBytes);

        assertThat(channel.isActive()).isTrue();
        ByteBuf forwarded = channel.readInbound();
        assertThat(forwarded).isNotNull();
        assertThat(forwarded.readableBytes()).isEqualTo(5);
        forwarded.release();
    }

    @Test
    public void givenNonHAProxyMessage_whenChannelRead_thenConnectionClosed() {
        channel.writeInbound("not-a-haproxy-message");

        assertThat(channel.isActive()).isFalse();
    }
}
