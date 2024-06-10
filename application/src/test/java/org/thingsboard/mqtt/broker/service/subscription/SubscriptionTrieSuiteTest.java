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
package org.thingsboard.mqtt.broker.service.subscription;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.exception.SubscriptionTrieClearException;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class SubscriptionTrieSuiteTest {

    private ConcurrentMapSubscriptionTrie<String> subscriptionTrie;
    private AtomicInteger subscriptionCounter;
    private AtomicLong nodesCounter;

    @Before
    public void before() {
        this.subscriptionCounter = new AtomicInteger(0);
        this.nodesCounter = new AtomicLong(0);
        StatsManager statsManagerMock = Mockito.mock(StatsManager.class);
        Mockito.when(statsManagerMock.createSubscriptionSizeCounter()).thenReturn(subscriptionCounter);
        Mockito.when(statsManagerMock.createSubscriptionTrieNodesCounter()).thenReturn(nodesCounter);
        this.subscriptionTrie = new ConcurrentMapSubscriptionTrie<>(statsManagerMock);
    }

    @Test
    public void testSaveSameSessionDifferentTopics() {
        subscriptionTrie.put("home/+/temperature", "sensor1");
        Assert.assertEquals(1, subscriptionTrie.get("home/livingroom/temperature").size());
        subscriptionTrie.put("home/+/temperature", "sensor1");
        Assert.assertEquals(1, subscriptionTrie.get("home/livingroom/temperature").size());
        subscriptionTrie.put("home/livingroom/humidity", "sensor1");
        Assert.assertEquals(1, subscriptionTrie.get("home/livingroom/humidity").size());
    }

    @Test
    public void testSaveDifferentSessionsSameTopic() {
        subscriptionTrie.put("home/+/temperature", "sensor1");
        Assert.assertEquals(1, subscriptionTrie.get("home/livingroom/temperature").size());
        subscriptionTrie.put("home/+/temperature", "sensor2");
        Assert.assertEquals(2, subscriptionTrie.get("home/livingroom/temperature").size());
    }

    @Test
    public void testSaveMultipleSessionsDifferentTopics() {
        subscriptionTrie.put("home/livingroom/temperature", "sensor1");
        subscriptionTrie.put("home/livingroom/humidity", "sensor2");
        Assert.assertEquals(1, subscriptionTrie.get("home/livingroom/temperature").size());
        Assert.assertEquals(1, subscriptionTrie.get("home/livingroom/humidity").size());
        subscriptionTrie.put("home/livingroom/temperature", "sensor1");
        subscriptionTrie.put("home/livingroom/humidity", "sensor2");
        Assert.assertEquals(1, subscriptionTrie.get("home/livingroom/temperature").size());
        Assert.assertEquals(1, subscriptionTrie.get("home/livingroom/humidity").size());
    }

    @Test
    public void testSaveSessionWithWildcards() {
        subscriptionTrie.put("home/+/temperature", "sensor1");
        subscriptionTrie.put("home/livingroom/temperature", "sensor2");
        Assert.assertEquals(2, subscriptionTrie.get("home/livingroom/temperature").size());
        Assert.assertEquals(1, subscriptionTrie.get("home/kitchen/temperature").size());
    }

    @Test
    public void testSaveSameSession() {
        subscriptionTrie.put("1/2", "test");
        Assert.assertEquals(1, subscriptionTrie.get("1/2").size());
        subscriptionTrie.put("1/2", "test");
        Assert.assertEquals(1, subscriptionTrie.get("1/2").size());
    }

    @Test
    public void testDelete() {
        subscriptionTrie.put("1/2", "test");
        subscriptionTrie.delete("1/2", s -> s.equals("test"));
        List<ValueWithTopicFilter<String>> result = subscriptionTrie.get("1/2");
        Assert.assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void testDeleteNonExistingSubscription() {
        subscriptionTrie.put("1/2", "test");
        subscriptionTrie.delete("1/3", s -> s.equals("test"));
        List<ValueWithTopicFilter<String>> result = subscriptionTrie.get("1/2");
        Assert.assertEquals(1, result.size());
    }

    @Test
    public void testDeleteMultipleSubscriptions() {
        subscriptionTrie.put("1/2", "test1");
        subscriptionTrie.put("1/2", "test2");
        subscriptionTrie.delete("1/2", s -> s.equals("test1"));
        List<ValueWithTopicFilter<String>> result = subscriptionTrie.get("1/2");
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("test2", result.get(0).getValue());
    }

    @Test
    public void testGet() {
        subscriptionTrie.put("1/22/3", "test1");
        subscriptionTrie.put("1/+/3", "test2");
        subscriptionTrie.put("1/#", "test3");
        subscriptionTrie.put("1/22/#", "test4");
        subscriptionTrie.put("1/+/4", "test5");
        subscriptionTrie.put("1/22/4", "test6");
        subscriptionTrie.put("#", "test7");
        subscriptionTrie.put("+/22/3", "test8");
        subscriptionTrie.put("+/22/+", "test9");
        subscriptionTrie.put("1/+/#", "test10");
        List<ValueWithTopicFilter<String>> result = subscriptionTrie.get("1/22/3");
        Assert.assertEquals(Set.of(
                        new ValueWithTopicFilter<>("test1", "1/22/3"),
                        new ValueWithTopicFilter<>("test2", "1/+/3"),
                        new ValueWithTopicFilter<>("test3", "1/#"),
                        new ValueWithTopicFilter<>("test4", "1/22/#"),
                        new ValueWithTopicFilter<>("test7", "#"),
                        new ValueWithTopicFilter<>("test8", "+/22/3"),
                        new ValueWithTopicFilter<>("test9", "+/22/+"),
                        new ValueWithTopicFilter<>("test10", "1/+/#")
                ),
                new HashSet<>(result));
    }

    @Test
    public void testTopicsWith$() {
        subscriptionTrie.put("#", "test1");
        subscriptionTrie.put("+/monitor/Clients", "test2");
        subscriptionTrie.put("$SYS/#", "test3");
        subscriptionTrie.put("$SYS/monitor/+", "test4");
        List<ValueWithTopicFilter<String>> result = subscriptionTrie.get("$SYS/monitor/Clients");
        Assert.assertEquals(Set.of(new ValueWithTopicFilter<>("test3", "$SYS/#"),
                        new ValueWithTopicFilter<>("test4", "$SYS/monitor/+")
                ),
                new HashSet<>(result));
    }

    @Test
    public void testSubscriptionCount() {
        for (int i = 0; i < 10; i++) {
            subscriptionTrie.put(Integer.toString(i), "val");
        }
        Assert.assertEquals(10, subscriptionCounter.get());
        for (int i = 0; i < 9; i++) {
            subscriptionTrie.delete(Integer.toString(i), s -> true);
        }
        Assert.assertEquals(1, subscriptionCounter.get());
    }

    @Test
    public void testNodeCount_Basic() {
        for (int i = 0; i < 10; i++) {
            subscriptionTrie.put(Integer.toString(i), "val");
        }
        Assert.assertEquals(10, nodesCounter.get());
    }

    @Test
    public void testNodeCount_TwoValues() {
        for (int i = 0; i < 10; i++) {
            subscriptionTrie.put(Integer.toString(i), "val1");
            subscriptionTrie.put(Integer.toString(i), "val2");
        }
        Assert.assertEquals(10, nodesCounter.get());
    }

    @Test
    public void testNodeCount_RemoveValues() {
        for (int i = 0; i < 10; i++) {
            subscriptionTrie.put(Integer.toString(i), "val1");
            subscriptionTrie.put(Integer.toString(i), "val2");
            subscriptionTrie.delete(Integer.toString(i), "val1"::equals);
            subscriptionTrie.delete(Integer.toString(i), "val2"::equals);
        }
        Assert.assertEquals(10, nodesCounter.get());
    }

    @Test
    public void testNodeCount_MultipleLevels() {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 3; j++) {
                subscriptionTrie.put(i + "/" + j, "val");
            }
        }
        // 10 first level + 30 second level
        Assert.assertEquals(10 + 30, nodesCounter.get());
    }

    @Test
    public void testClearTrie_Basic() throws SubscriptionTrieClearException {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 3; j++) {
                subscriptionTrie.put(i + "/" + j, "val");
            }
        }

        subscriptionTrie.delete("0/0", s -> true);
        subscriptionTrie.delete("0/1", s -> true);
        subscriptionTrie.delete("0/2", s -> true);
        subscriptionTrie.delete("1/0", s -> true);

        subscriptionTrie.setWaitForClearLockMs(100);
        subscriptionTrie.clearEmptyNodes();
        // should clear 0/0, 0/1, 0/2, 0 and 1/0 nodes
        Assert.assertEquals(40 - 5, nodesCounter.get());
    }

    @Test
    public void testClearTrie_ClearAll() throws SubscriptionTrieClearException {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 3; j++) {
                subscriptionTrie.put(i + "/" + j, "val");
                subscriptionTrie.delete(i + "/" + j, s -> true);
            }
        }
        Assert.assertEquals(40, nodesCounter.get());

        subscriptionTrie.setWaitForClearLockMs(100);
        subscriptionTrie.clearEmptyNodes();

        Assert.assertEquals(0, nodesCounter.get());
    }

    @Test
    public void testSingleLevelWildcardSubscription() {
        subscriptionTrie.put("home/+/temperature", "sensor1");
        subscriptionTrie.put("home/livingroom/temperature", "sensor2");
        subscriptionTrie.put("home/bedroom/temperature", "sensor3");
        List<ValueWithTopicFilter<String>> result = subscriptionTrie.get("home/livingroom/temperature");
        Assert.assertEquals(Set.of(
                        new ValueWithTopicFilter<>("sensor1", "home/+/temperature"),
                        new ValueWithTopicFilter<>("sensor2", "home/livingroom/temperature")
                ),
                new HashSet<>(result));
    }

    @Test
    public void testMultiLevelWildcardSubscription() {
        subscriptionTrie.put("home/#", "sensor1");
        subscriptionTrie.put("home/livingroom/temperature", "sensor2");
        subscriptionTrie.put("home/livingroom/humidity", "sensor3");
        List<ValueWithTopicFilter<String>> result = subscriptionTrie.get("home/livingroom/temperature");
        Assert.assertEquals(Set.of(
                        new ValueWithTopicFilter<>("sensor1", "home/#"),
                        new ValueWithTopicFilter<>("sensor2", "home/livingroom/temperature")
                ),
                new HashSet<>(result));
    }

    @Test
    public void testCombinedWildcardsSubscription() {
        subscriptionTrie.put("home/+/temperature", "sensor1");
        subscriptionTrie.put("home/#", "sensor2");
        subscriptionTrie.put("home/livingroom/temperature", "sensor3");
        List<ValueWithTopicFilter<String>> result = subscriptionTrie.get("home/livingroom/temperature");
        Assert.assertEquals(Set.of(
                        new ValueWithTopicFilter<>("sensor1", "home/+/temperature"),
                        new ValueWithTopicFilter<>("sensor2", "home/#"),
                        new ValueWithTopicFilter<>("sensor3", "home/livingroom/temperature")
                ),
                new HashSet<>(result));
    }

    @Test
    public void testWildcardsAndRegularSubscriptions() {
        subscriptionTrie.put("home/+/temperature", "sensor1");
        subscriptionTrie.put("home/livingroom/temperature", "sensor2");
        subscriptionTrie.put("home/#", "sensor3");
        subscriptionTrie.put("home/livingroom/#", "sensor4");
        List<ValueWithTopicFilter<String>> result = subscriptionTrie.get("home/livingroom/temperature");
        Assert.assertEquals(Set.of(
                        new ValueWithTopicFilter<>("sensor1", "home/+/temperature"),
                        new ValueWithTopicFilter<>("sensor2", "home/livingroom/temperature"),
                        new ValueWithTopicFilter<>("sensor3", "home/#"),
                        new ValueWithTopicFilter<>("sensor4", "home/livingroom/#")
                ),
                new HashSet<>(result));
    }

    @Test
    public void testSubscriptionWithDifferentLevels() {
        subscriptionTrie.put("home/+/temperature", "sensor1");
        subscriptionTrie.put("home/livingroom/#", "sensor2");
        subscriptionTrie.put("home/+/+", "sensor3");
        subscriptionTrie.put("home/#", "sensor4");
        List<ValueWithTopicFilter<String>> result = subscriptionTrie.get("home/livingroom/temperature");
        Assert.assertEquals(Set.of(
                        new ValueWithTopicFilter<>("sensor1", "home/+/temperature"),
                        new ValueWithTopicFilter<>("sensor2", "home/livingroom/#"),
                        new ValueWithTopicFilter<>("sensor3", "home/+/+"),
                        new ValueWithTopicFilter<>("sensor4", "home/#")
                ),
                new HashSet<>(result));
    }

    @Test
    public void testSubscriptionWithFirstSlashAndSingleLvlWildcard() {
        subscriptionTrie.put("+/+", "subscription1");
        subscriptionTrie.put("/+", "subscription2");
        subscriptionTrie.put("+", "subscription3");
        subscriptionTrie.put("/#", "subscription4");
        List<ValueWithTopicFilter<String>> result = subscriptionTrie.get("/finance");
        Assert.assertEquals(Set.of(
                        new ValueWithTopicFilter<>("subscription1", "+/+"),
                        new ValueWithTopicFilter<>("subscription2", "/+"),
                        new ValueWithTopicFilter<>("subscription4", "/#")
                ),
                new HashSet<>(result));

        Assert.assertEquals(4, subscriptionCounter.get());

        subscriptionTrie.delete("+/+", s -> true);
        subscriptionTrie.delete("/+", s -> true);
        subscriptionTrie.delete("+", s -> true);
        subscriptionTrie.delete("/#", s -> true);

        Assert.assertEquals(0, subscriptionCounter.get());
    }

    @Test
    public void testSubscriptionWithTopicFilterThatStartsWithSlash() {
        subscriptionTrie.put("/one/two/three/", "subscription1");
        List<ValueWithTopicFilter<String>> result = subscriptionTrie.get("/one/two/three/");
        Assert.assertEquals(Set.of(
                        new ValueWithTopicFilter<>("subscription1", "/one/two/three/")
                ),
                new HashSet<>(result));
    }

    @Test
    public void testWildcardSubscriptionsWithNoLastLvlInTopicName() {
        subscriptionTrie.put("sport/tennis/player1/#", "subscription1");
        subscriptionTrie.put("sport/tennis/player1/+", "subscription2");
        List<ValueWithTopicFilter<String>> result = subscriptionTrie.get("sport/tennis/player1");
        Assert.assertEquals(Set.of(
                        new ValueWithTopicFilter<>("subscription1", "sport/tennis/player1/#")
                ),
                new HashSet<>(result));
    }

    @Test
    public void testWildcardSubscriptionsWithSingleLvlInTopicName() {
        subscriptionTrie.put("sport/#", "subscription1");
        subscriptionTrie.put("sport/+", "subscription2");
        List<ValueWithTopicFilter<String>> result = subscriptionTrie.get("sport");
        Assert.assertEquals(Set.of(
                        new ValueWithTopicFilter<>("subscription1", "sport/#")
                ),
                new HashSet<>(result));
    }

    @Test
    public void testWildcardSubscriptionsWithEmptyLastLvlInTopicName() {
        subscriptionTrie.put("sport/#", "subscription1");
        subscriptionTrie.put("sport/+", "subscription2");
        subscriptionTrie.put("sport/+/+", "subscription3");
        subscriptionTrie.put("sport/football/+", "subscription4");
        List<ValueWithTopicFilter<String>> result = subscriptionTrie.get("sport/football/");
        Assert.assertEquals(Set.of(
                        new ValueWithTopicFilter<>("subscription1", "sport/#"),
                        new ValueWithTopicFilter<>("subscription3", "sport/+/+"),
                        new ValueWithTopicFilter<>("subscription4", "sport/football/+")
                ),
                new HashSet<>(result));

        Assert.assertEquals(4, subscriptionCounter.get());

        subscriptionTrie.delete("sport/+/+", s -> true);

        Assert.assertEquals(3, subscriptionCounter.get());
    }

    @Test
    public void testSubscriptionsWithEmptyLevelInTheMiddle() {
        subscriptionTrie.put("sport/football//match", "subscription1");
        subscriptionTrie.put("sport/football/+/match", "subscription2");
        subscriptionTrie.put("sport/football/#", "subscription3");
        List<ValueWithTopicFilter<String>> result = subscriptionTrie.get("sport/football//match");
        Assert.assertEquals(Set.of(
                        new ValueWithTopicFilter<>("subscription1", "sport/football//match"),
                        new ValueWithTopicFilter<>("subscription2", "sport/football/+/match"),
                        new ValueWithTopicFilter<>("subscription3", "sport/football/#")
                ),
                new HashSet<>(result));

        Assert.assertEquals(3, subscriptionCounter.get());

        subscriptionTrie.delete("sport/football//match", s -> true);
        subscriptionTrie.delete("sport/football/+/match", s -> true);
        subscriptionTrie.delete("sport/football/#", s -> true);

        Assert.assertEquals(0, subscriptionCounter.get());
    }

    @Test
    public void testSubscriptionsWithEmptyLevelAtTheBeginning() {
        subscriptionTrie.put("//sport/football/match", "subscription1");
        subscriptionTrie.put("/+/sport/football/match", "subscription2");
        subscriptionTrie.put("+/+/sport/football/match", "subscription3");
        List<ValueWithTopicFilter<String>> result = subscriptionTrie.get("//sport/football/match");
        Assert.assertEquals(Set.of(
                        new ValueWithTopicFilter<>("subscription1", "//sport/football/match"),
                        new ValueWithTopicFilter<>("subscription2", "/+/sport/football/match"),
                        new ValueWithTopicFilter<>("subscription3", "+/+/sport/football/match")
                ),
                new HashSet<>(result));

        Assert.assertEquals(3, subscriptionCounter.get());

        subscriptionTrie.delete("//sport/football/match", s -> true);
        subscriptionTrie.delete("/+/sport/football/match", s -> true);
        subscriptionTrie.delete("+/+/sport/football/match", s -> true);

        Assert.assertEquals(0, subscriptionCounter.get());
    }

    @Test
    public void testSubscriptionsWithEmptyLevelAtTheEnd() {
        subscriptionTrie.put("football/match//", "subscription1");
        subscriptionTrie.put("football/match/+/", "subscription2");
        subscriptionTrie.put("football/match/+/+", "subscription3");
        subscriptionTrie.put("football/match/#", "subscription4");
        subscriptionTrie.put("football/match/+//", "subscription5");
        List<ValueWithTopicFilter<String>> result = subscriptionTrie.get("football/match//");
        Assert.assertEquals(Set.of(
                        new ValueWithTopicFilter<>("subscription1", "football/match//"),
                        new ValueWithTopicFilter<>("subscription2", "football/match/+/"),
                        new ValueWithTopicFilter<>("subscription3", "football/match/+/+"),
                        new ValueWithTopicFilter<>("subscription4", "football/match/#")
                ),
                new HashSet<>(result));

        Assert.assertEquals(5, subscriptionCounter.get());

        subscriptionTrie.delete("football/match//", s -> true);
        subscriptionTrie.delete("football/match/+/", s -> true);
        subscriptionTrie.delete("football/match/+/+", s -> true);
        subscriptionTrie.delete("football/match/#", s -> true);
        subscriptionTrie.delete("football/match/+//", s -> true);

        Assert.assertEquals(0, subscriptionCounter.get());
    }


}
