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
package org.thingsboard.mqtt.broker.adaptor;

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.constant.BrokerConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NettyMqttConverterTest {

    static final String SHARED_SUBSCRIBER_GROUP_TOPIC_NAME_SUFFIX = "shared-subscriber-group/main/+/temp";
    static final String SHARED_SUBSCRIBER_GROUP_TOPIC_NAME = BrokerConstants.SHARED_SUBSCRIPTION_PREFIX + SHARED_SUBSCRIBER_GROUP_TOPIC_NAME_SUFFIX;

    @Test
    void testGetShareNameFromSharedSubscription() {
        String shareName = NettyMqttConverter.getShareName(SHARED_SUBSCRIBER_GROUP_TOPIC_NAME);
        assertEquals("shared-subscriber-group", shareName);
    }

    @Test
    void testGetShareName() {
        String shareName = NettyMqttConverter.getShareName(SHARED_SUBSCRIBER_GROUP_TOPIC_NAME_SUFFIX);
        assertNull(shareName);
    }

    @Test
    void testGetShareName1() {
        String shareName = NettyMqttConverter.getShareName(BrokerConstants.SHARED_SUBSCRIPTION_PREFIX + "/topic");
        assertNotNull(shareName);
        assertTrue(shareName.isEmpty());
    }

    @Test
    void testGetShareName2() {
        assertThrows(RuntimeException.class, () -> NettyMqttConverter.getShareName(BrokerConstants.SHARED_SUBSCRIPTION_PREFIX + "topic"));
    }

    @Test
    void testGetTopicNameFromSharedSubscription() {
        String topicName = NettyMqttConverter.getTopicName(SHARED_SUBSCRIBER_GROUP_TOPIC_NAME);
        assertEquals("main/+/temp", topicName);
    }

    @Test
    void testGetTopicName() {
        String topicName = NettyMqttConverter.getTopicName(SHARED_SUBSCRIBER_GROUP_TOPIC_NAME_SUFFIX);
        assertEquals(SHARED_SUBSCRIBER_GROUP_TOPIC_NAME_SUFFIX, topicName);
    }
}