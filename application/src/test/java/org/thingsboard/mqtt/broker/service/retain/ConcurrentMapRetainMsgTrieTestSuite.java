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
package org.thingsboard.mqtt.broker.service.retain;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class ConcurrentMapRetainMsgTrieTestSuite {

    private ConcurrentMapRetainMsgTrie<String> retainMsgTrie;

    @Before
    public void before(){
        this.retainMsgTrie = new ConcurrentMapRetainMsgTrie<>();
    }

    @Test
    public void testDelete(){
        retainMsgTrie.put("1/2", "test");
        retainMsgTrie.delete("1/2");
        List<String> result = retainMsgTrie.get("#");
        Assert.assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void testGetSingleLevel(){
        retainMsgTrie.put("1/11/3", "test1");
        retainMsgTrie.put("1/22/3", "test2");
        retainMsgTrie.put("1/33/3", "test3");
        retainMsgTrie.put("1/22/4", "test4");
        List<String> result = retainMsgTrie.get("1/+/3");
        Assert.assertEquals(Set.of("test1", "test2", "test3"),
                new HashSet<>(result));
    }

    @Test
    public void testGetMultipleLevel(){
        retainMsgTrie.put("1/11/3", "test1");
        retainMsgTrie.put("1/22/3", "test2");
        retainMsgTrie.put("1/33/3", "test3");
        retainMsgTrie.put("1/22/4", "test4");
        retainMsgTrie.put("1/22/5", "test5");
        retainMsgTrie.put("2/22/5", "test6");
        retainMsgTrie.put("2/1/5", "test7");
        List<String> result = retainMsgTrie.get("1/#");
        Assert.assertEquals(Set.of("test1", "test2", "test3", "test4", "test5"),
                new HashSet<>(result));
    }

    @Test
    public void testGetWith$(){
        retainMsgTrie.put("$SYS/monitor/Clients", "test1");
        Assert.assertTrue(retainMsgTrie.get("#").isEmpty());
        Assert.assertTrue(retainMsgTrie.get("+/monitor/Clients").isEmpty());
        Assert.assertEquals(Set.of("test1"), new HashSet<>(retainMsgTrie.get("$SYS/#")));
        Assert.assertEquals(Set.of("test1"), new HashSet<>(retainMsgTrie.get("$SYS/monitor/+")));
    }

}
