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
package org.thingsboard.mqtt.broker.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.jodah.concurrentunit.Waiter;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = TopicsIntegrationTest.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
public class TopicsIntegrationTest extends AbstractPubSubIntegrationTest {

    private static final String BASIC_TOPIC = "use-case/country/city/store/department/group/device";

    private static final String MULTI_LEVEL_TOPIC_FILTER = "use-case/country/city/#";
    private static final List<String> MULTI_LEVEL_TOPICS = Arrays.asList(
            "use-case/country/city/store",
            "use-case/country/city/store/department/group",
            "use-case/country/city/store/department/1",
            "use-case/country/city/store/group/2",
            "use-case/country/city/test/1"
    );

    private static final String SINGLE_LEVELS_TOPIC_FILTER = "use-case/+/city/store/department/group/+";
    private static final List<String> SINGLE_LEVELS_TOPICS = Arrays.asList(
            "use-case/ua/city/store/department/group/1",
            "use-case/ua/city/store/department/group/2",
            "use-case/uk/city/store/department/group/1",
            "use-case/bg/city/store/department/group/2",
            "use-case/be/city/store/department/group/3"
    );

    @Value("${server.mqtt.bind_address}")
    private String mqttAddress;
    @Value("${server.mqtt.bind_port}")
    private int mqttPort;

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private IntegrationTestInitService initPubSubTestService;

    @Test
    public void testPubSubBasicTopic() throws Throwable {
        initPubSubTestService.initPubSubTest(
                (waiter, subscriberId) -> initSubscriber(waiter, subscriberId, BASIC_TOPIC),
                (waiter, publisherId) -> initPublisher(waiter, publisherId, () -> BASIC_TOPIC));
    }

    @Test
    public void testPubSubSingleLevelsTopic() throws Throwable {
        initPubSubTestService.initPubSubTest(
                (waiter, subscriberId) -> initSubscriber(waiter, subscriberId, SINGLE_LEVELS_TOPIC_FILTER),
                (waiter, publisherId) -> initPublisher(waiter, publisherId, () -> SINGLE_LEVELS_TOPICS.get(publisherId % SINGLE_LEVELS_TOPICS.size())));
    }

    @Test
    public void testPubSubMultipleLevelsTopic() throws Throwable {
        initPubSubTestService.initPubSubTest(
                (waiter, subscriberId) -> initSubscriber(waiter, subscriberId, MULTI_LEVEL_TOPIC_FILTER),
                (waiter, publisherId) -> initPublisher(waiter, publisherId, () -> MULTI_LEVEL_TOPICS.get(publisherId % MULTI_LEVEL_TOPICS.size())));
    }

    void initPublisher(Waiter waiter, int publisherId, Supplier<String> topicSupplier) {
        try {
            MqttClient pubClient = new MqttClient("tcp://" + mqttAddress + ":" + mqttPort, "topic_pub_client_"
                    + publisherId + "_" + UUID.randomUUID().toString());
            pubClient.connect();
            for (int j = 0; j < IntegrationTestInitService.PUBLISH_MSGS_COUNT; j++) {
                MqttMessage msg = new MqttMessage();
                TestPublishMsg payload = new TestPublishMsg(publisherId, j, j == IntegrationTestInitService.PUBLISH_MSGS_COUNT - 1);
                msg.setPayload(mapper.writeValueAsBytes(payload));
                msg.setQos(j % 2);
                pubClient.publish(topicSupplier.get(), msg);
            }
            log.info("[{}] Publisher stopped publishing", publisherId);
        } catch (Exception e) {
            log.error("[{}] Failed to publish", publisherId, e);
            e.printStackTrace();
            waiter.assertNull(e);
        }
    }

    void initSubscriber(Waiter waiter, int subscriberId, String topicFilter) {
        try {
            MqttClient subClient = new MqttClient("tcp://" + mqttAddress + ":" + mqttPort, "topic_sub_client_"
                    + subscriberId + "_" + UUID.randomUUID().toString());
            subClient.connect();
            Map<Integer, TestPublishMsg> previousMsgs = new HashMap<>();
            AtomicInteger unfinishedProducers = new AtomicInteger(IntegrationTestInitService.PUBLISHERS_COUNT);
            int qos = subscriberId % 2;
            subClient.subscribe(topicFilter, qos, (topic, message) -> {
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
}
