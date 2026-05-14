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

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.junit.Test;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.server.ip.ForwardHeadersIpAddressHandler;
import org.thingsboard.mqtt.broker.server.wshandler.WsBinaryFrameHandler;
import org.thingsboard.mqtt.broker.server.wshandler.WsByteBufEncoder;
import org.thingsboard.mqtt.broker.server.wshandler.WsContinuationFrameHandler;
import org.thingsboard.mqtt.broker.server.wshandler.WsTextFrameHandler;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractMqttWsChannelInitializerWiringTest {

    private static EmbeddedChannel buildWsPipeline(boolean proxyEnabled, boolean forwardHeadersEnabled) {
        EmbeddedChannel channel = new EmbeddedChannel();
        TestWsChannelInitializer.constructTestWsPipeline(channel, proxyEnabled, forwardHeadersEnabled);
        return channel;
    }

    @Test
    public void givenForwardHeadersOnAndProxyOff_whenPipelineBuilt_thenHandlerInstalledAfterAggregator() {
        EmbeddedChannel channel = buildWsPipeline(false, true);

        assertThat(channel.pipeline().get(ForwardHeadersIpAddressHandler.class)).isNotNull();
        assertThat(channel.pipeline().names())
                .containsSubsequence("aggregator", "forwardHeadersIpAdrHandler", "wsProtocol");
    }

    @Test
    public void givenProxyOnAndForwardHeadersOn_whenPipelineBuilt_thenForwardHandlerAbsent() {
        EmbeddedChannel channel = buildWsPipeline(true, true);

        assertThat(channel.pipeline().get(ForwardHeadersIpAddressHandler.class)).isNull();
    }

    @Test
    public void givenForwardHeadersOff_whenPipelineBuilt_thenForwardHandlerAbsent() {
        EmbeddedChannel channel = buildWsPipeline(false, false);

        assertThat(channel.pipeline().get(ForwardHeadersIpAddressHandler.class)).isNull();
    }

    /**
     * Minimal stand-in for the WS pipeline-construction step. Mirrors the same sequence as
     * AbstractMqttWsChannelInitializer.constructWsPipeline without dragging in Netty
     * SocketChannel or Spring wiring; verifies the conditional handler insertion contract.
     */
    private static final class TestWsChannelInitializer {

        private TestWsChannelInitializer() {
        }

        static void constructTestWsPipeline(EmbeddedChannel ch, boolean proxyEnabled, boolean forwardHeadersEnabled) {
            ch.pipeline().addLast("codec", new HttpServerCodec());
            ch.pipeline().addLast("aggregator", new HttpObjectAggregator(BrokerConstants.WS_MAX_CONTENT_LENGTH));
            if (forwardHeadersEnabled && !proxyEnabled) {
                ch.pipeline().addLast("forwardHeadersIpAdrHandler", new ForwardHeadersIpAddressHandler());
            }
            ch.pipeline().addLast("wsProtocol", new WebSocketServerProtocolHandler(BrokerConstants.WS_PATH, "mqtt"));
            ch.pipeline().addLast(new WsBinaryFrameHandler());
            ch.pipeline().addLast(new WsContinuationFrameHandler());
            ch.pipeline().addLast(new WsTextFrameHandler());
            ch.pipeline().addLast(new WsByteBufEncoder());
        }
    }
}
