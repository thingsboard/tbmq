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
package org.thingsboard.mqtt.broker.service.stats.timer;

import java.util.concurrent.TimeUnit;

public class StubTimerStats implements SubscriptionTimerStats, PublishMsgProcessingTimerStats, DeliveryTimerStats, RetainedMsgTimerStats {

    @Override
    public void logSubscriptionsLookup(long startTime, TimeUnit unit) {
    }

    @Override
    public void logClientSessionsLookup(long startTime, TimeUnit unit) {
    }

    @Override
    public void logNotPersistentMessagesProcessing(long startTime, TimeUnit unit) {
    }

    @Override
    public void logPersistentMessagesProcessing(long startTime, TimeUnit unit) {
    }

    @Override
    public void logDelivery(long startTime, TimeUnit unit) {

    }

    @Override
    public void logRetainedMsgLookup(long startTime, TimeUnit unit) {

    }
}
