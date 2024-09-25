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
package org.thingsboard.mqtt.broker.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.mqtt.broker.adaptor.NettyMqttConverter;
import org.thingsboard.mqtt.broker.common.data.MqttQoS;
import org.thingsboard.mqtt.broker.common.data.dto.SubscriptionOptionsDto;
import org.thingsboard.mqtt.broker.common.data.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionInfoDto {

    private String topicFilter;
    private MqttQoS qos;
    private SubscriptionOptionsDto options;
    private Integer subscriptionId;

    public static SubscriptionInfoDto fromTopicSubscription(TopicSubscription topicSubscription) {
        return new SubscriptionInfoDto(
                getTopicFilter(topicSubscription),
                MqttQoS.valueOf(topicSubscription.getQos()),
                SubscriptionOptionsDto.fromSubscriptionOptions(topicSubscription.getOptions()),
                getSubscriptionId(topicSubscription)
        );
    }

    public static TopicSubscription toTopicSubscription(SubscriptionInfoDto subscriptionInfoDto) {
        return new TopicSubscription(
                NettyMqttConverter.getTopicFilter(subscriptionInfoDto.getTopicFilter()),
                subscriptionInfoDto.getQos().value(),
                NettyMqttConverter.getShareName(subscriptionInfoDto.getTopicFilter()),
                SubscriptionOptions.fromSubscriptionOptionsDto(subscriptionInfoDto.getOptions()),
                getSubscriptionId(subscriptionInfoDto)
        );
    }

    static String getTopicFilter(TopicSubscription topicSubscription) {
        return topicSubscription.isSharedSubscription() ? constructSharedSubscriptionTopicFilter(topicSubscription) : topicSubscription.getTopicFilter();
    }

    private static String constructSharedSubscriptionTopicFilter(TopicSubscription topicSubscription) {
        return BrokerConstants.SHARED_SUBSCRIPTION_PREFIX + topicSubscription.getShareName() +
                BrokerConstants.TOPIC_DELIMITER_STR + topicSubscription.getTopicFilter();
    }

    private static Integer getSubscriptionId(TopicSubscription topicSubscription) {
        return topicSubscription.getSubscriptionId() == -1 ? null : topicSubscription.getSubscriptionId();
    }

    private static int getSubscriptionId(SubscriptionInfoDto subscriptionInfoDto) {
        return subscriptionInfoDto.getSubscriptionId() == null ? -1 : subscriptionInfoDto.getSubscriptionId();
    }

    @JsonIgnore
    public boolean isCommonSubscription() {
        return !isSharedSubscription();
    }

    @JsonIgnore
    public boolean isSharedSubscription() {
        return NettyMqttConverter.isSharedTopic(topicFilter);
    }
}
