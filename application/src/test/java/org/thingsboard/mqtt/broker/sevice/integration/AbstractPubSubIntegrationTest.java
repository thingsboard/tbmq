package org.thingsboard.mqtt.broker.sevice.integration;

import net.jodah.concurrentunit.Waiter;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.thingsboard.mqtt.broker.ThingsboardMQTTBrokerApplication;
import org.thingsboard.mqtt.broker.queue.kafka.settings.PublishMsgKafkaSettings;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Import(AbstractPubSubIntegrationTest.KafkaTestContainersConfiguration.class)
@ComponentScan({"org.thingsboard.mqtt.broker"})
@RunWith(SpringRunner.class)
@WebAppConfiguration
@SpringBootTest(classes = ThingsboardMQTTBrokerApplication.class)
public abstract class AbstractPubSubIntegrationTest {
    private static final String SINGLE_LEVELS_TOPIC = "use-case/+/city/store/department/group/+";
    private static final String MULTI_LEVEL_TOPIC = "use-case/country/city/#";

    static final int SUBSCRIBERS_COUNT = 10;
    static final int PUBLISHERS_COUNT = 5;
    static final int PUBLISH_MSGS_COUNT = 100;


    @ClassRule
    public static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"));

    void initPubSubTest() throws Throwable {
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

    abstract void initSubscriber(Waiter waiter, int subscriberId);

    abstract void initPublishers(Waiter waiter, int publisherId);


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
