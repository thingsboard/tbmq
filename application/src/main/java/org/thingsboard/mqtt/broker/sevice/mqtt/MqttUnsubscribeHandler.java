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
package org.thingsboard.mqtt.broker.sevice.mqtt;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.sevice.subscription.SubscriptionService;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
@Slf4j
public class MqttUnsubscribeHandler {

    private final MqttMessageGenerator mqttMessageGenerator;
    private final SubscriptionService subscriptionService;

    public void process(ClientSessionCtx ctx, MqttUnsubscribeMessage msg) {
        UUID sessionId = ctx.getSessionId();
        List<String> topics = msg.payload().topics();
        log.trace("[{}] Processing unsubscribe [{}], topics - {}", sessionId, msg.variableHeader().messageId(), topics);

        ListenableFuture<Void> unsubscribeFuture = subscriptionService.unsubscribe(sessionId, topics);

        // TODO: test this manually
        unsubscribeFuture.addListener(() -> {
            ctx.getChannel().writeAndFlush(mqttMessageGenerator.createUnSubAckMessage(msg.variableHeader().messageId()));
        }, MoreExecutors.directExecutor());
    }

}
