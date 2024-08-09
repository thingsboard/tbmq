/**
 * Copyright © 2016-2024 The Thingsboard Authors
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

public interface StatsManager {
    TbQueueCallback wrapTbQueueCallback(TbQueueCallback queueCallback, MessagesStats stats);

    MessagesStats createMsgDispatcherPublishStats();

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

    AtomicInteger createSubscriptionSizeCounter();

    AtomicInteger createRetainMsgSizeCounter();

    AtomicLong createSubscriptionTrieNodesCounter();

    AtomicLong createRetainMsgTrieNodesCounter();

    void registerLastWillStats(Map<?, ?> lastWillMsgsMap);

    void registerActiveSessionsStats(Map<?, ?> sessionsMap);

    AtomicLong registerActiveSslSessionsStats();

    void registerAllClientSessionsStats(Map<?, ?> clientSessionsMap);

    void registerClientSubscriptionsStats(Map<?, ?> clientSubscriptionsMap);

    void registerRetainedMsgStats(Map<?, ?> retainedMessagesMap);

    void registerActiveApplicationProcessorsStats(Map<?, ?> processingFuturesMap);

    void registerActiveSharedApplicationProcessorsStats(Map<String, List<ApplicationSharedSubscriptionJob>> processingFuturesMap);

    SubscriptionTimerStats getSubscriptionTimerStats();

    RetainedMsgTimerStats getRetainedMsgTimerStats();

    PublishMsgProcessingTimerStats getPublishMsgProcessingTimerStats();

    DeliveryTimerStats getDeliveryTimerStats();

    ClientActorStats getClientActorStats();

    boolean isEnabled();
}
