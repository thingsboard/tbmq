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
package org.thingsboard.mqtt.broker.service.util;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.util.CollectionsUtil;

import java.util.Comparator;
import java.util.Set;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class CollectionsUtilTest {
    @Test
    public void testGetAddedValues() {
        Set<TopicSubscription> prevSubscriptions = Set.of(new TopicSubscription("1", 0), new TopicSubscription("2", 0));
        Set<TopicSubscription> newSubscriptions = Set.of(new TopicSubscription("1", 1), new TopicSubscription("3", 0));
        Set<TopicSubscription> addedSubscriptions = CollectionsUtil.getAddedValues(newSubscriptions, prevSubscriptions, Comparator.comparing(TopicSubscription::getTopic)
                .thenComparing(TopicSubscription::getQos));
        Assert.assertEquals(newSubscriptions, addedSubscriptions);
    }

    @Test
    public void testGetAddedValues_1() {
        Set<TopicSubscription> prevSubscriptions = Set.of(new TopicSubscription("1", 0), new TopicSubscription("2", 0));
        Set<TopicSubscription> newSubscriptions = Set.of(new TopicSubscription("1", 1), new TopicSubscription("2", 0));
        Set<TopicSubscription> addedSubscriptions = CollectionsUtil.getAddedValues(newSubscriptions, prevSubscriptions, Comparator.comparing(TopicSubscription::getTopic)
                .thenComparing(TopicSubscription::getQos));
        Assert.assertEquals(Set.of(new TopicSubscription("1", 1)), addedSubscriptions);
    }

    @Test
    public void testGetAddedValues_2() {
        Set<TopicSubscription> prevSubscriptions = Set.of(new TopicSubscription("1", 0), new TopicSubscription("2", 0));
        Set<TopicSubscription> newSubscriptions = Set.of(new TopicSubscription("1", 0), new TopicSubscription("2", 0));
        Set<TopicSubscription> addedSubscriptions = CollectionsUtil.getAddedValues(newSubscriptions, prevSubscriptions, Comparator.comparing(TopicSubscription::getTopic)
                .thenComparing(TopicSubscription::getQos));
        Assert.assertEquals(Set.of(), addedSubscriptions);
    }

    @Test
    public void testGetRemovedValues_0() {
        Set<TopicSubscription> prevSubscriptions = Set.of(new TopicSubscription("1", 0), new TopicSubscription("2", 0));
        Set<TopicSubscription> newSubscriptions = Set.of(new TopicSubscription("1", 1), new TopicSubscription("3", 0));
        Set<TopicSubscription> removedSubscriptions = CollectionsUtil.getRemovedValues(newSubscriptions, prevSubscriptions, Comparator.comparing(TopicSubscription::getTopic)
                .thenComparing(TopicSubscription::getQos));
        Assert.assertEquals(prevSubscriptions, removedSubscriptions);
    }

    @Test
    public void testGetRemovedValues_1() {
        Set<TopicSubscription> prevSubscriptions = Set.of(new TopicSubscription("1", 0), new TopicSubscription("2", 0));
        Set<TopicSubscription> newSubscriptions = Set.of(new TopicSubscription("1", 1), new TopicSubscription("2", 0));
        Set<TopicSubscription> removedSubscriptions = CollectionsUtil.getRemovedValues(newSubscriptions, prevSubscriptions, Comparator.comparing(TopicSubscription::getTopic)
                .thenComparing(TopicSubscription::getQos));
        Assert.assertEquals(Set.of(new TopicSubscription("1", 0)), removedSubscriptions);
    }

    @Test
    public void testGetRemovedValues_2() {
        Set<TopicSubscription> prevSubscriptions = Set.of(new TopicSubscription("1", 0), new TopicSubscription("2", 0));
        Set<TopicSubscription> newSubscriptions = Set.of(new TopicSubscription("1", 0), new TopicSubscription("2", 0));
        Set<TopicSubscription> removedSubscriptions = CollectionsUtil.getRemovedValues(newSubscriptions, prevSubscriptions, Comparator.comparing(TopicSubscription::getTopic)
                .thenComparing(TopicSubscription::getQos));
        Assert.assertEquals(Set.of(), removedSubscriptions);
    }

    @Test
    public void testGetRemovedValues_3() {
        Set<TopicSubscription> prevSubscriptions = Set.of(new TopicSubscription("1", 0), new TopicSubscription("2", 0));
        Set<TopicSubscription> newSubscriptions = Set.of(new TopicSubscription("1", 0));
        Set<TopicSubscription> removedSubscriptions = CollectionsUtil.getRemovedValues(newSubscriptions, prevSubscriptions, Comparator.comparing(TopicSubscription::getTopic)
                .thenComparing(TopicSubscription::getQos));
        Assert.assertEquals(Set.of(new TopicSubscription("2", 0)), removedSubscriptions);
    }
}
