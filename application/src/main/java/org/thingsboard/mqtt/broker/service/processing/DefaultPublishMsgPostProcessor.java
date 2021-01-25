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
package org.thingsboard.mqtt.broker.service.processing;

import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.exception.NotSupportedQoSLevelException;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultPublishMsgPostProcessor implements PublishMsgPostProcessor {
    private final SuccessfulPublishService successfulPublishService;

    @Override
    public void processSentMsg(ClientSessionCtx sessionCtx, MqttPublishMessage msg) {
        String clientId = sessionCtx.getSessionInfo().getClientInfo().getClientId();
        MqttQoS mqttQoS = msg.fixedHeader().qosLevel();
        switch (mqttQoS) {
            case AT_MOST_ONCE:
                successfulPublishService.confirmSuccessfulPublish(clientId);
                break;
            case AT_LEAST_ONCE:
                // TODO think about retry
                break;
            default:
                throw new NotSupportedQoSLevelException("QoS level " + mqttQoS + " is not supported.");
        }
    }

    @Override
    public void processReceivedMsg(UUID sessionId, MqttPubAckMessage msg) {
        // TODO make publish 'successful'
    }
}
