package org.thingsboard.mqtt.broker.sevice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.concurrentunit.Waiter;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@DirtiesContext
@ContextConfiguration(classes = SimpleTopicPubSubIntegrationTest.class, loader = SpringBootContextLoader.class)
public class SimpleTopicPubSubIntegrationTest extends AbstractPubSubIntegrationTest {
    private static final String BASIC_TOPIC = "use-case/country/city/store/department/group/device";

    @Value("${server.mqtt.bind_address}")
    private String mqttAddress;
    @Value("${server.mqtt.bind_port}")
    private int mqttPort;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testPubSubSimpleTopic() throws Throwable {
        initPubSubTest();
    }

    @Override
    void initPublishers(Waiter waiter, int publisherId) {
        try {
            MqttClient pubClient = new MqttClient("tcp://" + mqttAddress + ":" + mqttPort, "test_pub_client_" + publisherId);
            pubClient.connect();
            for (int j = 0; j < PUBLISH_MSGS_COUNT; j++) {
                MqttMessage msg = new MqttMessage();
                TestPublishMsg payload = new TestPublishMsg(publisherId, j, j == PUBLISH_MSGS_COUNT - 1);
                msg.setPayload(mapper.writeValueAsBytes(payload));
                msg.setQos(j % 2);
                pubClient.publish(BASIC_TOPIC, msg);
            }
            log.info("[{}] Publisher stopped publishing", publisherId);
        } catch (Exception e) {
            log.error("[{}] Failed to publish", publisherId, e);
            e.printStackTrace();
            waiter.assertNull(e);
        }
    }

    @Override
    void initSubscriber(Waiter waiter, int subscriberId) {
        try {
            MqttClient subClient = new MqttClient("tcp://" + mqttAddress + ":" + mqttPort, "test_sub_client_" + subscriberId);
            subClient.connect();
            Map<Integer, TestPublishMsg> previousMsgs = new HashMap<>();
            AtomicInteger unfinishedProducers = new AtomicInteger(PUBLISHERS_COUNT);
            int qos = subscriberId % 2;
            subClient.subscribe(BASIC_TOPIC, qos, (topic, message) -> {
                TestPublishMsg currentMsg = mapper.readValue(message.getPayload(), TestPublishMsg.class);
                TestPublishMsg previousMsg = previousMsgs.get(currentMsg.publisherId);
                if (previousMsg != null) {
                    waiter.assertFalse(previousMsg.isLast);
                    waiter.assertEquals(previousMsg.sequenceId + 1, currentMsg.sequenceId);
                }
                if (currentMsg.isLast && unfinishedProducers.decrementAndGet() == 0) {
                    log.info("[{}] Successfully processed all messages", subscriberId);
                    waiter.resume();
                }
                previousMsgs.put(currentMsg.publisherId, currentMsg);
            });
        } catch (Exception e) {
            log.error("[{}] Failed to process published messages", subscriberId, e);
            waiter.assertNull(e);
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    private static class TestPublishMsg {
        private int publisherId;
        private int sequenceId;
        private boolean isLast;
    }
}
