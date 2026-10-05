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

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A PUBLISH whose Kafka write fails must not hold back the responses to the PUBLISH packets received after it.
 * The write failure is forced by a producer max.request.size below the size of one of the published messages.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = PersistFailurePubResponseIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "queue.kafka.msg-all.additional-producer-config=max.request.size:20000"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class PersistFailurePubResponseIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final String TOPIC = "persist/failure";
    private static final byte[] TOO_LARGE_FOR_KAFKA_PAYLOAD = new byte[30_000];
    private static final long PUB_RESPONSE_TIMEOUT_MS = 10_000;
    private static final int UNSPECIFIED_ERROR = 0x80;
    private static final int SUCCESS = 0x00;

    private MqttAsyncClient pubClient;

    @After
    public void clear() throws MqttException {
        if (pubClient != null) {
            if (pubClient.isConnected()) {
                // no quiesce: on a failure there are in-flight msgs that would never complete
                pubClient.disconnect(0).waitForCompletion(PUB_RESPONSE_TIMEOUT_MS);
            }
            pubClient.close();
        }
    }

    @Test
    public void givenMqtt5Qos1MsgFailedToPersist_whenPublishMoreMsgs_thenTheirPubAcksAreNotHeldBack() throws Throwable {
        verifyPubResponsesAfterFailedPersist("persist_failure_qos1", 1);
    }

    @Test
    public void givenMqtt5Qos2MsgFailedToPersist_whenPublishMoreMsgs_thenTheirPubRecsAreNotHeldBack() throws Throwable {
        verifyPubResponsesAfterFailedPersist("persist_failure_qos2", 2);
    }

    private void verifyPubResponsesAfterFailedPersist(String clientId, int qos) throws MqttException {
        pubClient = new MqttAsyncClient(SERVER_URI + mqttPort, clientId, new MemoryPersistence());
        pubClient.connect(new MqttConnectionOptions()).waitForCompletion(PUB_RESPONSE_TIMEOUT_MS);

        IMqttToken failedToken = pubClient.publish(TOPIC, TOO_LARGE_FOR_KAFKA_PAYLOAD, qos, false);
        failedToken.waitForCompletion(PUB_RESPONSE_TIMEOUT_MS);
        // first code is the PUBACK/PUBREC one; for QoS 2 Paho still sends PUBREL after a failure PUBREC and appends the PUBCOMP code
        assertThat(failedToken.getReasonCodes()[0]).isEqualTo(UNSPECIFIED_ERROR);

        List<IMqttToken> tokens = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            tokens.add(pubClient.publish(TOPIC, ("data_" + i).getBytes(), qos, false));
        }
        for (IMqttToken token : tokens) {
            // before the fix this timed out: the failed msg stayed at the head of the ordered response queue
            token.waitForCompletion(PUB_RESPONSE_TIMEOUT_MS);
            assertThat(token.getReasonCodes()).containsOnly(SUCCESS);
        }
        assertThat(pubClient.isConnected()).isTrue();
    }
}
