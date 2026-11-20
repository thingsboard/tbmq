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
package org.thingsboard.mqtt.broker.lite;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.controller.AbstractControllerTest;
import org.thingsboard.mqtt.broker.dao.rocksdb.timeseries.ClientLatestStatsBuffer;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClientImpl;
import org.thingsboard.mqtt.broker.service.test.util.RestartingSpringJUnit4ClassRunner;
import org.thingsboard.mqtt.broker.service.test.util.SpringRestarter;
import org.thingsboard.mqtt.broker.testsupport.EditionTest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The latest stats of a client, which the standalone edition keeps in memory between flushes, are flushed when the
 * broker shuts down gracefully, so the session details read the same values after a restart.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = ClientStatsLiteTest.class, loader = SpringBootContextLoader.class)
@EditionTest
@RunWith(RestartingSpringJUnit4ClassRunner.class)
public class ClientStatsLiteTest extends AbstractControllerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int MESSAGES = 10;

    @Autowired
    private TbMessageStatsReportClientImpl statsReportClient;
    @Autowired
    private ClientLatestStatsBuffer clientLatestStatsBuffer;

    private MqttClient publisher;

    @After
    public void closeClient() {
        LiteTestClients.closeQuietly(publisher);
    }

    @Test
    public void aClientsStatsSurviveAGracefulRestart() throws Exception {
        String clientId = "client-stats-restart";
        publisher = new MqttClient("tcp://localhost:" + mqttPort, clientId, new MemoryPersistence());
        MqttConnectionOptions options = new MqttConnectionOptions();
        // a persistent session: the stats of a session that ends with its connection are removed with it
        options.setCleanStart(false);
        options.setSessionExpiryInterval(3600L);
        publisher.connect(options);
        for (int i = 0; i < MESSAGES; i++) {
            publisher.publish("client-stats/data", new MqttMessage(("m" + i).getBytes(StandardCharsets.UTF_8), 1, false, null));
        }
        // the scheduled report, without waiting for the next minute
        statsReportClient.process();

        loginSysAdmin();
        Map<String, Long> before = clientStats(clientId);
        assertThat(before).containsEntry(BrokerConstants.SENT_PUBLISH_MSGS, (long) MESSAGES)
                .containsEntry(BrokerConstants.QOS_1_SENT_PUBLISH_MSGS, (long) MESSAGES);
        // read from memory: the flush interval has not passed
        assertThat(clientLatestStatsBuffer.get(clientId, BrokerConstants.SENT_PUBLISH_MSGS)).isPresent();

        SpringRestarter.getInstance().restart();
        mockMvc = null;
        setupWebTest();
        loginSysAdmin();

        assertThat(clientLatestStatsBuffer.getAll(clientId)).isEmpty();
        assertThat(clientStats(clientId)).isEqualTo(before);
    }

    /**
     * The latest client stats as the session details of the UI read them.
     */
    private Map<String, Long> clientStats(String clientId) throws Exception {
        String keys = String.join(",", BrokerConstants.CLIENT_SESSION_METRIC_KEYS);
        Map<String, Long> stats = new HashMap<>();
        await().atMost(TIMEOUT).until(() -> {
            JsonNode latest = doGetAsync("/api/timeseries/latest?entityId={clientId}&keys={keys}", JsonNode.class, clientId, keys);
            stats.clear();
            latest.fields().forEachRemaining(field -> {
                JsonNode value = field.getValue().get(0).get("value");
                if (value != null && !value.isNull()) {
                    stats.put(field.getKey(), value.asLong());
                }
            });
            return stats.containsKey(BrokerConstants.SENT_PUBLISH_MSGS);
        });
        return stats;
    }

}
