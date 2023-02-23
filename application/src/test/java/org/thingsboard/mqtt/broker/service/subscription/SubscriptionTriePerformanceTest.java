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
package org.thingsboard.mqtt.broker.service.subscription;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.ConnectionInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class SubscriptionTriePerformanceTest {
    private static final String SERVICE_ID = "serviceId";

    private static final int FIRST_LEVEL_SEGMENTS = 50;
    private static final int SECOND_LEVEL_SEGMENTS = 100;
    private static final int SINGLE_LEVEL_WILDCARDS_PERCENTAGE = 10;
    private static final int MULTIPLE_LEVEL_WILDCARDS_PERCENTAGE = 5;
    private static final int MAX_LEVELS = 8;
    private static final int MAX_SUBSCRIPTIONS_PER_SESSION = 4;
    private static final int NUMBER_OF_TOPICS = 10000;
    private static final int NUMBER_OF_SUBSCRIBERS = 100_000;
    private static final int NUMBER_OF_MESSAGES = 100_000;
    private static final int NUMBER_OF_THREADS = 5;


    private ConcurrentMapSubscriptionTrie<SessionInfo> subscriptionTrie;
    private final List<SessionInfoSubscriptions> sessionInfoSubscriptionsList = new ArrayList<>();

    @AllArgsConstructor
    private static class SessionInfoSubscriptions {
        private final SessionInfo sessionInfo;
        private final Set<String> topicFilters;
    }

    @Before
    public void before() {
        StatsManager statsManagerMock = Mockito.mock(StatsManager.class);
        Mockito.when(statsManagerMock.createSubscriptionSizeCounter()).thenReturn(new AtomicInteger());
        Mockito.when(statsManagerMock.createSubscriptionTrieNodesCounter()).thenReturn(new AtomicLong());
        this.subscriptionTrie = new ConcurrentMapSubscriptionTrie<>(statsManagerMock);
    }

    @Test
    public void testSingleThread() throws Exception {
        List<Supplier<String>> levelSuppliers = initializeLevelSuppliers();

        List<String> topicFilters = initializeTopicFilters(levelSuppliers);
        List<String> topics = topicFilters.stream().map(s -> s.replaceAll("[+#]", "test")).collect(Collectors.toList());


        fillSubscriptionTrie(topicFilters);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        long startTime = System.currentTimeMillis();
        ListenableFuture<Void> task = Futures.submit(
                () -> {
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
                        String randomTopic = topics.get(r.nextInt(topics.size()));
                        subscriptionTrie.get(randomTopic);
                    }
                },
                executor);

        task.get(30, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        System.out.println("All took " + (endTime - startTime) + " ms");

    }

    @Test
    public void testMultipleThreads() throws Exception {
        List<Supplier<String>> levelSuppliers = initializeLevelSuppliers();

        List<String> topicFilters = initializeTopicFilters(levelSuppliers);
        List<String> topics = topicFilters.stream().map(s -> s.replaceAll("[+#]", "test")).collect(Collectors.toList());

        fillSubscriptionTrie(topicFilters);

        ExecutorService subscribeExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch processingPublishers = new CountDownLatch(NUMBER_OF_THREADS);
        subscribeExecutor.execute(() -> {
            simulateSubscribers(topicFilters, processingPublishers);
        });
        long startTime = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);
        for (int i = 0; i < NUMBER_OF_THREADS; i++) {
            executor.execute(() -> {
                try {
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    for (int j = 0; j < NUMBER_OF_MESSAGES / NUMBER_OF_THREADS; j++) {
                        String randomTopic = topics.get(r.nextInt(topics.size()));
                        subscriptionTrie.get(randomTopic);
                    }
                } finally {
                    processingPublishers.countDown();
                }
            });
        }
        processingPublishers.await(30, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        System.out.println("All took " + (endTime - startTime) + " ms");

    }

    private void simulateSubscribers(List<String> topicFilters, CountDownLatch processingPublishers) {
        while (processingPublishers.getCount() > 0) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            if (r.nextBoolean()) {
                SessionInfoSubscriptions sessionInfoSubscriptions = this.sessionInfoSubscriptionsList.get(r.nextInt(this.sessionInfoSubscriptionsList.size()));
                for (String topicFilter : sessionInfoSubscriptions.topicFilters) {
                    subscriptionTrie.delete(topicFilter,
                            sessionInfo -> sessionInfoSubscriptions.sessionInfo.getSessionId().equals(sessionInfo.getSessionId()));
                }
            } else {
                SessionInfo sessionInfo = new SessionInfo(SERVICE_ID, UUID.randomUUID(), r.nextBoolean(), 100,
                        new ClientInfo(UUID.randomUUID().toString(), ClientType.DEVICE), new ConnectionInfo(0, 0, 0));
                String randomTopicFilter = topicFilters.get(r.nextInt(topicFilters.size()));
                subscriptionTrie.put(randomTopicFilter, sessionInfo);
            }

            try {
                Thread.sleep(2);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void fillSubscriptionTrie(List<String> topicFilters) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < NUMBER_OF_SUBSCRIBERS; i++) {
            SessionInfo sessionInfo = new SessionInfo(SERVICE_ID, UUID.randomUUID(), r.nextBoolean(), 100,
                    new ClientInfo(UUID.randomUUID().toString(), ClientType.DEVICE), new ConnectionInfo(0, 0, 0));
            int subscriptionsCount = r.nextInt(MAX_SUBSCRIPTIONS_PER_SESSION) + 1;
            SessionInfoSubscriptions sessionInfoSubscriptions = new SessionInfoSubscriptions(sessionInfo, new HashSet<>());
            for (int j = 0; j < subscriptionsCount; j++) {
                String topicFilter = topicFilters.get(r.nextInt(topicFilters.size()));
                subscriptionTrie.put(topicFilter, sessionInfo);
                sessionInfoSubscriptions.topicFilters.add(topicFilter);
            }
            sessionInfoSubscriptionsList.add(sessionInfoSubscriptions);
        }
    }

    private List<String> initializeTopicFilters(List<Supplier<String>> levelSuppliers) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<String> topicFilters = new ArrayList<>(NUMBER_OF_TOPICS);
        int singleLevelWildcardsAmount = SINGLE_LEVEL_WILDCARDS_PERCENTAGE * (NUMBER_OF_TOPICS / 100);
        int multipleLevelWildcardsAmount = MULTIPLE_LEVEL_WILDCARDS_PERCENTAGE * (NUMBER_OF_TOPICS / 100);
        for (int i = 0; i < NUMBER_OF_TOPICS; i++) {
            StringBuilder topicFilterBuilder = new StringBuilder();
            int topicLevel = r.nextInt(MAX_LEVELS) + 1;
            boolean useSingleLevelWildcard = r.nextInt(NUMBER_OF_TOPICS) <= singleLevelWildcardsAmount;
            boolean useMultipleLevelWildcard = r.nextInt(NUMBER_OF_TOPICS) <= multipleLevelWildcardsAmount;
            for (int j = 0; j < topicLevel; j++) {
                if (useMultipleLevelWildcard && j == topicLevel - 1) {
                    topicFilterBuilder.append("#");
                } else if (useSingleLevelWildcard && r.nextInt(topicLevel) == 0) {
                    topicFilterBuilder.append("+");
                } else {
                    topicFilterBuilder.append(levelSuppliers.get(j).get());
                }
                if (j != topicLevel - 1) {
                    topicFilterBuilder.append("/");
                }
            }
            topicFilters.add(topicFilterBuilder.toString());
        }
        return topicFilters;
    }

    private List<Supplier<String>> initializeLevelSuppliers() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Supplier<String>> levelSuppliers = new ArrayList<>(MAX_LEVELS);
        List<String> firstLevelSegments = IntStream.range(0, FIRST_LEVEL_SEGMENTS).boxed()
                .map(ignored -> UUID.randomUUID().toString().substring(0, 10))
                .collect(Collectors.toList());
        List<String> secondLevelSegments = IntStream.range(0, SECOND_LEVEL_SEGMENTS).boxed()
                .map(ignored -> UUID.randomUUID().toString().substring(0, 10))
                .collect(Collectors.toList());
        levelSuppliers.add(() -> firstLevelSegments.get(r.nextInt(firstLevelSegments.size())));
        levelSuppliers.add(() -> secondLevelSegments.get(r.nextInt(secondLevelSegments.size())));
        for (int i = 0; i < MAX_LEVELS; i++) {
            levelSuppliers.add(() -> UUID.randomUUID().toString().substring(0, 10));
        }
        return levelSuppliers;
    }


}
