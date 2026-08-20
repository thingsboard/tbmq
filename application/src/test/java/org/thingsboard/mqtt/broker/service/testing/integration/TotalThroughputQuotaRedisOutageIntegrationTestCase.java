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
package org.thingsboard.mqtt.broker.service.testing.integration;

import io.micrometer.core.instrument.Counter;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The headline outage arc end to end, against a real Jedis failure rather than a mock: Valkey is PAUSED - the
 * socket blackholes, so draws hang for the client timeout, the harsh detection path - the quota degrades and
 * refuses through the real {@code droppedMsgs} counter and a real {@code 0x97} PUBACK, then Valkey is unpaused
 * and enforcement resumes through a real probe draw. Pause rather than stop/start, because a restarted container
 * maps a new port the already-built Spring context would never find.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaRedisOutageIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        // one token per second sustained keeps the node-local pool and credit tiny, so the outage is felt within a
        // handful of publishes - while the 600-token capacity guarantees no refusal here can be a spent-budget
        // refusal: every drop this suite asserts is attributable to the outage path alone
        "mqtt.rate-limits.total.config=600:600",
        "mqtt.rate-limits.total.block-size=1",
        // refuse from the first failed draw. The grace DURATION arithmetic is pinned deterministically at the unit
        // level against the nanoTime seam; a wall-clock grace here would re-introduce exactly the CI flakiness
        // those tests avoid. What this suite adds is the wiring a mock cannot: the Jedis timeout, the refusal
        // surfacing to a real client, and recovery once the container answers again.
        "mqtt.rate-limits.total.degraded-grace-ms=0"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaRedisOutageIntegrationTestCase extends AbstractTotalThroughputQuotaIntegrationTest {

    private static final int QUOTA_EXCEEDED = 151; // MQTT 5 reason code 0x97

    private boolean valkeyPaused;

    @After
    public void unpauseValkeyIfStillPaused() {
        unpauseValkey();
    }

    @Test
    public void givenRedisOutage_whenPublishingPastTheGrace_thenRefusesAndRecoversWhenRedisReturns() throws Throwable {
        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, "quota_redis_outage");
        List<Integer> reasonCodes = new CopyOnWriteArrayList<>();
        pubClient.setCallback(new ReasonCodeCollector(reasonCodes));
        pubClient.connect(new MqttConnectionOptions());
        AtomicInteger seq = new AtomicInteger();
        try {
            // healthy baseline: a publish is charged against the real shared bucket and stored
            double processedBefore = publishesProcessed();
            publish(pubClient, seq.getAndIncrement());
            Awaitility.await("the baseline publish is charged and stored")
                    .atMost(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS)
                    .until(() -> publishesProcessed() >= processedBefore + 1);

            pauseValkey();

            // drive traffic until a draw actually FAILS: only then is the node degraded and the (zero) grace over.
            // Refusals before that instant belong to the bounded onset gap, not to the deadline under test.
            Awaitility.await("the first failed draw marks the node degraded")
                    .atMost(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS)
                    .until(() -> {
                        publish(pubClient, seq.getAndIncrement());
                        return degradedCount() >= 1;
                    });

            // past the grace every publish must be refused: a droppedMsgs record and 0x97 to the MQTT 5 publisher
            double droppedBefore = droppedMsgs();
            reasonCodes.clear();
            Awaitility.await("a publish past the grace is refused with QUOTA_EXCEEDED and lands in droppedMsgs")
                    .atMost(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS)
                    .until(() -> {
                        publish(pubClient, seq.getAndIncrement());
                        return droppedMsgs() > droppedBefore && reasonCodes.contains(QUOTA_EXCEEDED);
                    });

            double processedAtOutage = publishesProcessed();
            unpauseValkey();

            // recovery is driven through the same public charge path production uses: a probing draw lands against
            // the resumed socket and granting resumes - a node that could not probe would refuse forever
            Awaitility.await("after Redis returns, a publish is granted and stored again")
                    .atMost(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS)
                    .until(() -> {
                        publish(pubClient, seq.getAndIncrement());
                        return publishesProcessed() > processedAtOutage;
                    });
        } finally {
            unpauseValkey(); // before the client teardown, so the disconnect never races a paused broker dependency
            pubClient.disconnect();
            pubClient.close();
        }
    }

    private void publish(MqttClient client, int seq) throws MqttException {
        client.publish("quota/redis/outage", ("data_" + seq).getBytes(StandardCharsets.UTF_8), 1, false);
    }

    private double degradedCount() {
        Counter counter = meterRegistry.find(StatsType.THROUGHPUT_QUOTA_DEGRADED.getPrintName()).counter();
        return counter == null ? 0 : counter.count();
    }

    private void pauseValkey() {
        valkey.getDockerClient().pauseContainerCmd(valkey.getContainerId()).exec();
        valkeyPaused = true;
        log.info("Paused the Valkey container: the shared bucket is now a black hole");
    }

    private void unpauseValkey() {
        if (valkeyPaused) {
            valkey.getDockerClient().unpauseContainerCmd(valkey.getContainerId()).exec();
            valkeyPaused = false;
            log.info("Unpaused the Valkey container");
        }
    }

    /** Collects PUBACK reason codes off Paho's callback thread; everything else is irrelevant to this suite. */
    private record ReasonCodeCollector(List<Integer> reasonCodes) implements MqttCallback {

        @Override
        public void deliveryComplete(IMqttToken token) {
            for (int code : token.getReasonCodes()) {
                reasonCodes.add(code);
            }
        }

        @Override
        public void disconnected(MqttDisconnectResponse response) {
        }

        @Override
        public void mqttErrorOccurred(MqttException e) {
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
        }

        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
        }

        @Override
        public void authPacketArrived(int reasonCode, MqttProperties properties) {
        }
    }
}
