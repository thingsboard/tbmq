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
package org.thingsboard.mqtt.broker.service.stats;

import org.thingsboard.mqtt.broker.common.stats.MessagesStats;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.data.ApplicationSharedSubscriptionJob;
import org.thingsboard.mqtt.broker.service.stats.timer.DeliveryTimerStats;
import org.thingsboard.mqtt.broker.service.stats.timer.PublishMsgProcessingTimerStats;
import org.thingsboard.mqtt.broker.service.stats.timer.RetainedMsgTimerStats;
import org.thingsboard.mqtt.broker.service.stats.timer.SubscriptionTimerStats;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public interface StatsManager {

    TbQueueCallback wrapTbQueueCallback(TbQueueCallback queueCallback, MessagesStats stats);

    MessagesStats createMsgDispatcherPublishStats();

    /**
     * Returns the dropped-messages stats. Defined here so it shares the {@code stats.enabled} master switch
     * with all other broker metrics: when stats are disabled the stub manager returns a no-op instance, so
     * the {@code droppedMsgs} counter is not exposed on {@code /actuator/prometheus}.
     */
    DroppedMsgStats getDroppedMsgStats();

    /**
     * Returns the dropped-lifecycle-events stats. Shares the {@code stats.enabled} master switch; when stats
     * are disabled the stub manager returns a no-op instance.
     */
    DroppedLifecycleEventStats getDroppedLifecycleEventStats();

    /**
     * Returns the client-disconnect stats. Shares the {@code stats.enabled} master switch; when stats
     * are disabled the stub manager returns a no-op instance.
     */
    ClientDisconnectStats getClientDisconnectStats();

    /**
     * Returns the total-throughput-quota degradation stats. When stats are disabled the stub manager
     * returns a no-op instance.
     */
    ThroughputQuotaStats getThroughputQuotaStats();

    /**
     * Returns the connection-outcome stats. Shares the {@code stats.enabled} master switch; when stats
     * are disabled the stub manager returns a no-op instance.
     */
    ConnectionStats getConnectionStats();

    ClientSessionEventConsumerStats createClientSessionEventConsumerStats(String consumerId);

    PublishMsgConsumerStats createPublishMsgConsumerStats(String consumerId);

    DeviceProcessorStats createDeviceProcessorStats(String consumerId);

    ApplicationProcessorStats createApplicationProcessorStats(String clientId);

    ApplicationProcessorStats createSharedApplicationProcessorStats(String clientId, TopicSharedSubscription subscription);

    ClientSubscriptionConsumerStats getClientSubscriptionConsumerStats();

    RetainedMsgConsumerStats getRetainedMsgConsumerStats();

    void clearApplicationProcessorStats(String clientId);

    void clearSharedApplicationProcessorStats(String clientId);

    void clearSharedApplicationProcessorStats(String clientId, TopicSharedSubscription subscription);

    AtomicInteger createNonWritableClientsGauge();

    AtomicInteger createSubscriptionSizeGauge();

    AtomicInteger createRetainMsgSizeGauge();

    AtomicLong createSubscriptionTrieNodesGauge();

    AtomicLong createRetainMsgTrieNodesGauge();

    void registerLastWillStats(Map<?, ?> lastWillMsgsMap);

    void registerActiveSessionsStats(Map<?, ?> sessionsMap);

    AtomicLong registerActiveSslSessionsStats();

    void registerAllClientSessionsStats(Map<?, ?> clientSessionsMap);

    void registerSubscriptionsStats(LongAdder subscriptionCount);

    void registerRetainedMsgStats(Map<?, ?> retainedMessagesMap);

    void registerActiveApplicationProcessorsStats(Map<?, ?> processingFuturesMap);

    void registerActiveSharedApplicationProcessorsStats(Map<String, List<ApplicationSharedSubscriptionJob>> processingFuturesMap);

    SubscriptionTimerStats getSubscriptionTimerStats();

    RetainedMsgTimerStats getRetainedMsgTimerStats();

    PublishMsgProcessingTimerStats getPublishMsgProcessingTimerStats();

    DeliveryTimerStats getDeliveryTimerStats();

    ActorStats getClientActorStats();

    ActorStats getPersistedDeviceActorStats();

    FlowControlStats getFlowControlStats();

    boolean isEnabled();
}
