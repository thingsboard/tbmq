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
package org.thingsboard.mqtt.broker.service.stats.timer;

import io.micrometer.core.instrument.Timer;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;
import org.thingsboard.mqtt.broker.service.stats.StatsType;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TimerStats implements SubscriptionTimerStats, PublishMsgProcessingTimerStats, DeliveryTimerStats {
    private final List<Timer> timers;

    private final Timer subscriptionLookupTimer;
    private final Timer clientSessionsLookupTimer;
    private final Timer notPersistentMessagesProcessingTimer;
    private final Timer persistentMessagesProcessingTimer;
    private final Timer deliveryTimer;

    public TimerStats(StatsFactory statsFactory) {
        this.subscriptionLookupTimer = statsFactory.createTimer(StatsType.SUBSCRIPTION_LOOKUP.getPrintName());
        this.clientSessionsLookupTimer = statsFactory.createTimer(StatsType.CLIENT_SESSIONS_LOOKUP.getPrintName());
        this.notPersistentMessagesProcessingTimer = statsFactory.createTimer(StatsType.NOT_PERSISTENT_MESSAGES_PROCESSING.getPrintName());
        this.persistentMessagesProcessingTimer = statsFactory.createTimer(StatsType.PERSISTENT_MESSAGES_PROCESSING.getPrintName());
        this.deliveryTimer = statsFactory.createTimer(StatsType.DELIVERY.getPrintName());

        this.timers = Arrays.asList(
                subscriptionLookupTimer, clientSessionsLookupTimer, notPersistentMessagesProcessingTimer, persistentMessagesProcessingTimer,
                deliveryTimer
        );
    }

    public Collection<Timer> getTimers() {
        return timers;
    }

    @Override
    public void logSubscriptionsLookup(long amount, TimeUnit unit) {
        subscriptionLookupTimer.record(amount, unit);
    }

    @Override
    public void logClientSessionsLookup(long amount, TimeUnit unit) {
        clientSessionsLookupTimer.record(amount, unit);
    }

    @Override
    public void logNotPersistentMessagesProcessing(long amount, TimeUnit unit) {
        notPersistentMessagesProcessingTimer.record(amount, unit);
    }

    @Override
    public void logPersistentMessagesProcessing(long amount, TimeUnit unit) {
        persistentMessagesProcessingTimer.record(amount, unit);
    }

    @Override
    public void logDelivery(long amount, TimeUnit unit) {
        deliveryTimer.record(amount, unit);
    }
}
