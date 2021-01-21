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
package org.thingsboard.mqtt.broker.service.subscription;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
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
public class SubscriptionTrieTestSuite {

    private ConcurrentMapSubscriptionTrie<String> subscriptionTrie;
    private AtomicInteger subscriptionCounter;
    private AtomicLong nodesCounter;

    @Before
    public void before(){
        this.subscriptionCounter = new AtomicInteger(0);
        this.nodesCounter = new AtomicLong(0);
        StatsManager statsManagerMock = Mockito.mock(StatsManager.class);
        Mockito.when(statsManagerMock.createSubscriptionSizeCounter()).thenReturn(subscriptionCounter);
        Mockito.when(statsManagerMock.createSubscriptionTrieNodesCounter()).thenReturn(nodesCounter);
        this.subscriptionTrie = new ConcurrentMapSubscriptionTrie<>(statsManagerMock);
    }

    @Test
    public void testSaveSameSession(){
        subscriptionTrie.put("1/2", "test");
        Assert.assertEquals(1, subscriptionTrie.get("1/2").size());
        subscriptionTrie.put("1/2", "test");
        Assert.assertEquals(1, subscriptionTrie.get("1/2").size());
    }

    @Test
    public void testDelete(){
        subscriptionTrie.put("1/2", "test");
        subscriptionTrie.delete("1/2", s -> s.equals("test"));
        List<String> result = subscriptionTrie.get("1/2");
        Assert.assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void testGet(){
        subscriptionTrie.put("1/22/3", "test1");
        subscriptionTrie.put("1/+/3", "test2");
        subscriptionTrie.put("1/#", "test3");
        subscriptionTrie.put("1/22/#", "test4");
        subscriptionTrie.put("1/+/4", "test5");
        subscriptionTrie.put("1/22/4", "test6");
        subscriptionTrie.put("#", "test7");
        subscriptionTrie.put("+/22/3", "test8");
        subscriptionTrie.put("+/22/+", "test9");
        List<String> result = subscriptionTrie.get("1/22/3");
        Assert.assertEquals(Set.of("test1", "test2", "test3", "test4", "test7", "test8", "test9"),
                new HashSet<>(result));
    }

    @Test
    public void testTopicsWith$(){
        subscriptionTrie.put("#", "test1");
        subscriptionTrie.put("+/monitor/Clients", "test2");
        subscriptionTrie.put("$SYS/#", "test3");
        subscriptionTrie.put("$SYS/monitor/+", "test4");
        List<String> result = subscriptionTrie.get("$SYS/monitor/Clients");
        Assert.assertEquals(Set.of("test3", "test4"),
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
}
