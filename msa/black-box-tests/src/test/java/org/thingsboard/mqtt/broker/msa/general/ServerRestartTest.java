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
package org.thingsboard.mqtt.broker.msa.general;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.concurrentunit.Waiter;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.Test;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.DockerComposeContainer;
import org.thingsboard.mqtt.broker.msa.AbstractContainerTest;
import org.thingsboard.mqtt.broker.msa.ContainerTestSuite;
import org.thingsboard.mqtt.broker.msa.DockerComposeExecutor;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class ServerRestartTest extends AbstractContainerTest {

    //TODO: make it readable and not so dependent on Thread.sleep()
    @Test
    public void testBrokerRestart() throws Throwable {
        Thread.sleep(2000);
        DockerComposeExecutor dockerCompose = ContainerTestSuite.dockerManager.getDockerComposeExecutor();
        final int messagesBeforeRestart = 50;
        final int messagesAfterRestart = 50;
        MqttClient pubClient = new MqttClient("tcp://localhost:1883", "black_box_test_pub");
        pubClient.connect();

        Waiter waiter = new Waiter();
        MqttClient subClient = new MqttClient("tcp://localhost:1883", "black_box_test_sub");
        subClient.connect();
        AtomicReference<TestPublishMsg> previousMsg = new AtomicReference<>();
        subClient.subscribe("test", 0, (topic, message) -> {
            TestPublishMsg currentMsg = mapper.readValue(message.getPayload(), TestPublishMsg.class);
            if (previousMsg.get() != null) {
                waiter.assertEquals(previousMsg.get().sequenceId + 1, currentMsg.sequenceId);
            }
            if (currentMsg.sequenceId == messagesBeforeRestart - 1) {
                waiter.resume();
            }
            previousMsg.getAndSet(currentMsg);
        });
        for (int j = 0; j < messagesBeforeRestart; j++) {
            MqttMessage msg = new MqttMessage();
            TestPublishMsg payload = new TestPublishMsg(j);
            msg.setPayload(mapper.writeValueAsBytes(payload));
            msg.setQos(0);
            pubClient.publish("test", msg);
        }
        waiter.await(2, TimeUnit.SECONDS);
        pubClient.disconnect();
        subClient.disconnect();
        pubClient.close();
        subClient.close();

        DockerComposeContainer testContainer = ContainerTestSuite.getTestContainer();
        Optional<ContainerState> containerStateOpt = testContainer.getContainerByServiceName("tb-mqtt-broker_1");
        ContainerState containerState = containerStateOpt.orElseThrow();
        dockerCompose.withCommand("restart " + containerState.getContainerId());
        dockerCompose.invokeDocker();

        Thread.sleep(2000);
        subClient = new MqttClient("tcp://localhost:1883", "black_box_test_sub");
        subClient.connect();
        subClient.subscribe("test", 0, (topic, message) -> {
            TestPublishMsg currentMsg = mapper.readValue(message.getPayload(), TestPublishMsg.class);
            if (previousMsg.get() != null) {
                waiter.assertEquals(previousMsg.get().sequenceId + 1, currentMsg.sequenceId);
            }
            if (currentMsg.sequenceId == messagesBeforeRestart + messagesAfterRestart - 1) {
                waiter.resume();
            }
            previousMsg.getAndSet(currentMsg);
        });
        pubClient = new MqttClient("tcp://localhost:1883", "black_box_test_pub");
        pubClient.connect();
        for (int j = messagesBeforeRestart; j < messagesAfterRestart + messagesBeforeRestart; j++) {
            MqttMessage msg = new MqttMessage();
            TestPublishMsg payload = new TestPublishMsg(j);
            msg.setPayload(mapper.writeValueAsBytes(payload));
            msg.setQos(0);
            pubClient.publish("test", msg);
        }
        waiter.await(1, TimeUnit.SECONDS);
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    static class TestPublishMsg {
        int sequenceId;
    }
}
