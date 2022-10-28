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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@RunWith(MockitoJUnitRunner.class)
public class ProtoConverterTest {

    @Test
    public void givenTopicSubscriptions_whenConvertToProtoAndBack_thenOk() {
        Set<TopicSubscription> input = Set.of(
                new TopicSubscription("topic1", 0, "name1"),
                new TopicSubscription("topic2", 1)
        );

        QueueProtos.ClientSubscriptionsProto clientSubscriptionsProto = ProtoConverter.convertToClientSubscriptionsProto(input);

        QueueProtos.TopicSubscriptionProto topicSubscriptionProto = clientSubscriptionsProto.getSubscriptionsList()
                .stream()
                .filter(tsp -> tsp.getShareName().isEmpty())
                .findFirst()
                .orElse(null);
        assertNotNull(topicSubscriptionProto);

        Set<TopicSubscription> output = ProtoConverter.convertToClientSubscriptions(clientSubscriptionsProto);

        TopicSubscription topicSubscription = output
                .stream()
                .filter(ts -> ts.getShareName() == null)
                .findFirst()
                .orElse(null);
        assertNotNull(topicSubscription);

        assertEquals(input, output);
    }
}