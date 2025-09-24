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
package org.thingsboard.mqtt.broker.server.ws;

import io.netty.channel.socket.SocketChannel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.server.AbstractMqttWsChannelInitializer;
import org.thingsboard.mqtt.broker.server.MqttHandlerFactory;

@Slf4j
@Component
@Qualifier("WsChannelInitializer")
@Getter
public class MqttWsChannelInitializer extends AbstractMqttWsChannelInitializer {

    private final MqttWsServerContext context;

    public MqttWsChannelInitializer(MqttHandlerFactory handlerFactory, MqttWsServerContext context) {
        super(handlerFactory);
        this.context = context;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        super.initChannel(ch);
    }

    @Override
    public int getMaxPayloadSize() {
        return context.getMaxPayloadSize();
    }

    @Override
    public String getChannelInitializerName() {
        return BrokerConstants.WS;
    }

    @Override
    public Boolean isListenerProxyProtocolEnabled() {
        return context.getListenerProxyProtocolEnabled();
    }

    @Override
    protected String getSubprotocols() {
        return context.getSubprotocols();
    }
}
