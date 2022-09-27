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
package org.thingsboard.mqtt.broker.service.subscription.shared;

import com.google.common.collect.Iterables;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class SharedSubscriptionProcessorImpl implements SharedSubscriptionProcessor {

    private final ConcurrentMap<TopicSharedSubscription, SharedSubscriptionIterator> sharedSubscriptionIteratorsMap = new ConcurrentHashMap<>();

    @Override
    public Subscription processRoundRobin(SharedSubscription sharedSubscription) {
        TopicSharedSubscription key = sharedSubscription.getTopicSharedSubscription();
        Iterator<Subscription> iterator = getIterator(key, sharedSubscription);
        return getOneSubscription(iterator);
    }

    @Override
    public void unsubscribe(TopicSharedSubscription topicSharedSubscription) {
        sharedSubscriptionIteratorsMap.remove(topicSharedSubscription);
    }

    private Iterator<Subscription> getIterator(TopicSharedSubscription key, SharedSubscription sharedSubscription) {
        if (sharedSubscriptionIteratorsMap.containsKey(key)) {
            SharedSubscriptionIterator subscriptionIterator = sharedSubscriptionIteratorsMap.get(key);
            if (subscriptionIterator.getSharedSubscription().equals(sharedSubscription)) {
                return subscriptionIterator.getIterator();
            } else {
                return createIteratorAndPutToMap(key, sharedSubscription);
            }
        } else {
            return createIteratorAndPutToMap(key, sharedSubscription);
        }
    }

    private Iterator<Subscription> createIteratorAndPutToMap(TopicSharedSubscription key, SharedSubscription sharedSubscription) {
        Iterator<Subscription> iterator = createIterator(sharedSubscription.getSubscriptions());
        sharedSubscriptionIteratorsMap.put(key, new SharedSubscriptionIterator(sharedSubscription, iterator));
        return iterator;
    }

    Iterator<Subscription> createIterator(List<Subscription> subscriptions) {
        return Iterables.cycle(subscriptions).iterator();
    }

    Subscription getOneSubscription(Iterator<Subscription> iterator) {
        while (true) {
            Subscription next = iterator.next();
            if (next.getClientSession().isConnected()) {
                return next;
            }
        }
    }

    @Data
    @RequiredArgsConstructor
    static class SharedSubscriptionIterator {
        private final SharedSubscription sharedSubscription;
        private final Iterator<Subscription> iterator;
    }
}
