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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.constant.BrokerConstants;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.SessionListener;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionService;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class MqttSubscribeHandler {

    private final MqttMessageGenerator mqttMessageGenerator;
    private final SubscriptionService subscriptionService;

    public void process(ClientSessionCtx ctx, MqttSubscribeMessage msg, SessionListener sessionListener) throws MqttException {
        UUID sessionId = ctx.getSessionId();
        List<MqttTopicSubscription> subscriptions = msg.payload().topicSubscriptions();
        log.trace("[{}] Processing subscribe [{}], subscriptions - {}", sessionId, msg.variableHeader().messageId(), subscriptions);

        ListenableFuture<Void> subscribeFuture = subscriptionService.subscribe(sessionId, subscriptions, sessionListener);

        subscribeFuture.addListener(() -> {
            List<Integer> grantedQoSList = subscriptions.stream().map(sub -> getMinSupportedQos(sub.qualityOfService())).collect(Collectors.toList());
            ctx.getChannel().writeAndFlush(mqttMessageGenerator.createSubAckMessage(msg.variableHeader().messageId(), grantedQoSList));
            log.trace("[{}] Client subscribed to {}", sessionId, subscriptions);
        }, MoreExecutors.directExecutor());
    }

    private static int getMinSupportedQos(MqttQoS reqQoS) {
        return Math.min(reqQoS.value(), BrokerConstants.MAX_SUPPORTED_QOS_LVL.value());
    }

}
