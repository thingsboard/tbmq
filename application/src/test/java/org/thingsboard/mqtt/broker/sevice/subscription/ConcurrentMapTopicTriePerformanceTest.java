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
package org.thingsboard.mqtt.broker.sevice.subscription;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.Tenant;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class ConcurrentMapTopicTriePerformanceTest {
    private static final Random r = new Random();


    private static final int FIRST_LEVEL_SEGMENTS = 50;
    private static final int SECOND_LEVEL_SEGMENTS = 100;
    private static final int SINGLE_LEVEL_WILDCARDS_PERCENTAGE = 10;
    private static final int MULTIPLE_LEVEL_WILDCARDS_PERCENTAGE = 5;
    private static final int MAX_LEVELS = 8;
    private static final int NUMBER_OF_TOPICS = 10000;
    private static final int NUMBER_OF_SUBSCRIBERS = 100_000;
    private static final int NUMBER_OF_MESSAGES = 100_000;

    private ConcurrentMapTopicTrie<SessionInfo> concurrentMapTopicTrie;

    @Before
    public void before(){
        this.concurrentMapTopicTrie = new ConcurrentMapTopicTrie<>();
    }

    @Test
    public void test1000Topics1000000Publish() throws Exception{
        List<Supplier<String>> levelSuppliers = initializeLevelSuppliers();

        List<String> topicFilters = initializeTopicFilters(levelSuppliers);
        List<String> topics = topicFilters.stream().map(s -> s.replaceAll("[+#]", "test")).collect(Collectors.toList());


        fillTopicTrie(topicFilters);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        long startTime = System.currentTimeMillis();
        ListenableFuture<Void> task = Futures.submit(
                () -> {
                    for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
                        String randomTopic = topics.get(r.nextInt(topics.size()));
                        concurrentMapTopicTrie.get(randomTopic);
                    }
                },
                executor);

        task.get(10, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        System.out.println("All took " + (endTime - startTime) + " ms");

    }

    private void fillTopicTrie(List<String> topicFilters) {
        for (int i = 0; i < NUMBER_OF_SUBSCRIBERS; i++) {
            SessionInfo sessionInfo = new SessionInfo(UUID.randomUUID(), r.nextBoolean(),
                    new ClientInfo(UUID.randomUUID().toString(), new Tenant(UUID.randomUUID())));
            String topicFilter = topicFilters.get(r.nextInt(topicFilters.size()));
            concurrentMapTopicTrie.put(topicFilter, sessionInfo);
        }
    }

    private List<String> initializeTopicFilters(List<Supplier<String>> levelSuppliers) {
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
