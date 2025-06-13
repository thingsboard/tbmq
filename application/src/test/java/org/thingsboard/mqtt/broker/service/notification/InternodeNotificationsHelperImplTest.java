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
package org.thingsboard.mqtt.broker.service.notification;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.queue.kafka.settings.InternodeNotificationsKafkaSettings;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InternodeNotificationsHelperImplTest {

    @Mock
    private InternodeNotificationsKafkaSettings kafkaSettings;

    @Mock
    private TbQueueAdmin queueAdmin;

    private InternodeNotificationsHelperImpl helper;

    @Before
    public void setUp() {
        String kafkaPrefix = "custom-prefix.";
        when(kafkaSettings.getTopicPrefix()).thenReturn("internode");

        helper = new InternodeNotificationsHelperImpl(kafkaSettings, queueAdmin);

        ReflectionTestUtils.setField(helper, "kafkaPrefix", kafkaPrefix);

        helper.init();
    }

    @Test
    public void testGetServiceTopic() {
        String serviceId = "nodeA";
        String expectedTopic = "custom-prefix.internode.nodeA";

        String topic = helper.getServiceTopic(serviceId);

        assertThat(topic).isNotNull();
        assertThat(topic).isEqualTo(expectedTopic);
    }

    @Test
    public void testGetServiceIds() {
        List<String> mockIds = List.of("nodeA", "nodeB");
        when(queueAdmin.getBrokerServiceIds()).thenReturn(mockIds);

        assertThat(helper.getServiceIds()).containsExactlyInAnyOrder("nodeA", "nodeB");
    }
}
