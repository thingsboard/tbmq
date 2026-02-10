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
package org.thingsboard.mqtt.broker.service.subscription.shared;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class SharedSubscriptionProcessorImpl implements SharedSubscriptionProcessor {

    private final ConcurrentMap<TopicSharedSubscription, SharedSubscriptionState> sharedSubscriptionStatesMap = new ConcurrentHashMap<>();

    @Override
    public Subscription processRoundRobin(SharedSubscription sharedSubscription) {
        TopicSharedSubscription key = sharedSubscription.getTopicSharedSubscription();
        List<Subscription> subscriptions = sharedSubscription.getSubscriptions();
        AtomicInteger counter = getCounter(key, sharedSubscription);
        return getOneSubscription(subscriptions, counter);
    }

    @Override
    public void unsubscribe(TopicSharedSubscription topicSharedSubscription) {
        sharedSubscriptionStatesMap.remove(topicSharedSubscription);
    }

    private AtomicInteger getCounter(TopicSharedSubscription key, SharedSubscription sharedSubscription) {
        return sharedSubscriptionStatesMap.compute(key, (k, existing) -> {
            if (existing == null || !existing.getSharedSubscription().equals(sharedSubscription)) {
                return new SharedSubscriptionState(sharedSubscription, new AtomicInteger(0));
            }
            return existing;
        }).getCounter();
    }

    Subscription getOneSubscription(List<Subscription> subscriptions, AtomicInteger counter) {
        int size = subscriptions.size();
        for (int i = 0; i < size; i++) {
            int index = Math.floorMod(counter.getAndIncrement(), size);
            Subscription subscription = subscriptions.get(index);
            if (subscription.getClientSessionInfo().isConnected()) {
                return subscription;
            }
        }
        return null;
    }

    @Data
    @RequiredArgsConstructor
    static class SharedSubscriptionState {
        private final SharedSubscription sharedSubscription;
        private final AtomicInteger counter;
    }
}
