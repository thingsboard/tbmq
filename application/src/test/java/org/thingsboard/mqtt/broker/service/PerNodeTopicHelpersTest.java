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
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.service.historical.stats.HistoricalStatsTotalHelperImpl;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectClientCommandHelperImpl;
import org.thingsboard.mqtt.broker.service.notification.InternodeNotificationsHelperImpl;
import org.thingsboard.mqtt.broker.service.processing.downlink.DownLinkPublisherHelperImpl;
import org.thingsboard.mqtt.broker.service.system.SystemInfoService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PerNodeTopicHelpersTest {

    @Test
    void topicsWithoutKafkaPrefix() {
        assertTopics("");
    }

    @Test
    void topicsWithKafkaPrefix() {
        assertTopics("acme.");
    }

    private void assertTopics(String prefix) {
        DisconnectClientCommandHelperImpl disconnectHelper = new DisconnectClientCommandHelperImpl();
        ReflectionTestUtils.setField(disconnectHelper, "kafkaPrefix", prefix);
        ReflectionTestUtils.setField(disconnectHelper, "disconnectClientCommandTopicPrefix", "tbmq.client.disconnect");
        disconnectHelper.init();

        DownLinkPublisherHelperImpl downLinkHelper = new DownLinkPublisherHelperImpl();
        ReflectionTestUtils.setField(downLinkHelper, "kafkaPrefix", prefix);
        ReflectionTestUtils.setField(downLinkHelper, "basicDownLinkTopicPrefixProperty", "tbmq.msg.downlink.basic");
        ReflectionTestUtils.setField(downLinkHelper, "persistentDownLinkTopicPrefixProperty", "tbmq.msg.downlink.persisted");
        downLinkHelper.init();

        InternodeNotificationsHelperImpl internodeHelper = new InternodeNotificationsHelperImpl(mock(SystemInfoService.class));
        ReflectionTestUtils.setField(internodeHelper, "kafkaPrefix", prefix);
        ReflectionTestUtils.setField(internodeHelper, "internodeNotificationsTopicPrefix", "tbmq.sys.internode.notifications");
        internodeHelper.init();

        HistoricalStatsTotalHelperImpl historicalHelper = new HistoricalStatsTotalHelperImpl();
        ReflectionTestUtils.setField(historicalHelper, "kafkaPrefix", prefix);
        ReflectionTestUtils.setField(historicalHelper, "historicalDataTotalTopic", "tbmq.sys.historical.data");

        assertThat(disconnectHelper.getServiceTopic("node-1")).isEqualTo(prefix + "tbmq.client.disconnect.node-1");
        assertThat(downLinkHelper.getBasicDownLinkServiceTopic("node-1")).isEqualTo(prefix + "tbmq.msg.downlink.basic.node-1");
        assertThat(downLinkHelper.getPersistentDownLinkServiceTopic("node-1")).isEqualTo(prefix + "tbmq.msg.downlink.persisted.node-1");
        assertThat(internodeHelper.getServiceTopic("node-1")).isEqualTo(prefix + "tbmq.sys.internode.notifications.node-1");
        assertThat(historicalHelper.getTopic()).isEqualTo(prefix + "tbmq.sys.historical.data");
    }
}
