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

import io.netty.handler.ssl.SslHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;

@Service
@RequiredArgsConstructor
public class MqttHandlerFactoryImpl implements MqttHandlerFactory {

    private final ClientMqttActorManager actorManager;
    private final ClientLogger clientLogger;
    private final RateLimitService rateLimitService;

    @Value("${mqtt.max-in-flight-msgs:1000}")
    private int maxInFlightMsgs;

    @Override
    public MqttSessionHandler create(SslHandler sslHandler, String initializerName) {
        return new MqttSessionHandler(actorManager, clientLogger, rateLimitService, sslHandler, initializerName, maxInFlightMsgs);
    }
}
