/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.adaptor;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.constant.BrokerConstants;

@RunWith(MockitoJUnitRunner.class)
public class NettyMqttConverterTest {

    static final String SHARED_SUBSCRIBER_GROUP_TOPIC_NAME_SUFFIX = "shared-subscriber-group/main/+/temp";
    static final String SHARED_SUBSCRIBER_GROUP_TOPIC_NAME = BrokerConstants.SHARED_SUBSCRIPTION_PREFIX + SHARED_SUBSCRIBER_GROUP_TOPIC_NAME_SUFFIX;

    @Test
    public void testGetShareNameFromSharedSubscription() {
        String shareName = NettyMqttConverter.getShareName(SHARED_SUBSCRIBER_GROUP_TOPIC_NAME);
        Assert.assertEquals("shared-subscriber-group", shareName);
    }

    @Test
    public void testGetShareName() {
        String shareName = NettyMqttConverter.getShareName(SHARED_SUBSCRIBER_GROUP_TOPIC_NAME_SUFFIX);
        Assert.assertNull(shareName);
    }

    @Test
    public void testGetShareName1() {
        String shareName = NettyMqttConverter.getShareName(BrokerConstants.SHARED_SUBSCRIPTION_PREFIX + "/topic");
        Assert.assertNotNull(shareName);
        Assert.assertTrue(shareName.isEmpty());
    }

    @Test(expected = RuntimeException.class)
    public void testGetShareName2() {
        NettyMqttConverter.getShareName(BrokerConstants.SHARED_SUBSCRIPTION_PREFIX + "topic");
    }

    @Test
    public void testGetTopicNameFromSharedSubscription() {
        String topicName = NettyMqttConverter.getTopicName(SHARED_SUBSCRIBER_GROUP_TOPIC_NAME);
        Assert.assertEquals("main/+/temp", topicName);
    }

    @Test
    public void testGetTopicName() {
        String topicName = NettyMqttConverter.getTopicName(SHARED_SUBSCRIBER_GROUP_TOPIC_NAME_SUFFIX);
        Assert.assertEquals(SHARED_SUBSCRIBER_GROUP_TOPIC_NAME_SUFFIX, topicName);
    }
}