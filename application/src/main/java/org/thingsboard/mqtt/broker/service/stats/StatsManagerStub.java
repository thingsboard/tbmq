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
package org.thingsboard.mqtt.broker.service.stats;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.ActorStatsManager;
import org.thingsboard.mqtt.broker.common.stats.MessagesStats;
import org.thingsboard.mqtt.broker.common.stats.StubMessagesStats;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.stats.ProducerStatsManager;
import org.thingsboard.mqtt.broker.queue.stats.Timer;
import org.thingsboard.mqtt.broker.service.stats.timer.DeliveryTimerStats;
import org.thingsboard.mqtt.broker.service.stats.timer.PublishMsgProcessingTimerStats;
import org.thingsboard.mqtt.broker.service.stats.timer.StubTimerStats;
import org.thingsboard.mqtt.broker.service.stats.timer.SubscriptionTimerStats;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "stats", value = "enabled", havingValue = "false", matchIfMissing = true)
public class StatsManagerStub implements StatsManager, ActorStatsManager, ProducerStatsManager {
    private static final StubTimerStats timerStats = new StubTimerStats();

    @Override
    public TbQueueCallback wrapTbQueueCallback(TbQueueCallback queueCallback, MessagesStats stats) {
        return queueCallback;
    }

    @Override
    public MessagesStats createMsgDispatcherPublishStats() {
        return StubMessagesStats.STUB_MESSAGE_STATS;
    }

    @Override
    public PublishMsgConsumerStats createPublishMsgConsumerStats(String consumerId) {
        return StubPublishMsgConsumerStats.STUB_PUBLISH_MSG_CONSUMER_STATS;
    }

    @Override
    public DeviceProcessorStats createDeviceProcessorStats(String consumerId) {
        return StubDeviceProcessorStats.STUB_DEVICE_PROCESSOR_STATS;
    }

    @Override
    public ApplicationProcessorStats createApplicationProcessorStats(String clientId) {
        return StubApplicationProcessorStats.STUB_APPLICATION_PROCESSOR_STATS;
    }

    @Override
    public ClientSubscriptionConsumerStats getClientSubscriptionConsumerStats() {
        return StubClientSubscriptionConsumerStats.STUB_CLIENT_SUBSCRIPTION_CONSUMER_STATS;
    }

    @Override
    public void clearApplicationProcessorStats(String clientId) {
    }

    @Override
    public AtomicInteger createSubscriptionSizeCounter() {
        return new AtomicInteger(0);
    }

    @Override
    public void registerLastWillStats(Map<?, ?> lastWillMsgsMap) {
    }

    @Override
    public void registerActiveSessionsStats(Map<?, ?> sessionsMap) {
    }

    @Override
    public void registerAllClientSessionsStats(Map<?, ?> clientSessionsMap) {
    }

    @Override
    public void registerClientSubscriptionsStats(Map<?, ?> clientSubscriptionsMap) {
    }

    @Override
    public void registerActiveApplicationProcessorsStats(Map<?, ?> processingFuturesMap) {
    }

    @Override
    public SubscriptionTimerStats getSubscriptionTimerStats() {
        return timerStats;
    }

    @Override
    public PublishMsgProcessingTimerStats getPublishMsgProcessingTimerStats() {
        return timerStats;
    }

    @Override
    public DeliveryTimerStats getDeliveryTimerStats() {
        return timerStats;
    }

    @Override
    public void registerActorsStats(Map<?, ?> actorsMap) {
    }

    @Override
    public AtomicLong createSubscriptionTrieNodesCounter() {
        return new AtomicLong(0);
    }

    @Override
    public Timer createTimer(String clientId) {
        return (amount, unit) -> {};
    }

    @Override
    public void registerProducerQueue(String queueName, Queue<?> queue) {

    }
}
