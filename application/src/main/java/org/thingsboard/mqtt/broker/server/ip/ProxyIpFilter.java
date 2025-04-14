/**
 * Copyright © 2016-2025 The Thingsboard Authors
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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.server.MqttSessionHandler;

import java.net.InetSocketAddress;

@Slf4j
public class ProxyIpFilter extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        log.trace("[{}] Received msg: {}", ctx.channel().id(), msg);
        if (msg instanceof HAProxyMessage proxyMsg) {
            if (proxyMsg.sourceAddress() != null && proxyMsg.sourcePort() > 0) {
                InetSocketAddress address = new InetSocketAddress(proxyMsg.sourceAddress(), proxyMsg.sourcePort());
                log.trace("[{}] Setting address: {}", ctx.channel().id(), address);
                ctx.channel().attr(MqttSessionHandler.ADDRESS).set(address);
                // We no longer need this channel in the pipeline. Similar to HAProxyMessageDecoder
                ctx.pipeline().remove(this);
            } else {
                log.trace("Received local health-check connection message: {}", proxyMsg);
                ctx.close();
            }
        } else {
            log.warn("[{}] Received unexpected msg, expected HAProxyMessage: {}", ctx.channel().id(), msg);
            ctx.close();
        }
    }

}
