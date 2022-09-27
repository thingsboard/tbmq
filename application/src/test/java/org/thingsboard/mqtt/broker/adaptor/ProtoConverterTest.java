package org.thingsboard.mqtt.broker.adaptor;

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProtoConverterTest {

    @Test
    void givenTopicSubscriptions_whenConvertToProtoAndBack_thenOk() {
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