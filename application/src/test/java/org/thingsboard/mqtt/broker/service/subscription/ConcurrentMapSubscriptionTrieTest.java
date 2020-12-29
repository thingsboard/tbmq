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
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class ConcurrentMapSubscriptionTrieTest {

    private ConcurrentMapSubscriptionTrie<String> subscriptionTrie;

    @Before
    public void before(){
        this.subscriptionTrie = new ConcurrentMapSubscriptionTrie<>();
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

}
