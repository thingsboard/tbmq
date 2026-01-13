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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.ssl.SslHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.thingsboard.mqtt.broker.server.ip.IpAddressHandler;
import org.thingsboard.mqtt.broker.server.ip.ProxyIpAddressHandler;
import org.thingsboard.mqtt.broker.server.traffic.DuplexTrafficHandler;

import java.util.Objects;

@Slf4j
@Getter
public abstract class AbstractMqttChannelInitializer extends ChannelInitializer<SocketChannel> implements MqttChannelInitializer {

    @Value("${mqtt.version-3-1.max-client-id-length}")
    private int maxClientIdLength;
    @Value("${historical-data-report.enabled:true}")
    private boolean historicalDataReportEnabled;
    @Value("${listener.proxy_enabled:false}")
    private boolean globalProxyProtocolEnabled;

    protected final MqttHandlerFactory handlerFactory;

    public AbstractMqttChannelInitializer(MqttHandlerFactory handlerFactory) {
        this.handlerFactory = handlerFactory;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();

        if (isProxyProtocolEnabled()) {
            pipeline.addLast("proxy", new HAProxyMessageDecoder());
            pipeline.addLast("ipAdrHandler", new ProxyIpAddressHandler());
        } else {
            pipeline.addLast("ipAdrHandler", new IpAddressHandler());
        }

        SslHandler sslHandler = getSslHandler();
        if (sslHandler != null) {
            pipeline.addLast(sslHandler);
        }

        constructWsPipeline(ch);

        pipeline.addLast("decoder", new MqttDecoder(getMaxPayloadSize(), getMaxClientIdLength()));
        pipeline.addLast("encoder", MqttEncoder.INSTANCE);

        if (historicalDataReportEnabled) {
            pipeline.addLast(new DuplexTrafficHandler(handlerFactory.getTbMessageStatsReportClient()));
        }

        MqttSessionHandler handler = handlerFactory.create(sslHandler, getChannelInitializerName());

        pipeline.addLast(handler);
        ch.closeFuture().addListener(handler);

        log.debug("[{}] Created {} channel for IP {}.", handler.getSessionId(), getChannelInitializerName(), ch.localAddress());
    }

    protected void constructWsPipeline(SocketChannel ch) {

    }

    private boolean isProxyProtocolEnabled() {
        return Objects.requireNonNullElseGet(isListenerProxyProtocolEnabled(), () -> globalProxyProtocolEnabled);
    }

}
