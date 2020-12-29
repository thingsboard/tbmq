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
package org.thingsboard.mqtt.broker.service.mqtt;

import io.netty.handler.codec.mqtt.MqttConnectMessage;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.SessionInfoCreator;

import java.util.UUID;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_ACCEPTED;

@Service
@AllArgsConstructor
@Slf4j
public class MqttConnectHandler {

    private final MqttMessageGenerator mqttMessageGenerator;

    public void process(ClientSessionCtx ctx, MqttConnectMessage msg) {
        UUID sessionId = ctx.getSessionId();
        log.info("[{}] Processing connect msg for client: {}!", sessionId, msg.payload().clientIdentifier());
        String clientId = msg.payload().clientIdentifier();
        // TODO: login and get tenantId if there's such
        ctx.setConnected();
        SessionInfo sessionInfo = SessionInfoCreator.create(sessionId, clientId, !msg.variableHeader().isCleanSession());
        ctx.setSessionInfo(sessionInfo);
        ctx.setSessionInfoProto(SessionInfoCreator.createProto(sessionInfo));
        ctx.getChannel().writeAndFlush(mqttMessageGenerator.createMqttConnAckMsg(CONNECTION_ACCEPTED));
        log.info("[{}] Client connected!", sessionId);
    }
}
