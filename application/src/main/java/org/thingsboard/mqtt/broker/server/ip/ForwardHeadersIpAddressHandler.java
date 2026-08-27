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

import com.google.common.net.InetAddresses;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.server.MqttSessionHandler;

import java.net.InetAddress;
import java.net.InetSocketAddress;

@Slf4j
public class ForwardHeadersIpAddressHandler extends ChannelInboundHandlerAdapter {

    static final String X_FORWARDED_FOR = "X-Forwarded-For";
    static final String X_REAL_IP = "X-Real-IP";

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest request) {
            InetAddress resolved = resolveFromHeaders(request);
            if (resolved != null) {
                // Port 0 — the client's real source port is not carried in X-Forwarded-For / X-Real-IP.
                InetSocketAddress address = new InetSocketAddress(resolved, 0);
                log.trace("[{}] Overriding address from forward headers: {}", ctx.channel().id(), address);
                ctx.channel().attr(MqttSessionHandler.ADDRESS).set(address);
            } else {
                log.trace("[{}] No usable forward headers; address unchanged", ctx.channel().id());
            }
            ctx.pipeline().remove(this);
        }
        ctx.fireChannelRead(msg);
    }

    private InetAddress resolveFromHeaders(FullHttpRequest request) {
        String xff = request.headers().get(X_FORWARDED_FOR);
        InetAddress fromXff = parseLeftmost(xff);
        if (fromXff != null) {
            return fromXff;
        }
        String xRealIp = request.headers().get(X_REAL_IP);
        return parseSingle(xRealIp);
    }

    private InetAddress parseLeftmost(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        int comma = headerValue.indexOf(',');
        String first = (comma >= 0 ? headerValue.substring(0, comma) : headerValue).trim();
        return parseSingle(first);
    }

    private InetAddress parseSingle(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        try {
            return InetAddresses.forString(trimmed);
        } catch (IllegalArgumentException e) {
            log.debug("Unparseable forward-header value: '{}'", value);
            return null;
        }
    }
}
