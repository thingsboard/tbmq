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
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.thingsboard.mqtt.broker.ThingsboardMQTTBrokerApplication;
import org.thingsboard.mqtt.broker.queue.kafka.settings.PublishMsgKafkaSettings;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ThingsboardMQTTBrokerApplication.class)
@Import(org.thingsboard.mqtt.broker.sevice.integration.PublishSubscribeIntegrationTest.KafkaTestContainersConfiguration.class)
@ContextConfiguration(classes = PublishSubscribeIntegrationTest.class, loader = SpringBootContextLoader.class)
@DirtiesContext
@ComponentScan({"org.thingsboard.mqtt.broker"})
@WebAppConfiguration
@Slf4j
public class PublishSubscribeIntegrationTest {
    private static final String BASIC_TOPIC = "use-case/country/city/store/department/group/device";
    private static final String SINGLE_LEVELS_TOPIC = "use-case/+/city/store/department/group/+";
    private static final String MULTI_LEVEL_TOPIC = "use-case/country/city/#";

    private static final int SUBSCRIBERS_COUNT = 10;
    private static final int PUBLISHERS_COUNT = 5;
    private static final int PUBLISH_MSGS_COUNT = 100;

    private final ObjectMapper mapper = new ObjectMapper();

    @ClassRule
    public static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"));

    @Value("${server.mqtt.bind_address}")
    private String mqttAddress;
    @Value("${server.mqtt.bind_port}")
    private int mqttPort;

    @Test
    public void pubSubSingleSimpleTopic() throws Throwable {
        Waiter subscribersWaiter = new Waiter();
        CountDownLatch connectingSubscribers = new CountDownLatch(SUBSCRIBERS_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(PUBLISHERS_COUNT);
        for (int i = 0; i < SUBSCRIBERS_COUNT; i++) {
            int finalI = i;
            executor.execute(() -> {
                initSubscriber(subscribersWaiter, finalI);
                connectingSubscribers.countDown();
            });
        }

        connectingSubscribers.await(2, TimeUnit.SECONDS);

        CountDownLatch processingPublishers = new CountDownLatch(PUBLISHERS_COUNT);
        for (int i = 0; i < PUBLISHERS_COUNT; i++) {
            int finalI = i;
            executor.execute(() -> {
                initPublishers(subscribersWaiter, finalI);
                processingPublishers.countDown();
            });
        }

        processingPublishers.await(2, TimeUnit.SECONDS);

        subscribersWaiter.await(1, TimeUnit.SECONDS, SUBSCRIBERS_COUNT);
    }

    private void initPublishers(Waiter waiter, int publisherId) {
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

    private void initSubscriber(Waiter waiter, int subscriberId) {
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


    @TestConfiguration
    static class KafkaTestContainersConfiguration {
        @Bean
        ReplaceKafkaPropertiesBeanPostProcessor beanPostProcessor() {
            return new ReplaceKafkaPropertiesBeanPostProcessor();
        }
    }

    static class ReplaceKafkaPropertiesBeanPostProcessor implements BeanPostProcessor {
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof PublishMsgKafkaSettings) {
                PublishMsgKafkaSettings kafkaSettings = (PublishMsgKafkaSettings) bean;
                kafkaSettings.setServers(kafka.getBootstrapServers());
            }
            return bean;
        }
    }

}
