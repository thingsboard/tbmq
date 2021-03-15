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
package org.thingsboard.mqtt.broker.service.mqtt.handlers;

import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
@Slf4j
public class MqttUnsubscribeHandler {

    private final MqttMessageGenerator mqttMessageGenerator;
    private final SubscriptionManager subscriptionManager;

    public void process(ClientSessionCtx ctx, MqttUnsubscribeMessage msg) {
        UUID sessionId = ctx.getSessionId();
        String clientId = ctx.getSessionInfo().getClientInfo().getClientId();
        List<String> topicFilters = msg.payload().topics();
        log.trace("[{}][{}] Processing unsubscribe [{}], topic filters - {}", clientId, sessionId, msg.variableHeader().messageId(), topicFilters);

        subscriptionManager.unsubscribe(clientId, topicFilters);

        ctx.getChannel().writeAndFlush(mqttMessageGenerator.createUnSubAckMessage(msg.variableHeader().messageId()));
    }

}
