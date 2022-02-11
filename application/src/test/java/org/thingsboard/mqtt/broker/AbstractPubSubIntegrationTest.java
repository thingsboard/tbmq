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
package org.thingsboard.mqtt.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.ClassRule;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.web.WebAppConfiguration;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaAdminSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaConsumerSettings;
import org.thingsboard.mqtt.broker.queue.kafka.settings.TbKafkaProducerSettings;

@ActiveProfiles("test")
@Import(AbstractPubSubIntegrationTest.KafkaTestContainersConfiguration.class)
@ComponentScan({"org.thingsboard.mqtt.broker"})
@WebAppConfiguration
@SpringBootTest(classes = ThingsboardMqttBrokerApplication.class)
public abstract class AbstractPubSubIntegrationTest {
    protected final ObjectMapper mapper = new ObjectMapper();

    @Value("${listener.tcp.bind_port}")
    protected int mqttPort;

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class TestPublishMsg {
        public int publisherId;
        public int sequenceId;
        public boolean isLast;
    }

    @ClassRule
    public static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"));

    public static class ReplaceKafkaPropertiesBeanPostProcessor implements BeanPostProcessor {
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof TbKafkaConsumerSettings) {
                TbKafkaConsumerSettings kafkaSettings = (TbKafkaConsumerSettings) bean;
                kafkaSettings.setServers(kafka.getBootstrapServers());
            }
            if (bean instanceof TbKafkaProducerSettings) {
                TbKafkaProducerSettings kafkaSettings = (TbKafkaProducerSettings) bean;
                kafkaSettings.setServers(kafka.getBootstrapServers());
            }
            if (bean instanceof TbKafkaAdminSettings) {
                TbKafkaAdminSettings kafkaAdminSettings = (TbKafkaAdminSettings) bean;
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
