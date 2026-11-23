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
package org.thingsboard.mqtt.broker.lite.spec;

import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgQueueStore;
import org.thingsboard.mqtt.broker.dao.messages.QueueAppend;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.io.EOFException;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * A publish whose copy for a persistent DEVICE subscriber cannot be written to the store fails (the card #656 rule in
 * Lite): on MQTT 5 it is answered with reason code 0x80 (Unspecified error); on MQTT 3.1.1, which has no failure code,
 * the connection is closed. The store fails every write that holds a copy for one subscriber and works for the others.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = FailedPublishLiteTest.class, loader = SpringBootContextLoader.class)
@RunWith(SpringRunner.class)
public class FailedPublishLiteTest extends AbstractLiteSpecCase {

    private static final String FAILING_SUBSCRIBER = "failed-publish-sub";
    private static final String FAILING_TOPIC = "spec/failed-publish/failing";
    private static final int UNSPECIFIED_ERROR = 0x80;

    @MockitoSpyBean
    private DeviceMsgQueueStore deviceMsgQueueStore;

    @Before
    public void failTheStoreForOneSubscriber() throws IOException {
        doAnswer(invocation -> {
            List<QueueAppend> appends = invocation.getArgument(0);
            if (appends.stream().anyMatch(append -> FAILING_SUBSCRIBER.equals(append.queueKey()))) {
                throw new StoreException("Failed to append to the device queues (test)");
            }
            return invocation.callRealMethod();
        }).when(deviceMsgQueueStore).append(anyList());
        // offline, so each copy for it must be written to the store
        disconnect(persistentSubscriber(FAILING_SUBSCRIBER, FAILING_TOPIC, 1), FAILING_SUBSCRIBER);
    }

    @Test
    public void aStoreErrorOnMqtt5IsAcknowledgedWith0x80() throws Exception {
        RawMqttClient publisher = connectClean("failed-publish-mqtt5-pub", MqttVersion.MQTT_5);

        assertThat(publisher.publishQos1(FAILING_TOPIC, 1, "qos1", null)).as("PUBACK reason code").isEqualTo(UNSPECIFIED_ERROR);
        assertThat(publisher.publishQos2(FAILING_TOPIC, 2, "qos2", null)).as("PUBREC reason code").isEqualTo(UNSPECIFIED_ERROR);
        verifyTheStoreFailedTheCopies(2);
    }

    @Test
    public void aStoreErrorOnMqtt311ClosesTheConnection() throws Exception {
        RawMqttClient publisher = connectClean("failed-publish-mqtt311-pub", MqttVersion.MQTT_3_1_1);

        publisher.publish(FAILING_TOPIC, 1, 1, "qos1", null);
        // the next thing the publisher sees is the closed connection, not a PUBACK
        assertThatThrownBy(() -> publisher.next(TIMEOUT)).isInstanceOf(EOFException.class);
        verifyTheStoreFailedTheCopies(1);
    }

    @Test
    public void theBrokerKeepsServingAfterAStoreError() throws Exception {
        String otherTopic = "spec/failed-publish/other";
        RawMqttClient otherSubscriber = persistentSubscriber("failed-publish-other-sub", otherTopic, 1);
        RawMqttClient publisher = connectClean("failed-publish-serving-pub", MqttVersion.MQTT_5);
        assertThat(publisher.publishQos1(FAILING_TOPIC, 1, "failing", null)).isEqualTo(UNSPECIFIED_ERROR);
        verifyTheStoreFailedTheCopies(1);

        assertThat(publisher.publishQos1(otherTopic, 2, "after", null)).isEqualTo(SUCCESS);

        RawMqttClient.Packet publish = nextPublish(otherSubscriber);
        assertThat(publish.payload()).isEqualTo("after");
        otherSubscriber.pubAck(publish.packetId());
    }

    /**
     * The failure the test saw is the stubbed one: the store was asked {@code count} times to append a batch holding a
     * copy for the failing subscriber (the spy's invocations are reset after each test).
     */
    private void verifyTheStoreFailedTheCopies(int count) {
        verify(deviceMsgQueueStore, times(count)).append(argThat(appends ->
                appends.stream().anyMatch(append -> FAILING_SUBSCRIBER.equals(append.queueKey()))));
    }

}
