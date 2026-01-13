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
package org.thingsboard.mqtt.broker.dto;

import org.junit.Test;
import org.thingsboard.mqtt.broker.common.data.MqttQoS;
import org.thingsboard.mqtt.broker.common.data.dto.SubscriptionOptionsDto;
import org.thingsboard.mqtt.broker.common.data.subscription.ClientTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SubscriptionInfoDtoTest {

    @Test
    public void fromSubscription_ShouldCreateSubscriptionInfoDto() {
        SubscriptionOptions options = SubscriptionOptions.newInstance();
        SubscriptionOptionsDto optionsDto = SubscriptionOptionsDto.fromSubscriptionOptions(options);
        TopicSubscription topicSubscription = new ClientTopicSubscription("test/topic", 1, options);

        SubscriptionInfoDto dto = SubscriptionInfoDto.fromTopicSubscription(topicSubscription);

        assertNotNull(dto);
        assertFalse(dto.isSharedSubscription());
        assertEquals("test/topic", dto.getTopicFilter());
        assertEquals(MqttQoS.AT_LEAST_ONCE, dto.getQos());
        assertNull(dto.getSubscriptionId());
        assertEquals(optionsDto, dto.getOptions());
    }

    @Test
    public void getTopicFilter_ShouldReturnCorrectFilterForSharedSubscription() {
        SubscriptionOptions options = SubscriptionOptions.newInstance();
        TopicSubscription topicSubscription = new ClientTopicSubscription("test/topic", 1, "shareName", options, 1);

        String topicFilter = SubscriptionInfoDto.getTopicFilter(topicSubscription);
        SubscriptionInfoDto dto = SubscriptionInfoDto.fromTopicSubscription(topicSubscription);
        assertTrue(dto.isSharedSubscription());
        assertEquals(1, dto.getSubscriptionId().intValue());

        assertEquals("$share/shareName/test/topic", topicFilter);
    }

    @Test
    public void getTopicFilter_ShouldReturnCorrectFilterForNonSharedSubscription() {
        SubscriptionOptions options = SubscriptionOptions.newInstance();
        TopicSubscription topicSubscription = new ClientTopicSubscription("test/topic", 1, options);

        String topicFilter = SubscriptionInfoDto.getTopicFilter(topicSubscription);

        assertEquals("test/topic", topicFilter);
    }

    @Test
    public void toTopicSubscription_ShouldConvertSubscriptionInfoDtoToTopicSubscription_ForNonSharedSubscription() {
        SubscriptionOptionsDto options = SubscriptionOptionsDto.newInstance();
        SubscriptionOptions subscriptionOptions = SubscriptionOptions.fromSubscriptionOptionsDto(options);
        SubscriptionInfoDto dto = SubscriptionInfoDto.builder()
                .topicFilter("test/topic")
                .qos(MqttQoS.AT_LEAST_ONCE)
                .options(options)
                .subscriptionId(null)
                .build();

        TopicSubscription topicSubscription = SubscriptionInfoDto.toTopicSubscription(dto);

        assertNotNull(topicSubscription);
        assertEquals("test/topic", topicSubscription.getTopicFilter());
        assertEquals(1, topicSubscription.getQos());
        assertNull(topicSubscription.getShareName());
        assertEquals(subscriptionOptions, topicSubscription.getOptions());
        assertEquals(-1, topicSubscription.getSubscriptionId());
    }

    @Test
    public void toTopicSubscription_ShouldConvertSubscriptionInfoDtoToTopicSubscription_ForSharedSubscription() {
        SubscriptionOptionsDto options = SubscriptionOptionsDto.newInstance();
        SubscriptionOptions subscriptionOptions = SubscriptionOptions.fromSubscriptionOptionsDto(options);
        SubscriptionInfoDto dto = SubscriptionInfoDto.builder()
                .topicFilter("$share/shareName/test/topic")
                .qos(MqttQoS.EXACTLY_ONCE)
                .options(options)
                .subscriptionId(1)
                .build();

        TopicSubscription topicSubscription = SubscriptionInfoDto.toTopicSubscription(dto);

        assertNotNull(topicSubscription);
        assertEquals("test/topic", topicSubscription.getTopicFilter());
        assertEquals(2, topicSubscription.getQos());
        assertEquals("shareName", topicSubscription.getShareName());
        assertEquals(subscriptionOptions, topicSubscription.getOptions());
        assertEquals(1, topicSubscription.getSubscriptionId());
    }
}
