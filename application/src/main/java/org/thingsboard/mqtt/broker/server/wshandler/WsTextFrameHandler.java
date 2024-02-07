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
package org.thingsboard.mqtt.broker.server.wshandler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.server.MqttSessionHandler;

import java.net.InetSocketAddress;

@Slf4j
public class WsTextFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final TextWebSocketFrame msg) throws Exception {
        if (log.isDebugEnabled()) {
            var address = ctx.channel().attr(MqttSessionHandler.ADDRESS).get();
            if (address == null) {
                address = (InetSocketAddress) ctx.channel().remoteAddress();
            }
            log.debug("Received illegal Text WebSocket frames from IP:{}. Disconnecting the client.", address);
        }
        ctx.channel().disconnect();
    }
}
