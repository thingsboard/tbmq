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

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.mqtt.broker.server.MqttSessionHandler;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

public class ForwardHeadersIpAddressHandlerTest {

    private static final InetSocketAddress SOCKET_ADDR = new InetSocketAddress("172.18.0.1", 54321);
    private static final String X_FORWARDED_FOR = ForwardHeadersIpAddressHandler.X_FORWARDED_FOR;
    private static final String X_REAL_IP = ForwardHeadersIpAddressHandler.X_REAL_IP;

    private EmbeddedChannel channel;

    @Before
    public void setUp() {
        channel = new EmbeddedChannel(new ForwardHeadersIpAddressHandler());
        channel.attr(MqttSessionHandler.ADDRESS).set(SOCKET_ADDR);
    }

    @After
    public void tearDown() {
        channel.finishAndReleaseAll();
    }

    private static FullHttpRequest upgradeRequest() {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/mqtt", Unpooled.EMPTY_BUFFER);
    }

    @Test
    public void givenSingleXffIp_whenChannelRead_thenAddressOverridden() {
        FullHttpRequest req = upgradeRequest();
        req.headers().set(X_FORWARDED_FOR, "203.0.113.7");

        channel.writeInbound(req);

        InetSocketAddress addr = channel.attr(MqttSessionHandler.ADDRESS).get();
        assertThat(addr.getHostString()).isEqualTo("203.0.113.7");
        assertThat(addr.getPort()).isZero();
        assertThat(channel.pipeline().get(ForwardHeadersIpAddressHandler.class)).isNull();
    }

    @Test
    public void givenXffChain_whenChannelRead_thenLeftmostIpWins() {
        FullHttpRequest req = upgradeRequest();
        req.headers().set(X_FORWARDED_FOR, "203.0.113.7, 10.0.0.5, 10.0.0.1");

        channel.writeInbound(req);

        assertThat(channel.attr(MqttSessionHandler.ADDRESS).get().getHostString()).isEqualTo("203.0.113.7");
    }

    @Test
    public void givenOnlyXRealIp_whenChannelRead_thenAddressOverridden() {
        FullHttpRequest req = upgradeRequest();
        req.headers().set(X_REAL_IP, "198.51.100.9");

        channel.writeInbound(req);

        assertThat(channel.attr(MqttSessionHandler.ADDRESS).get().getHostString()).isEqualTo("198.51.100.9");
    }

    @Test
    public void givenBothHeaders_whenChannelRead_thenXffWins() {
        FullHttpRequest req = upgradeRequest();
        req.headers().set(X_FORWARDED_FOR, "203.0.113.7");
        req.headers().set(X_REAL_IP, "198.51.100.9");

        channel.writeInbound(req);

        assertThat(channel.attr(MqttSessionHandler.ADDRESS).get().getHostString()).isEqualTo("203.0.113.7");
    }

    @Test
    public void givenEmptyXffAndNoXRealIp_whenChannelRead_thenAddressUnchanged() {
        FullHttpRequest req = upgradeRequest();
        req.headers().set(X_FORWARDED_FOR, "");

        channel.writeInbound(req);

        assertThat(channel.attr(MqttSessionHandler.ADDRESS).get()).isEqualTo(SOCKET_ADDR);
    }

    @Test
    public void givenUnknownLiteral_whenChannelRead_thenAddressUnchanged() {
        FullHttpRequest req = upgradeRequest();
        req.headers().set(X_FORWARDED_FOR, "unknown");

        channel.writeInbound(req);

        assertThat(channel.attr(MqttSessionHandler.ADDRESS).get()).isEqualTo(SOCKET_ADDR);
    }

    @Test
    public void givenIpv6Xff_whenChannelRead_thenAddressOverridden() {
        FullHttpRequest req = upgradeRequest();
        req.headers().set(X_FORWARDED_FOR, "2001:db8::1");

        channel.writeInbound(req);

        assertThat(channel.attr(MqttSessionHandler.ADDRESS).get().getAddress().getHostAddress()).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    public void givenIpv6XffInBrackets_whenChannelRead_thenAddressOverridden() {
        FullHttpRequest req = upgradeRequest();
        req.headers().set(X_FORWARDED_FOR, "[2001:db8::1]");

        channel.writeInbound(req);

        assertThat(channel.attr(MqttSessionHandler.ADDRESS).get().getAddress().getHostAddress()).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    public void givenMalformedIp_whenChannelRead_thenAddressUnchanged() {
        FullHttpRequest req = upgradeRequest();
        req.headers().set(X_FORWARDED_FOR, "not-an-ip");

        channel.writeInbound(req);

        assertThat(channel.attr(MqttSessionHandler.ADDRESS).get()).isEqualTo(SOCKET_ADDR);
    }

    @Test
    public void givenNoHeaders_whenChannelRead_thenAddressUnchangedAndHandlerRemoved() {
        FullHttpRequest req = upgradeRequest();

        channel.writeInbound(req);

        assertThat(channel.attr(MqttSessionHandler.ADDRESS).get()).isEqualTo(SOCKET_ADDR);
        assertThat(channel.pipeline().get(ForwardHeadersIpAddressHandler.class)).isNull();
    }

    @Test
    public void givenNonHttpMessageFirst_whenChannelRead_thenHandlerStaysInstalled() {
        channel.writeInbound("not-a-http-request");

        assertThat(channel.attr(MqttSessionHandler.ADDRESS).get()).isEqualTo(SOCKET_ADDR);
        assertThat(channel.pipeline().get(ForwardHeadersIpAddressHandler.class)).isNotNull();
    }
}
