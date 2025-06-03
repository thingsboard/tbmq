/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.rules.ExternalResource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.web.WebAppConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.utility.DockerImageName;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaAdminSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaConsumerSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaProducerSettings;

import java.util.List;

@Slf4j
@ActiveProfiles("test")
@Import(AbstractIntegrationTest.KafkaTestContainersConfiguration.class)
@ComponentScan({"org.thingsboard.mqtt.broker"})
@WebAppConfiguration
@SpringBootTest(classes = ThingsboardMqttBrokerApplication.class)
public abstract class AbstractIntegrationTest {

    @BeforeClass
    public static void setup() throws Exception {
        System.setProperty("tbmq.graceful.shutdown.timeout.sec", "1");
    }

    @Autowired
    protected MqttClientCredentialsService mqttClientCredentialsService;

    @ClassRule
    public static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @ClassRule
    public static GenericContainer redis = new GenericContainer("redis:7.2.5")
            .withExposedPorts(6379)
            .withLogConsumer(x -> log.warn("{}", ((OutputFrame) x).getUtf8StringWithoutLineEnding()))
            .withEnv("REDIS_PASSWORD", "password")
            .withCommand("redis-server", "--requirepass", "password");

    @ClassRule
    public static ExternalResource resource = new ExternalResource() {
        @Override
        protected void before() throws Throwable {
            redis.start();
            System.setProperty("redis.connection.type", "standalone");
            System.setProperty("redis.standalone.host", redis.getHost());
            System.setProperty("redis.standalone.port", String.valueOf(redis.getMappedPort(6379)));
            System.setProperty("redis.password", "password");
        }

        @Override
        protected void after() {
            redis.stop();
            List.of("redis.connection.type", "redis.standalone.host", "redis.standalone.port")
                    .forEach(System.getProperties()::remove);
        }
    };

    public static class ReplaceKafkaPropertiesBeanPostProcessor implements BeanPostProcessor {
        @Override
        public Object postProcessAfterInitialization(@NotNull Object bean, @NotNull String beanName) throws BeansException {
            if (bean instanceof TbKafkaConsumerSettings kafkaSettings) {
                kafkaSettings.setServers(kafka.getBootstrapServers());
            }
            if (bean instanceof TbKafkaProducerSettings kafkaSettings) {
                kafkaSettings.setServers(kafka.getBootstrapServers());
            }
            if (bean instanceof TbKafkaAdminSettings kafkaAdminSettings) {
                kafkaAdminSettings.setServers(kafka.getBootstrapServers());
            }
            return bean;
        }
    }

    @TestConfiguration
    static class KafkaTestContainersConfiguration {
        @Bean
        ReplaceKafkaPropertiesBeanPostProcessor beanPostProcessor() {
            return new ReplaceKafkaPropertiesBeanPostProcessor();
        }
    }

}
