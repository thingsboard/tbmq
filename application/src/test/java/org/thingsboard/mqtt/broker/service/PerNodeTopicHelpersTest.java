/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.thingsboard.mqtt.broker.service.historical.stats.HistoricalStatsTotalHelperImpl;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandHelperImpl;
import org.thingsboard.mqtt.broker.service.notification.InternodeNotificationsHelperImpl;
import org.thingsboard.mqtt.broker.service.processing.downlink.DownLinkPublisherHelperImpl;
import org.thingsboard.mqtt.broker.service.system.SystemInfoService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Resolves the helpers' {@code @Value} keys against the shipped {@code thingsboard-mqtt-broker.yml}, so each
 * assertion pins a property key to the topic name on the wire. Integration tests cannot catch two swapped keys,
 * because producers and consumers take the topic from the same helper.
 * <p>
 * The environment holds only that file and the test's own overrides: JVM system properties and OS variables
 * (such as {@code TB_KAFKA_PREFIX}) on the build machine must not change the expected names.
 */
class PerNodeTopicHelpersTest {

    private static final String BROKER_CONFIG = "thingsboard-mqtt-broker.yml";
    private static final String SERVICE_ID = "node-1";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> {
                MutablePropertySources sources = context.getEnvironment().getPropertySources();
                sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                loadBrokerConfig().forEach(sources::addLast);
            })
            .withBean(SystemInfoService.class, () -> mock(SystemInfoService.class))
            .withBean(DisconnectClientCommandHelperImpl.class)
            .withBean(DownLinkPublisherHelperImpl.class)
            .withBean(InternodeNotificationsHelperImpl.class)
            .withBean(HistoricalStatsTotalHelperImpl.class);

    @Test
    void defaultConfigGivesTheDefaultTopicNames() {
        contextRunner.run(context -> {
            assertThat(context.getBean(DisconnectClientCommandHelperImpl.class).getServiceTopic(SERVICE_ID))
                    .isEqualTo("tbmq.client.disconnect.node-1");
            assertThat(context.getBean(DownLinkPublisherHelperImpl.class).getBasicDownLinkServiceTopic(SERVICE_ID))
                    .isEqualTo("tbmq.msg.downlink.basic.node-1");
            assertThat(context.getBean(DownLinkPublisherHelperImpl.class).getPersistentDownLinkServiceTopic(SERVICE_ID))
                    .isEqualTo("tbmq.msg.downlink.persisted.node-1");
            assertThat(context.getBean(InternodeNotificationsHelperImpl.class).getServiceTopic(SERVICE_ID))
                    .isEqualTo("tbmq.sys.internode.notifications.node-1");
            assertThat(context.getBean(HistoricalStatsTotalHelperImpl.class).getTopic())
                    .isEqualTo("tbmq.sys.historical.data");
        });
    }

    @Test
    void kafkaPrefixIsPrependedToEveryTopicName() {
        contextRunner.withPropertyValues("queue.kafka.kafka-prefix=acme.").run(context -> {
            assertThat(context.getBean(DisconnectClientCommandHelperImpl.class).getServiceTopic(SERVICE_ID))
                    .isEqualTo("acme.tbmq.client.disconnect.node-1");
            assertThat(context.getBean(DownLinkPublisherHelperImpl.class).getBasicDownLinkServiceTopic(SERVICE_ID))
                    .isEqualTo("acme.tbmq.msg.downlink.basic.node-1");
            assertThat(context.getBean(DownLinkPublisherHelperImpl.class).getPersistentDownLinkServiceTopic(SERVICE_ID))
                    .isEqualTo("acme.tbmq.msg.downlink.persisted.node-1");
            assertThat(context.getBean(InternodeNotificationsHelperImpl.class).getServiceTopic(SERVICE_ID))
                    .isEqualTo("acme.tbmq.sys.internode.notifications.node-1");
            assertThat(context.getBean(HistoricalStatsTotalHelperImpl.class).getTopic())
                    .isEqualTo("acme.tbmq.sys.historical.data");
        });
    }

    private static List<PropertySource<?>> loadBrokerConfig() {
        try {
            return new YamlPropertySourceLoader().load(BROKER_CONFIG, new ClassPathResource(BROKER_CONFIG));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
