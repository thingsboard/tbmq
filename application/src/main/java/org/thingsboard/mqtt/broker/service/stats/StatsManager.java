/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.service.stats.timer.DeliveryTimerStats;
import org.thingsboard.mqtt.broker.service.stats.timer.PublishMsgProcessingTimerStats;
import org.thingsboard.mqtt.broker.service.stats.timer.RetainedMsgTimerStats;
import org.thingsboard.mqtt.broker.service.stats.timer.SubscriptionTimerStats;

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

    ClientSubscriptionConsumerStats getClientSubscriptionConsumerStats();

    RetainedMsgConsumerStats getRetainedMsgConsumerStats();

    void clearApplicationProcessorStats(String clientId);

    AtomicInteger createSubscriptionSizeCounter();

    AtomicInteger createRetainMsgSizeCounter();

    AtomicLong createSubscriptionTrieNodesCounter();

    AtomicLong createRetainMsgTrieNodesCounter();

    void registerLastWillStats(Map<?, ?> lastWillMsgsMap);

    void registerActiveSessionsStats(Map<?, ?> sessionsMap);

    void registerAllClientSessionsStats(Map<?, ?> clientSessionsMap);

    void registerClientSubscriptionsStats(Map<?, ?> clientSubscriptionsMap);

    void registerActiveApplicationProcessorsStats(Map<?, ?> processingFuturesMap);

    SubscriptionTimerStats getSubscriptionTimerStats();

    RetainedMsgTimerStats getRetainedMsgTimerStats();

    PublishMsgProcessingTimerStats getPublishMsgProcessingTimerStats();

    DeliveryTimerStats getDeliveryTimerStats();

    ClientActorStats getClientActorStats();
}
