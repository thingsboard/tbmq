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
public class ConcurrentMapTopicTrieTest {

    private ConcurrentMapTopicTrie<String> concurrentMapTopicTrie;

    @Before
    public void before(){
        this.concurrentMapTopicTrie = new ConcurrentMapTopicTrie<>();
    }

    @Test
    public void testDelete(){
        concurrentMapTopicTrie.put("1/2", "test");
        concurrentMapTopicTrie.delete("1/2", "test");
        List<String> result = concurrentMapTopicTrie.get("1/2");
        Assert.assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void testGet(){
        concurrentMapTopicTrie.put("1/2/3", "test1");
        concurrentMapTopicTrie.put("1/+/3", "test2");
        concurrentMapTopicTrie.put("1/#", "test3");
        concurrentMapTopicTrie.put("1/2/#", "test4");
        concurrentMapTopicTrie.put("1/+/4", "test5");
        concurrentMapTopicTrie.put("1/2/4", "test6");
        concurrentMapTopicTrie.put("#", "test7");
        concurrentMapTopicTrie.put("+/2/3", "test8");
        concurrentMapTopicTrie.put("+/2/+", "test9");
        List<String> result = concurrentMapTopicTrie.get("1/2/3");
        Assert.assertEquals(Set.of("test1", "test2", "test3", "test4", "test7", "test8", "test9"),
                new HashSet<>(result));
    }

}
