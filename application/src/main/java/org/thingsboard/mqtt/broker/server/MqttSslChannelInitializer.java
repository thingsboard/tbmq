/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.ssl.SslHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Qualifier("SslChannelInitializer")
@RequiredArgsConstructor
public class MqttSslChannelInitializer extends ChannelInitializer<SocketChannel> {
    @Value("${mqtt.version-3-1.max-client-id-length}")
    private int maxClientIdLength;

    private final MqttSslServerContext context;
    private final MqttHandlerFactory handlerFactory;


    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        SslHandler sslHandler = context.getSslHandlerProvider().getSslHandler();
        pipeline.addLast(sslHandler);

        pipeline.addLast("decoder", new MqttDecoder(context.getMaxPayloadSize(), maxClientIdLength));
        pipeline.addLast("encoder", MqttEncoder.INSTANCE);

        MqttSessionHandler handler = handlerFactory.create(sslHandler);

        pipeline.addLast(handler);
        ch.closeFuture().addListener(handler);

        if (log.isDebugEnabled()) {
            log.debug("[{}] Created SSL channel for IP {}.", handler.getSessionId(), ch.localAddress());
        }
    }

}
