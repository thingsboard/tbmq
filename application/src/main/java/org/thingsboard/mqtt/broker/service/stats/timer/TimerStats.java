/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.stats.timer;

import org.thingsboard.mqtt.broker.common.stats.ResettableTimer;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.service.stats.StatsType;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TimerStats implements SubscriptionTimerStats, PublishMsgProcessingTimerStats, DeliveryTimerStats, RetainedMsgTimerStats {
    private final List<ResettableTimer> timers;

    private final ResettableTimer subscriptionLookupTimer;
    private final ResettableTimer retainedMsgLookupTimer;
    private final ResettableTimer clientSessionsLookupTimer;
    private final ResettableTimer notPersistentMessagesProcessingTimer;
    private final ResettableTimer persistentMessagesProcessingTimer;
    private final ResettableTimer deliveryTimer;

    public TimerStats(StatsFactory statsFactory) {
        this.subscriptionLookupTimer = new ResettableTimer(statsFactory.createTimer(StatsType.SUBSCRIPTION_LOOKUP.getPrintName()));
        this.retainedMsgLookupTimer = new ResettableTimer(statsFactory.createTimer(StatsType.RETAINED_MSG_LOOKUP.getPrintName()));
        this.clientSessionsLookupTimer = new ResettableTimer(statsFactory.createTimer(StatsType.CLIENT_SESSIONS_LOOKUP.getPrintName()));
        this.notPersistentMessagesProcessingTimer = new ResettableTimer(statsFactory.createTimer(StatsType.NOT_PERSISTENT_MESSAGES_PROCESSING.getPrintName()));
        this.persistentMessagesProcessingTimer = new ResettableTimer(statsFactory.createTimer(StatsType.PERSISTENT_MESSAGES_PROCESSING.getPrintName()));
        this.deliveryTimer = new ResettableTimer(statsFactory.createTimer(StatsType.DELIVERY.getPrintName()));

        this.timers = Arrays.asList(
                subscriptionLookupTimer, retainedMsgLookupTimer, clientSessionsLookupTimer,
                notPersistentMessagesProcessingTimer, persistentMessagesProcessingTimer, deliveryTimer
        );
    }

    public Collection<ResettableTimer> getTimers() {
        return timers;
    }

    @Override
    public void logSubscriptionsLookup(long amount, TimeUnit unit) {
        subscriptionLookupTimer.logTime(amount, unit);
    }

    @Override
    public void logClientSessionsLookup(long amount, TimeUnit unit) {
        clientSessionsLookupTimer.logTime(amount, unit);
    }

    @Override
    public void logNotPersistentMessagesProcessing(long amount, TimeUnit unit) {
        notPersistentMessagesProcessingTimer.logTime(amount, unit);
    }

    @Override
    public void logPersistentMessagesProcessing(long amount, TimeUnit unit) {
        persistentMessagesProcessingTimer.logTime(amount, unit);
    }

    @Override
    public void logDelivery(long amount, TimeUnit unit) {
        deliveryTimer.logTime(amount, unit);
    }

    @Override
    public void logRetainedMsgLookup(long amount, TimeUnit unit) {
        retainedMsgLookupTimer.logTime(amount, unit);
    }
}
