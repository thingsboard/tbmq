/**
 * Copyright © 2016-2024 The Thingsboard Authors
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

import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.server.wshandler.WsBinaryFrameHandler;
import org.thingsboard.mqtt.broker.server.wshandler.WsByteBufEncoder;
import org.thingsboard.mqtt.broker.server.wshandler.WsContinuationFrameHandler;
import org.thingsboard.mqtt.broker.server.wshandler.WsTextFrameHandler;

@Slf4j
public abstract class AbstractMqttWsChannelInitializer extends AbstractMqttChannelInitializer {

    public AbstractMqttWsChannelInitializer(MqttHandlerFactory handlerFactory) {
        super(handlerFactory);
    }

    @Override
    public void initChannel(SocketChannel ch) {
        super.initChannel(ch);
    }

    protected abstract String getSubprotocols();

    @Override
    protected void constructWsPipeline(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(BrokerConstants.WS_MAX_CONTENT_LENGTH));
        pipeline.addLast(new WebSocketServerProtocolHandler(BrokerConstants.WS_PATH, getSubprotocols()));

        pipeline.addLast(new WsBinaryFrameHandler());
        pipeline.addLast(new WsContinuationFrameHandler());
        pipeline.addLast(new WsTextFrameHandler());

        pipeline.addLast(new WsByteBufEncoder());
    }
}
