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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.ThingsboardMqttBrokerApplication;
import org.thingsboard.mqtt.broker.testsupport.EditionTest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The dispatch stage reports the publishes it hands to the dispatcher as {@code incomingPublishMsg.consumed}, one
 * series per dispatch partition, like the consumer of the cluster edition.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ThingsboardMqttBrokerApplication.class)
@ActiveProfiles("test")
@EditionTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class DispatchStatsLiteTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String CONSUMED = "incomingPublishMsg.consumed";
    private static final int PUBLISHES = 3;

    @ClassRule
    public static TempStorePathRule store = new TempStorePathRule();

    @Autowired
    private MeterRegistry meterRegistry;

    @Value("${listener.tcp.bind_port}")
    private int mqttPort;

    private final List<Object> clients = new ArrayList<>();

    @After
    public void closeClients() {
        clients.forEach(LiteTestClients::closeQuietly);
        clients.clear();
    }

    @Test
    public void publishesAreCountedPerDispatchPartition() throws Exception {
        double totalBefore = sum("totalMsgs");
        double successBefore = sum("successfulMsgs");
        double failedBefore = sum("failedMsgs");
        long timedBefore = timerCount();

        List<MqttMessage> received = new CopyOnWriteArrayList<>();
        MqttClient subscriber = client("dispatch-stats-sub");
        subscriber.connect(cleanStart());
        subscriber.subscribe(new MqttSubscription[]{new MqttSubscription("dispatch/stats", 1)},
                new IMqttMessageListener[]{(topic, message) -> received.add(message)});
        MqttClient publisher = client("dispatch-stats-pub");
        publisher.connect(cleanStart());
        for (int i = 0; i < PUBLISHES; i++) {
            publisher.publish("dispatch/stats", new MqttMessage(("m" + i).getBytes(StandardCharsets.UTF_8), 1, false, null));
        }

        await().atMost(TIMEOUT).until(() -> received.size() == PUBLISHES);
        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(sum("totalMsgs") - totalBefore).isEqualTo(PUBLISHES);
            assertThat(sum("successfulMsgs") - successBefore).isEqualTo(PUBLISHES);
            assertThat(timerCount() - timedBefore).isEqualTo(PUBLISHES);
        });
        assertThat(sum("failedMsgs") - failedBefore).isZero();
        assertThat(meterRegistry.find(CONSUMED).counters())
                .isNotEmpty()
                .allSatisfy(counter -> assertThat(counter.getId().getTag("consumerId")).startsWith("dispatch-"));
        // what describes polling a queue in packs has no meaning here
        assertThat(meterRegistry.find(CONSUMED).tag("statsName", "timeoutMsgs").meters()).isEmpty();
        assertThat(meterRegistry.find(CONSUMED + ".pack.processing.time").meters()).isEmpty();
    }

    private double sum(String statsName) {
        return meterRegistry.find(CONSUMED).tag("statsName", statsName).counters().stream().mapToDouble(Counter::count).sum();
    }

    private long timerCount() {
        return meterRegistry.find(CONSUMED + ".processing.time").timers().stream().mapToLong(Timer::count).sum();
    }

    private MqttClient client(String clientId) throws Exception {
        MqttClient client = new MqttClient("tcp://localhost:" + mqttPort, clientId, new MemoryPersistence());
        clients.add(client);
        return client;
    }

    private static MqttConnectionOptions cleanStart() {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        return options;
    }

}
