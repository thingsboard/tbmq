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
package org.thingsboard.mqtt.broker.service.mqtt.retain;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.exception.RetainMsgTrieClearException;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(MockitoJUnitRunner.class)
@Slf4j
public class ConcurrentMapRetainMsgTrieTest {

    private ConcurrentMapRetainMsgTrie<String> retainMsgTrie;
    private AtomicInteger retainedMsgCounter;
    private AtomicLong nodesCounter;

    @Before
    public void before() {
        this.retainedMsgCounter = new AtomicInteger(0);
        this.nodesCounter = new AtomicLong(0);
        StatsManager statsManagerMock = Mockito.mock(StatsManager.class);
        Mockito.when(statsManagerMock.createRetainMsgSizeCounter()).thenReturn(retainedMsgCounter);
        Mockito.when(statsManagerMock.createRetainMsgTrieNodesCounter()).thenReturn(nodesCounter);
        this.retainMsgTrie = new ConcurrentMapRetainMsgTrie<>(statsManagerMock);
    }

    @Test
    public void testSaveSameTopic() {
        retainMsgTrie.put("1/2", "test1");
        Assert.assertEquals(1, retainMsgTrie.get("1/2").size());
        Assert.assertEquals("test1", retainMsgTrie.get("1/2").get(0));
        retainMsgTrie.put("1/2", "test2");
        Assert.assertEquals(1, retainMsgTrie.get("1/2").size());
        Assert.assertEquals("test2", retainMsgTrie.get("1/2").get(0));
    }

    @Test
    public void testDelete() {
        retainMsgTrie.put("1/2", "test");
        retainMsgTrie.delete("1/2");
        List<String> result = retainMsgTrie.get("#");
        Assert.assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void testSize() {
        retainMsgTrie.put("1/1", "test1");
        retainMsgTrie.put("1/2", "test2");
        retainMsgTrie.put("1/3", "test3");
        retainMsgTrie.put("1/4", "test4");
        retainMsgTrie.put("1/5", "test5");
        retainMsgTrie.delete("1/2");
        retainMsgTrie.put("1/5", "test55");
        retainMsgTrie.delete("1/3");
        Assert.assertEquals(3, retainMsgTrie.size());
    }

    @Test
    public void testGetSingleLevel() {
        retainMsgTrie.put("1/11/3", "test1");
        retainMsgTrie.put("1/22/3", "test2");
        retainMsgTrie.put("1/33/3", "test3");
        retainMsgTrie.put("1/22/4", "test4");
        retainMsgTrie.put("2/11/3", "test5");
        retainMsgTrie.put("2/22/3", "test6");
        retainMsgTrie.put("2/33/3", "test7");
        retainMsgTrie.put("2/22/4", "test8");
        List<String> result = retainMsgTrie.get("1/+/3");
        Assert.assertEquals(Set.of("test1", "test2", "test3"),
                new HashSet<>(result));
        result = retainMsgTrie.get("+/22/3");
        Assert.assertEquals(Set.of("test2", "test6"),
                new HashSet<>(result));
        result = retainMsgTrie.get("+/33/+");
        Assert.assertEquals(Set.of("test3", "test7"),
                new HashSet<>(result));
    }

    @Test
    public void testGetMultipleLevel() {
        retainMsgTrie.put("1/11/3", "test1");
        retainMsgTrie.put("1/22/3", "test2");
        retainMsgTrie.put("1/33/3", "test3");
        retainMsgTrie.put("1/22/4", "test4");
        retainMsgTrie.put("1/22/5", "test5");
        retainMsgTrie.put("2/22/5", "test6");
        retainMsgTrie.put("2/1/5", "test7");
        retainMsgTrie.put("2/22/6", "test8");
        retainMsgTrie.put("2/22/7", "test9");
        List<String> result = retainMsgTrie.get("1/#");
        Assert.assertEquals(Set.of("test1", "test2", "test3", "test4", "test5"),
                new HashSet<>(result));
        result = retainMsgTrie.get("2/22/#");
        Assert.assertEquals(Set.of("test6", "test8", "test9"),
                new HashSet<>(result));
    }

    @Test
    public void testGetWith$() {
        retainMsgTrie.put("$SYS/monitor/Clients", "test1");
        Assert.assertTrue(retainMsgTrie.get("#").isEmpty());
        Assert.assertTrue(retainMsgTrie.get("+/monitor/Clients").isEmpty());
        Assert.assertEquals(Set.of("test1"), new HashSet<>(retainMsgTrie.get("$SYS/#")));
        Assert.assertEquals(Set.of("test1"), new HashSet<>(retainMsgTrie.get("$SYS/monitor/+")));
    }

    @Test
    public void testRetainedMsgCount() {
        for (int i = 0; i < 10; i++) {
            retainMsgTrie.put(Integer.toString(i), "val");
        }
        Assert.assertEquals(10, retainedMsgCounter.get());
        for (int i = 0; i < 9; i++) {
            retainMsgTrie.delete(Integer.toString(i));
        }
        Assert.assertEquals(1, retainedMsgCounter.get());
    }

    @Test
    public void testNodeCount_Basic() {
        for (int i = 0; i < 10; i++) {
            retainMsgTrie.put(Integer.toString(i), "val");
        }
        Assert.assertEquals(10, nodesCounter.get());
    }

    @Test
    public void testNodeCount_TwoValues() {
        for (int i = 0; i < 10; i++) {
            retainMsgTrie.put(Integer.toString(i), "val1");
            retainMsgTrie.put(Integer.toString(i), "val2");
        }
        Assert.assertEquals(10, nodesCounter.get());
    }

    @Test
    public void testNodeCount_RemoveValues() {
        for (int i = 0; i < 10; i++) {
            retainMsgTrie.put(Integer.toString(i), "val1");
            retainMsgTrie.put(Integer.toString(i), "val2");
            retainMsgTrie.delete(Integer.toString(i));
            retainMsgTrie.delete(Integer.toString(i));
        }
        Assert.assertEquals(10, nodesCounter.get());
    }

    @Test
    public void testNodeCount_MultipleLevels() {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 3; j++) {
                retainMsgTrie.put(i + "/" + j, "val");
            }
        }
        // 10 first level + 30 second level
        Assert.assertEquals(10 + 30, nodesCounter.get());
    }

    @Test
    public void testClearTrie_Basic() throws RetainMsgTrieClearException {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 3; j++) {
                retainMsgTrie.put(i + "/" + j, "val");
            }
        }

        retainMsgTrie.delete("0/0");
        retainMsgTrie.delete("0/1");
        retainMsgTrie.delete("0/2");
        retainMsgTrie.delete("1/0");

        retainMsgTrie.setWaitForClearLockMs(100);
        retainMsgTrie.clearEmptyNodes();
        // should clear 0/0, 0/1, 0/2, 0 and 1/0 nodes
        Assert.assertEquals(40 - 5, nodesCounter.get());
    }

    @Test
    public void testClearTrie_ClearAll() throws RetainMsgTrieClearException {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 3; j++) {
                retainMsgTrie.put(i + "/" + j, "val");
                retainMsgTrie.delete(i + "/" + j);
            }
        }
        Assert.assertEquals(40, nodesCounter.get());

        retainMsgTrie.setWaitForClearLockMs(100);
        retainMsgTrie.clearEmptyNodes();

        Assert.assertEquals(0, nodesCounter.get());
    }
}