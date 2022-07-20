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
package org.thingsboard.mqtt.broker.service.integration;

import net.jodah.concurrentunit.Waiter;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@Service
public class IntegrationTestInitService {
    public static final int SUBSCRIBERS_COUNT = 10;
    public static final int PUBLISHERS_COUNT = 5;
    public static final int PUBLISH_MSGS_COUNT = 100;

    public void initPubSubTest(BiConsumer<Waiter, Integer> subscriberInitializer,
                               BiConsumer<Waiter, Integer> publisherInitializer) throws Throwable {
        Waiter subscribersWaiter = new Waiter();
        CountDownLatch connectingSubscribers = new CountDownLatch(SUBSCRIBERS_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(PUBLISHERS_COUNT);
        for (int i = 0; i < SUBSCRIBERS_COUNT; i++) {
            int finalI = i;
            executor.execute(() -> {
                subscriberInitializer.accept(subscribersWaiter, finalI);
                connectingSubscribers.countDown();
            });
        }

        connectingSubscribers.await();

        CountDownLatch processingPublishers = new CountDownLatch(PUBLISHERS_COUNT);
        for (int i = 0; i < PUBLISHERS_COUNT; i++) {
            int finalI = i;
            executor.execute(() -> {
                publisherInitializer.accept(subscribersWaiter, finalI);
                processingPublishers.countDown();
            });
        }

        processingPublishers.await();

        subscribersWaiter.await(1, TimeUnit.SECONDS, SUBSCRIBERS_COUNT);
    }
}
