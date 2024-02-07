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
package org.thingsboard.mqtt.broker.service.util;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.util.CollectionsUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class CollectionsUtilTest {

    private static Comparator<TopicSubscription> getComparator() {
        return Comparator.comparing(TopicSubscription::getTopicFilter).thenComparing(TopicSubscription::getQos);
    }

    @Test
    public void testGetAddedValues_0() {
        Set<TopicSubscription> prevSubscriptions = Set.of(new TopicSubscription("1", 0), new TopicSubscription("2", 0));
        Set<TopicSubscription> newSubscriptions = Set.of(new TopicSubscription("1", 1), new TopicSubscription("3", 0));
        Set<TopicSubscription> addedSubscriptions = CollectionsUtil.getAddedValues(newSubscriptions, prevSubscriptions, getComparator());

        Assert.assertEquals(2, addedSubscriptions.size());
        List<TopicSubscription> addedSubscriptionsList = new ArrayList<>(addedSubscriptions);
        Assert.assertEquals("1", addedSubscriptionsList.get(0).getTopicFilter());
        Assert.assertEquals(1, addedSubscriptionsList.get(0).getQos());
        Assert.assertEquals("3", addedSubscriptionsList.get(1).getTopicFilter());
        Assert.assertEquals(0, addedSubscriptionsList.get(1).getQos());
    }

    @Test
    public void testGetAddedValues_1() {
        Set<TopicSubscription> prevSubscriptions = Set.of(new TopicSubscription("1", 1), new TopicSubscription("2", 0));
        Set<TopicSubscription> newSubscriptions = Set.of(new TopicSubscription("1", 0), new TopicSubscription("2", 0));
        Set<TopicSubscription> addedSubscriptions = CollectionsUtil.getAddedValues(newSubscriptions, prevSubscriptions, getComparator());

        Assert.assertEquals(1, addedSubscriptions.size());
        List<TopicSubscription> addedSubscriptionsList = new ArrayList<>(addedSubscriptions);
        Assert.assertEquals("1", addedSubscriptionsList.get(0).getTopicFilter());
        Assert.assertEquals(0, addedSubscriptionsList.get(0).getQos());
    }

    @Test
    public void testGetAddedValues_2() {
        Set<TopicSubscription> prevSubscriptions = Set.of(new TopicSubscription("1", 0), new TopicSubscription("2", 0));
        Set<TopicSubscription> newSubscriptions = Set.of(new TopicSubscription("1", 0), new TopicSubscription("2", 0));
        Set<TopicSubscription> addedSubscriptions = CollectionsUtil.getAddedValues(newSubscriptions, prevSubscriptions, getComparator());
        Assert.assertEquals(0, addedSubscriptions.size());
    }

    @Test
    public void testGetAddedValues_3() {
        Set<TopicSubscription> prevSubscriptions = Set.of(new TopicSubscription("1", 0));
        Set<TopicSubscription> newSubscriptions = Set.of(new TopicSubscription("1", 0), new TopicSubscription("2", 2));
        Set<TopicSubscription> addedSubscriptions = CollectionsUtil.getAddedValues(newSubscriptions, prevSubscriptions, getComparator());

        Assert.assertEquals(1, addedSubscriptions.size());
        List<TopicSubscription> addedSubscriptionsList = new ArrayList<>(addedSubscriptions);
        Assert.assertEquals("2", addedSubscriptionsList.get(0).getTopicFilter());
        Assert.assertEquals(2, addedSubscriptionsList.get(0).getQos());
    }

    @Test
    public void testGetRemovedValues_0() {
        Set<TopicSubscription> prevSubscriptions = Set.of(new TopicSubscription("1", 0), new TopicSubscription("2", 0));
        Set<TopicSubscription> newSubscriptions = Set.of(new TopicSubscription("1", 1), new TopicSubscription("3", 0));
        Set<TopicSubscription> removedSubscriptions = CollectionsUtil.getRemovedValues(newSubscriptions, prevSubscriptions, getComparator());

        Assert.assertEquals(2, removedSubscriptions.size());
        List<TopicSubscription> removedSubscriptionsList = new ArrayList<>(removedSubscriptions);
        Assert.assertEquals("1", removedSubscriptionsList.get(0).getTopicFilter());
        Assert.assertEquals(0, removedSubscriptionsList.get(0).getQos());
        Assert.assertEquals("2", removedSubscriptionsList.get(1).getTopicFilter());
        Assert.assertEquals(0, removedSubscriptionsList.get(1).getQos());
    }

    @Test
    public void testGetRemovedValues_1() {
        Set<TopicSubscription> prevSubscriptions = Set.of(new TopicSubscription("1", 0), new TopicSubscription("2", 2));
        Set<TopicSubscription> newSubscriptions = Set.of(new TopicSubscription("1", 1), new TopicSubscription("2", 2));
        Set<TopicSubscription> removedSubscriptions = CollectionsUtil.getRemovedValues(newSubscriptions, prevSubscriptions, getComparator());

        Assert.assertEquals(1, removedSubscriptions.size());
        List<TopicSubscription> removedSubscriptionsList = new ArrayList<>(removedSubscriptions);
        Assert.assertEquals("1", removedSubscriptionsList.get(0).getTopicFilter());
        Assert.assertEquals(0, removedSubscriptionsList.get(0).getQos());
    }

    @Test
    public void testGetRemovedValues_2() {
        Set<TopicSubscription> prevSubscriptions = Set.of(new TopicSubscription("1", 0), new TopicSubscription("2", 0));
        Set<TopicSubscription> newSubscriptions = Set.of(new TopicSubscription("1", 0), new TopicSubscription("2", 0));
        Set<TopicSubscription> removedSubscriptions = CollectionsUtil.getRemovedValues(newSubscriptions, prevSubscriptions, getComparator());
        Assert.assertEquals(0, removedSubscriptions.size());
    }

    @Test
    public void testGetRemovedValues_3() {
        Set<TopicSubscription> prevSubscriptions = Set.of(new TopicSubscription("1", 0), new TopicSubscription("2", 1));
        Set<TopicSubscription> newSubscriptions = Set.of(new TopicSubscription("1", 0));
        Set<TopicSubscription> removedSubscriptions = CollectionsUtil.getRemovedValues(newSubscriptions, prevSubscriptions, getComparator());

        Assert.assertEquals(1, removedSubscriptions.size());
        List<TopicSubscription> removedSubscriptionsList = new ArrayList<>(removedSubscriptions);
        Assert.assertEquals("2", removedSubscriptionsList.get(0).getTopicFilter());
        Assert.assertEquals(1, removedSubscriptionsList.get(0).getQos());
    }

    @Test
    public void testGetAddedAndRemoved() {
        Set<TopicSubscription> prevSubscriptions = Set.of(
                new TopicSubscription("1", 0),
                new TopicSubscription("2", 1),
                new TopicSubscription("3", 2));

        Set<TopicSubscription> newSubscriptions = Set.of(
                new TopicSubscription("1", 0),
                new TopicSubscription("2", 2),
                new TopicSubscription("4", 1));

        Set<TopicSubscription> removedSubscriptions = CollectionsUtil.getRemovedValues(newSubscriptions, prevSubscriptions, getComparator());

        Assert.assertEquals(2, removedSubscriptions.size());
        List<TopicSubscription> removedSubscriptionsList = new ArrayList<>(removedSubscriptions);
        Assert.assertEquals("2", removedSubscriptionsList.get(0).getTopicFilter());
        Assert.assertEquals(1, removedSubscriptionsList.get(0).getQos());
        Assert.assertEquals("3", removedSubscriptionsList.get(1).getTopicFilter());
        Assert.assertEquals(2, removedSubscriptionsList.get(1).getQos());

        Set<TopicSubscription> addedSubscriptions = CollectionsUtil.getAddedValues(newSubscriptions, prevSubscriptions, getComparator());

        Assert.assertEquals(2, addedSubscriptions.size());
        List<TopicSubscription> addedSubscriptionsList = new ArrayList<>(addedSubscriptions);
        Assert.assertEquals("2", addedSubscriptionsList.get(0).getTopicFilter());
        Assert.assertEquals(2, addedSubscriptionsList.get(0).getQos());
        Assert.assertEquals("4", addedSubscriptionsList.get(1).getTopicFilter());
        Assert.assertEquals(1, addedSubscriptionsList.get(1).getQos());
    }
}
