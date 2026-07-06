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
import org.thingsboard.mqtt.broker.actors.client.service.subscription.UnsubscribeCallback;
import org.thingsboard.mqtt.broker.adaptor.NettyMqttConverter;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.service.integration.IntegrationLifecycleEventPublisher;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationPersistenceProcessor;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.util.MqttReasonCodeResolver;

import java.util.Collections;
import java.util.List;
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

        clientSubscriptionService.unsubscribeAndPersistReportingRemoved(ctx.getClientId(), msg.getTopics(),
                new UnsubscribeCallback() {
                    @Override
                    public void onSuccess(List<TopicSubscription> removedSubscriptions) {
                        MqttMessage unSubAckMessage = mqttMessageGenerator.createUnSubAckMessage(
                                msg.getMessageId(), getCodes(ctx, msg.getTopics(), removedSubscriptions));
                        ctx.getChannel().writeAndFlush(unSubAckMessage);
                        // MQTT allows UNSUBSCRIBE for filters the client never subscribed to. Emit CLIENT_UNSUBSCRIBED only for
                        // the subscriptions actually removed (symmetric with CLIENT_SUBSCRIBED, which emits only the granted ones).
                        if (!removedSubscriptions.isEmpty()) {
                            integrationLifecycleEventPublisher.publishUnsubscribed(ctx, removedSubscriptions);
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.warn("[{}][{}] Failed to process client unsubscription", ctx.getClientId(), ctx.getSessionId(), t);
                        // The Server MUST still respond with an UNSUBACK (MQTT-3.10.4-5). The persist failed, so the removed
                        // set is unknown: every requested filter gets an error code (null => codeless UNSUBACK on MQTT 3.1.1).
                        MqttMessage unSubAckMessage = mqttMessageGenerator.createUnSubAckMessage(
                                msg.getMessageId(), getFailureCodes(ctx, msg.getTopics()));
                        ctx.getChannel().writeAndFlush(unSubAckMessage);
                    }
                });

        stopProcessingApplicationSharedSubscriptions(ctx, msg.getTopics());
    }

    // A requested filter that matched a removed subscription gets SUCCESS; one the client was not subscribed to gets
    // NO_SUBSCRIPTION_EXISTED (both null on MQTT 3.1.1 => no reason codes). Matching is by bare topic filter, mirroring
    // how the removal itself matches, so codes always agree with what was actually removed.
    private List<UnsubAck> getCodes(ClientSessionCtx ctx, List<String> requestedTopics, List<TopicSubscription> removedSubscriptions) {
        Set<String> removedTopicFilters = removedSubscriptions.stream()
                .map(TopicSubscription::getTopicFilter)
                .collect(Collectors.toSet());
        return requestedTopics.stream()
                .map(topic -> removedTopicFilters.contains(NettyMqttConverter.getTopicFilter(topic))
                        ? MqttReasonCodeResolver.unsubAckSuccess(ctx)
                        : MqttReasonCodeResolver.unsubAckNoSubscriptionExisted(ctx))
                .collect(Collectors.toList());
    }

    // On persist failure the removed set is unknown, so every requested filter gets the same error code
    // (UNSPECIFIED_ERROR on MQTT 5, null => codeless UNSUBACK on MQTT 3.1.1).
    private List<UnsubAck> getFailureCodes(ClientSessionCtx ctx, List<String> requestedTopics) {
        return Collections.nCopies(requestedTopics.size(), MqttReasonCodeResolver.unsubAckError(ctx));
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
