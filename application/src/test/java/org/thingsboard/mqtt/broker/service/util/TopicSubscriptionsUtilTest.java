/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.common.data.subscription.ClientTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.util.TopicSubscriptionsUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class TopicSubscriptionsUtilTest {

    @Test
    public void testGetSubscriptionsUpdate_0() {
        Set<TopicSubscription> prevSubscriptions = Set.of(new ClientTopicSubscription("1", 0), new ClientTopicSubscription("2", 0));
        Set<TopicSubscription> newSubscriptions = Set.of(new ClientTopicSubscription("1", 1), new ClientTopicSubscription("3", 0));
        TopicSubscriptionsUtil.SubscriptionsUpdate subscriptionsUpdate = TopicSubscriptionsUtil.getSubscriptionsUpdate(prevSubscriptions, newSubscriptions);

        Set<TopicSubscription> toSubscribe = subscriptionsUpdate.getToSubscribe();
        Set<TopicSubscription> toUnsubscribe = subscriptionsUpdate.getToUnsubscribe();

        Assert.assertEquals(1, toUnsubscribe.size());
        List<TopicSubscription> removedSubscriptionsList = new ArrayList<>(toUnsubscribe);
        Assert.assertEquals("2", removedSubscriptionsList.get(0).getTopicFilter());
        Assert.assertEquals(0, removedSubscriptionsList.get(0).getQos());

        Assert.assertEquals(2, toSubscribe.size());
        List<TopicSubscription> addedSubscriptionsList = new ArrayList<>(toSubscribe);
        Assert.assertEquals("1", addedSubscriptionsList.get(0).getTopicFilter());
        Assert.assertEquals(1, addedSubscriptionsList.get(0).getQos());
        Assert.assertEquals("3", addedSubscriptionsList.get(1).getTopicFilter());
        Assert.assertEquals(0, addedSubscriptionsList.get(1).getQos());
    }

    @Test
    public void testGetSubscriptionsUpdate_1() {
        Set<TopicSubscription> prevSubscriptions = Set.of(new ClientTopicSubscription("1", 0, "s1"), new ClientTopicSubscription("2", 0));
        Set<TopicSubscription> newSubscriptions = Set.of(new ClientTopicSubscription("1", 1, "s2"), new ClientTopicSubscription("2", 0, "s3"));
        TopicSubscriptionsUtil.SubscriptionsUpdate subscriptionsUpdate = TopicSubscriptionsUtil.getSubscriptionsUpdate(prevSubscriptions, newSubscriptions);

        Set<TopicSubscription> toSubscribe = subscriptionsUpdate.getToSubscribe();
        Set<TopicSubscription> toUnsubscribe = subscriptionsUpdate.getToUnsubscribe();

        Assert.assertEquals(0, toUnsubscribe.size());

        Assert.assertEquals(2, toSubscribe.size());
        List<TopicSubscription> addedSubscriptionsList = new ArrayList<>(toSubscribe);
        Assert.assertEquals("1", addedSubscriptionsList.get(0).getTopicFilter());
        Assert.assertEquals(1, addedSubscriptionsList.get(0).getQos());
        Assert.assertEquals("s2", addedSubscriptionsList.get(0).getShareName());
        Assert.assertEquals("2", addedSubscriptionsList.get(1).getTopicFilter());
        Assert.assertEquals(0, addedSubscriptionsList.get(1).getQos());
        Assert.assertEquals("s3", addedSubscriptionsList.get(1).getShareName());
    }

    @Test
    public void testGetSubscriptionsUpdate_2() {
        Set<TopicSubscription> prevSubscriptions = Set.of();
        Set<TopicSubscription> newSubscriptions = Set.of(new ClientTopicSubscription("1", 1), new ClientTopicSubscription("2", 2));
        TopicSubscriptionsUtil.SubscriptionsUpdate subscriptionsUpdate = TopicSubscriptionsUtil.getSubscriptionsUpdate(prevSubscriptions, newSubscriptions);

        Set<TopicSubscription> toSubscribe = subscriptionsUpdate.getToSubscribe();
        Set<TopicSubscription> toUnsubscribe = subscriptionsUpdate.getToUnsubscribe();

        Assert.assertEquals(0, toUnsubscribe.size());

        Assert.assertEquals(2, toSubscribe.size());
        List<TopicSubscription> addedSubscriptionsList = new ArrayList<>(toSubscribe);
        Assert.assertEquals("1", addedSubscriptionsList.get(0).getTopicFilter());
        Assert.assertEquals(1, addedSubscriptionsList.get(0).getQos());
        Assert.assertEquals("2", addedSubscriptionsList.get(1).getTopicFilter());
        Assert.assertEquals(2, addedSubscriptionsList.get(1).getQos());
    }

    @Test
    public void testGetSubscriptionsUpdate_3() {
        Set<TopicSubscription> prevSubscriptions = Set.of(
                new ClientTopicSubscription("1", 0, new SubscriptionOptions()),
                new ClientTopicSubscription("2", 1,
                        new SubscriptionOptions(true, true, SubscriptionOptions.RetainHandlingPolicy.SEND_AT_SUBSCRIBE))
        );
        Set<TopicSubscription> newSubscriptions = Set.of(
                new ClientTopicSubscription("1", 0,
                        new SubscriptionOptions(true, false, SubscriptionOptions.RetainHandlingPolicy.SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS)),
                new ClientTopicSubscription("2", 0, "s3"));
        TopicSubscriptionsUtil.SubscriptionsUpdate subscriptionsUpdate = TopicSubscriptionsUtil.getSubscriptionsUpdate(prevSubscriptions, newSubscriptions);

        Set<TopicSubscription> toSubscribe = subscriptionsUpdate.getToSubscribe();
        Set<TopicSubscription> toUnsubscribe = subscriptionsUpdate.getToUnsubscribe();

        Assert.assertEquals(0, toUnsubscribe.size());

        Assert.assertEquals(2, toSubscribe.size());
        List<TopicSubscription> addedSubscriptionsList = new ArrayList<>(toSubscribe);
        Assert.assertEquals("1", addedSubscriptionsList.get(0).getTopicFilter());
        Assert.assertEquals(0, addedSubscriptionsList.get(0).getQos());
        Assert.assertNull(addedSubscriptionsList.get(0).getShareName());
        Assert.assertEquals(SubscriptionOptions.RetainHandlingPolicy.SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS, addedSubscriptionsList.get(0).getOptions().getRetainHandling());

        Assert.assertEquals("2", addedSubscriptionsList.get(1).getTopicFilter());
        Assert.assertEquals(0, addedSubscriptionsList.get(1).getQos());
        Assert.assertEquals("s3", addedSubscriptionsList.get(1).getShareName());
        Assert.assertFalse(addedSubscriptionsList.get(1).getOptions().isNoLocal());
        Assert.assertFalse(addedSubscriptionsList.get(1).getOptions().isRetainAsPublish());
        Assert.assertEquals(SubscriptionOptions.RetainHandlingPolicy.SEND_AT_SUBSCRIBE, addedSubscriptionsList.get(1).getOptions().getRetainHandling());
    }

    @Test
    public void testGetSubscriptionsUpdate_4() {
        Set<TopicSubscription> prevSubscriptions = Set.of(
                new ClientTopicSubscription("1", 0, null, new SubscriptionOptions(), -1),
                new ClientTopicSubscription("2", 1, "shared",
                        new SubscriptionOptions(true, true, SubscriptionOptions.RetainHandlingPolicy.SEND_AT_SUBSCRIBE), 1)
        );
        Set<TopicSubscription> newSubscriptions = Set.of(
                new ClientTopicSubscription("1", 0, null, new SubscriptionOptions(), 1),
                new ClientTopicSubscription("2", 1, "shared",
                        new SubscriptionOptions(true, true, SubscriptionOptions.RetainHandlingPolicy.SEND_AT_SUBSCRIBE), 2)
        );
        TopicSubscriptionsUtil.SubscriptionsUpdate subscriptionsUpdate = TopicSubscriptionsUtil.getSubscriptionsUpdate(prevSubscriptions, newSubscriptions);

        Set<TopicSubscription> toSubscribe = subscriptionsUpdate.getToSubscribe();
        Set<TopicSubscription> toUnsubscribe = subscriptionsUpdate.getToUnsubscribe();

        Assert.assertEquals(0, toUnsubscribe.size());

        Assert.assertEquals(2, toSubscribe.size());
        List<TopicSubscription> addedSubscriptionsList = new ArrayList<>(toSubscribe);
        Assert.assertEquals("1", addedSubscriptionsList.get(0).getTopicFilter());
        Assert.assertEquals(0, addedSubscriptionsList.get(0).getQos());
        Assert.assertNull(addedSubscriptionsList.get(0).getShareName());
        Assert.assertEquals(SubscriptionOptions.RetainHandlingPolicy.SEND_AT_SUBSCRIBE, addedSubscriptionsList.get(0).getOptions().getRetainHandling());
        Assert.assertFalse(addedSubscriptionsList.get(0).getOptions().isNoLocal());
        Assert.assertEquals(1, addedSubscriptionsList.get(0).getSubscriptionId());

        Assert.assertEquals("2", addedSubscriptionsList.get(1).getTopicFilter());
        Assert.assertEquals(1, addedSubscriptionsList.get(1).getQos());
        Assert.assertEquals("shared", addedSubscriptionsList.get(1).getShareName());
        Assert.assertTrue(addedSubscriptionsList.get(1).getOptions().isNoLocal());
        Assert.assertTrue(addedSubscriptionsList.get(1).getOptions().isRetainAsPublish());
        Assert.assertEquals(SubscriptionOptions.RetainHandlingPolicy.SEND_AT_SUBSCRIBE, addedSubscriptionsList.get(1).getOptions().getRetainHandling());
        Assert.assertEquals(2, addedSubscriptionsList.get(1).getSubscriptionId());
    }

    @Test
    public void testGetSubscriptionsUpdate_5() {
        Set<TopicSubscription> prevSubscriptions = Set.of(new ClientTopicSubscription("1", 0), new ClientTopicSubscription("2", 2));
        Set<TopicSubscription> newSubscriptions = Set.of(new ClientTopicSubscription("3", 1));
        TopicSubscriptionsUtil.SubscriptionsUpdate subscriptionsUpdate = TopicSubscriptionsUtil.getSubscriptionsUpdate(prevSubscriptions, newSubscriptions);

        Set<TopicSubscription> toSubscribe = subscriptionsUpdate.getToSubscribe();
        Set<TopicSubscription> toUnsubscribe = subscriptionsUpdate.getToUnsubscribe();

        Assert.assertEquals(2, toUnsubscribe.size());
        List<TopicSubscription> removedSubscriptionsList = new ArrayList<>(toUnsubscribe);
        Assert.assertEquals("1", removedSubscriptionsList.get(0).getTopicFilter());
        Assert.assertEquals(0, removedSubscriptionsList.get(0).getQos());
        Assert.assertEquals("2", removedSubscriptionsList.get(1).getTopicFilter());
        Assert.assertEquals(2, removedSubscriptionsList.get(1).getQos());

        Assert.assertEquals(1, toSubscribe.size());
        List<TopicSubscription> addedSubscriptionsList = new ArrayList<>(toSubscribe);
        Assert.assertEquals("3", addedSubscriptionsList.get(0).getTopicFilter());
        Assert.assertEquals(1, addedSubscriptionsList.get(0).getQos());
    }

    @Test
    public void testGetSubscriptionsUpdate_6() {
        Set<TopicSubscription> prevSubscriptions = Set.of(new ClientTopicSubscription("1", 2), new ClientTopicSubscription("2", 0));
        Set<TopicSubscription> newSubscriptions = Set.of();
        TopicSubscriptionsUtil.SubscriptionsUpdate subscriptionsUpdate = TopicSubscriptionsUtil.getSubscriptionsUpdate(prevSubscriptions, newSubscriptions);

        Set<TopicSubscription> toSubscribe = subscriptionsUpdate.getToSubscribe();
        Set<TopicSubscription> toUnsubscribe = subscriptionsUpdate.getToUnsubscribe();

        Assert.assertEquals(2, toUnsubscribe.size());
        List<TopicSubscription> removedSubscriptionsList = new ArrayList<>(toUnsubscribe);
        Assert.assertEquals("1", removedSubscriptionsList.get(0).getTopicFilter());
        Assert.assertEquals(2, removedSubscriptionsList.get(0).getQos());
        Assert.assertEquals("2", removedSubscriptionsList.get(1).getTopicFilter());
        Assert.assertEquals(0, removedSubscriptionsList.get(1).getQos());

        Assert.assertEquals(0, toSubscribe.size());
    }

    @Test
    public void testGetSubscriptionsUpdate_7() {
        Set<TopicSubscription> prevSubscriptions = Set.of(
                new ClientTopicSubscription("1", 0, "s1"),
                new ClientTopicSubscription("2", 1, new SubscriptionOptions(false, true, SubscriptionOptions.RetainHandlingPolicy.DONT_SEND_AT_SUBSCRIBE)),
                new ClientTopicSubscription("3", 2, null, new SubscriptionOptions(), 5),
                new ClientTopicSubscription("4", 2, null, new SubscriptionOptions(), 2));

        Set<TopicSubscription> newSubscriptions = Set.of(
                new ClientTopicSubscription("1", 0, null, new SubscriptionOptions(), 1),
                new ClientTopicSubscription("2", 2),
                new ClientTopicSubscription("3", 1));

        TopicSubscriptionsUtil.SubscriptionsUpdate subscriptionUpdates = TopicSubscriptionsUtil.getSubscriptionsUpdate(prevSubscriptions, newSubscriptions);
        Set<TopicSubscription> toSubscribe = subscriptionUpdates.getToSubscribe();
        Set<TopicSubscription> toUnsubscribe = subscriptionUpdates.getToUnsubscribe();

        Assert.assertEquals(1, toUnsubscribe.size());
        List<TopicSubscription> removedSubscriptionsList = new ArrayList<>(toUnsubscribe);
        Assert.assertEquals("4", removedSubscriptionsList.get(0).getTopicFilter());

        Assert.assertEquals(3, toSubscribe.size());
        List<TopicSubscription> addedSubscriptionsList = new ArrayList<>(toSubscribe);

        Assert.assertEquals("1", addedSubscriptionsList.get(0).getTopicFilter());
        Assert.assertEquals(0, addedSubscriptionsList.get(0).getQos());
        Assert.assertNull(addedSubscriptionsList.get(0).getShareName());
        Assert.assertEquals(1, addedSubscriptionsList.get(0).getSubscriptionId());

        Assert.assertEquals("2", addedSubscriptionsList.get(1).getTopicFilter());
        Assert.assertEquals(2, addedSubscriptionsList.get(1).getQos());
        Assert.assertEquals(SubscriptionOptions.RetainHandlingPolicy.SEND_AT_SUBSCRIBE, addedSubscriptionsList.get(1).getOptions().getRetainHandling());

        Assert.assertEquals("3", addedSubscriptionsList.get(2).getTopicFilter());
        Assert.assertEquals(1, addedSubscriptionsList.get(2).getQos());
        Assert.assertEquals(-1, addedSubscriptionsList.get(1).getSubscriptionId());
    }

}
