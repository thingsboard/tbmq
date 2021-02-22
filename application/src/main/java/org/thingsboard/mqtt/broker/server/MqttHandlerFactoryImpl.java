/**
 * Copyright © 2016-2020 The Thingsboard Authors
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

import io.netty.handler.ssl.SslHandler;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.service.mqtt.client.DisconnectService;
import org.thingsboard.mqtt.broker.service.mqtt.handlers.MqttMessageHandlers;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;

@Service
@AllArgsConstructor
public class MqttHandlerFactoryImpl implements MqttHandlerFactory {

    private final MqttMessageHandlers messageHandlers;
    private final KeepAliveService keepAliveService;
    private final DisconnectService disconnectService;

    @Override
    public MqttSessionHandler create(SslHandler sslHandler) {
        return new MqttSessionHandler(messageHandlers, keepAliveService, disconnectService, sslHandler);
    }
}
