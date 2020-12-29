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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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
import org.thingsboard.mqtt.broker.ThingsboardMqttBrokerApplication;
import org.thingsboard.mqtt.broker.queue.kafka.settings.PublishMsgKafkaSettings;

@Import(AbstractPubSubIntegrationTest.KafkaTestContainersConfiguration.class)
@ComponentScan({"org.thingsboard.mqtt.broker"})
@RunWith(SpringRunner.class)
@WebAppConfiguration
@SpringBootTest(classes = ThingsboardMqttBrokerApplication.class)
public abstract class AbstractPubSubIntegrationTest {
    @ClassRule
    public static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"));

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    static class TestPublishMsg {
        int publisherId;
        int sequenceId;
        boolean isLast;
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
