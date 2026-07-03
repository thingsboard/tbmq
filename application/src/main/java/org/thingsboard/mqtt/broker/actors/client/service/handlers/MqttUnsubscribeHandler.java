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
package org.thingsboard.mqtt.broker.actors.client.service.handlers;

import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttReasonCodes.UnsubAck;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttUnsubscribeMsg;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.adaptor.NettyMqttConverter;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.service.integration.IntegrationLifecycleEventPublisher;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationPersistenceProcessor;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.util.MqttReasonCodeResolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MqttUnsubscribeHandler {

    private final MqttMessageGenerator mqttMessageGenerator;
    private final ClientSubscriptionService clientSubscriptionService;
    private final ApplicationPersistenceProcessor applicationPersistenceProcessor;
    private final IntegrationLifecycleEventPublisher integrationLifecycleEventPublisher;

    public void process(ClientSessionCtx ctx, MqttUnsubscribeMsg msg) {
        log.trace("[{}][{}] Processing unsubscribe, messageId - {}, topic filters - {}", ctx.getClientId(), ctx.getSessionId(), msg.getMessageId(), msg.getTopics());

        MqttMessage unSubAckMessage = mqttMessageGenerator.createUnSubAckMessage(msg.getMessageId(), getCodes(ctx, msg));
        // MQTT allows UNSUBSCRIBE for filters the client never subscribed to. Emit CLIENT_UNSUBSCRIBED only for
        // the subscriptions actually removed (symmetric with CLIENT_SUBSCRIBED, which emits only the granted ones).
        List<TopicSubscription> removedSubscriptions = getRemovedSubscriptions(ctx.getClientId(), msg.getTopics());
        clientSubscriptionService.unsubscribeAndPersist(ctx.getClientId(), msg.getTopics(),
                CallbackUtil.createCallback(
                        () -> {
                            ctx.getChannel().writeAndFlush(unSubAckMessage);
                            if (!removedSubscriptions.isEmpty()) {
                                integrationLifecycleEventPublisher.publishUnsubscribed(ctx, removedSubscriptions);
                            }
                        },
                        t -> log.warn("[{}][{}] Failed to process client unsubscription", ctx.getClientId(), ctx.getSessionId(), t)
                ));

        stopProcessingApplicationSharedSubscriptions(ctx, msg.getTopics());
    }

    private List<TopicSubscription> getRemovedSubscriptions(String clientId, List<String> requestedTopics) {
        Set<TopicSubscription> currentSubscriptions = clientSubscriptionService.getClientSubscriptions(clientId);
        if (CollectionUtils.isEmpty(currentSubscriptions)) {
            return List.of();
        }
        // Index current subscriptions by (shareName, bare topic filter) so a requested $share/<group>/<filter>
        // resolves to that exact shared subscription (carrying its shareName), and a bare filter to the regular one.
        Map<SubKey, TopicSubscription> byKey = new HashMap<>();
        for (TopicSubscription sub : currentSubscriptions) {
            byKey.putIfAbsent(new SubKey(sub.getShareName(), sub.getTopicFilter()), sub);
        }
        List<TopicSubscription> removed = new ArrayList<>();
        for (String requested : requestedTopics) {
            String shareName = NettyMqttConverter.isSharedTopic(requested) ? NettyMqttConverter.getShareName(requested) : null;
            TopicSubscription sub = byKey.get(new SubKey(shareName, toTopicFilter(requested)));
            if (sub != null) {
                removed.add(sub);
            }
        }
        return removed;
    }

    // Composite lookup key; shareName is null for a regular subscription, the group name for a shared one.
    private record SubKey(String shareName, String topicFilter) {
    }

    private static String toTopicFilter(String topic) {
        return NettyMqttConverter.isSharedTopic(topic) ? NettyMqttConverter.getTopicFilter(topic) : topic;
    }

    private List<UnsubAck> getCodes(ClientSessionCtx ctx, MqttUnsubscribeMsg msg) {
        return msg
                .getTopics()
                .stream()
                .map(s -> MqttReasonCodeResolver.unsubAckSuccess(ctx))
                .collect(Collectors.toList());
    }

    private void stopProcessingApplicationSharedSubscriptions(ClientSessionCtx ctx, List<String> topics) {
        if (ctx.getSessionInfo().isPersistentAppClient()) {
            Set<TopicSharedSubscription> subscriptions = collectUniqueSharedSubscriptions(topics);
            if (CollectionUtils.isEmpty(subscriptions)) {
                return;
            }
            applicationPersistenceProcessor.stopProcessingSharedSubscriptions(ctx, subscriptions);
        }
    }

    Set<TopicSharedSubscription> collectUniqueSharedSubscriptions(List<String> topics) {
        return topics
                .stream()
                .filter(NettyMqttConverter::isSharedTopic)
                .map(topic -> new TopicSharedSubscription(
                        NettyMqttConverter.getTopicFilter(topic),
                        NettyMqttConverter.getShareName(topic)))
                .collect(Collectors.toSet());
    }

}
