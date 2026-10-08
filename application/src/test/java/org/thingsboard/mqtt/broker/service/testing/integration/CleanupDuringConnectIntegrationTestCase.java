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

import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.service.mqtt.client.cleanup.ClientSessionCleanUpServiceImpl;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.thingsboard.mqtt.broker.session.DisconnectReasonType.ON_ADMINISTRATIVE_ACTION;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = CleanupDuringConnectIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class CleanupDuringConnectIntegrationTestCase extends AbstractPubSubIntegrationTest {
    private static final String DEVICE = "cleanup-connecting-device";
    private static final String PUBLISHER = "cleanup-command-publisher";
    private static final String TOPIC = "cleanup/device/command";
    @Autowired ClientSessionCleanUpServiceImpl cleanup;
    @Autowired ClientSessionCache cache;
    @MockitoSpyBean ClientSessionCtxService contexts;
    @MockitoSpyBean ClientSessionEventService events;
    private final List<MqttClientCredentials> credentials = new ArrayList<>();

    @After
    public void removeCredentials() {
        credentials.forEach(c -> mqttClientCredentialsService.deleteCredentials(c.getId()));
    }

    @Test
    public void cleanupBetweenClusterAcceptanceAndLocalRegistrationPreservesCommandDelivery() throws Exception {
        credentials.add(mqttClientCredentialsService.saveCredentials(
                TestUtils.createDeviceClientCredentialsWithAuth(DEVICE, List.of(TOPIC))));
        credentials.add(mqttClientCredentialsService.saveCredentials(
                TestUtils.createDeviceClientCredentialsWithAuth(PUBLISHER, List.of(TOPIC))));
        enableBasicProvider();
        CountDownLatch registering = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        CountDownLatch delivered = new CountDownLatch(1);
        doAnswer(invocation -> {
            ClientSessionCtx ctx = invocation.getArgument(0);
            if (DEVICE.equals(ctx.getClientId())) {
                registering.countDown();
                Assert.assertTrue("registration released", resume.await(10, TimeUnit.SECONDS));
            }
            return invocation.callRealMethod();
        }).when(contexts).registerSession(any());
        MqttClient device = new MqttClient(SERVER_URI + mqttPort, DEVICE, new MemoryPersistence());
        MqttClient publisher = new MqttClient(SERVER_URI + mqttPort, PUBLISHER, new MemoryPersistence());
        try {
            try {
                device.connect();
                Assert.assertTrue("accepted session reached registration", registering.await(10, TimeUnit.SECONDS));
                Assert.assertTrue(cache.getClientSessionInfo(DEVICE).isConnected());
                Assert.assertFalse(contexts.hasSession(DEVICE));
                cleanup.cleanUp();
                verify(events, never()).notifyClientDisconnected(
                        argThat(info -> info != null && DEVICE.equals(info.getClientId())),
                        eq(ON_ADMINISTRATIVE_ACTION), isNull());
            } finally {
                resume.countDown();
            }
            await().atMost(10, TimeUnit.SECONDS).until(() -> contexts.hasSession(DEVICE));
            device.subscribe(new MqttSubscription[] {new MqttSubscription(TOPIC, 1)},
                    new org.eclipse.paho.mqttv5.client.IMqttMessageListener[] {(topic, message) -> {
                        if (java.util.Arrays.equals(PAYLOAD, message.getPayload())) delivered.countDown();
                    }});
            publisher.connect();
            publisher.publish(TOPIC, PAYLOAD, 1, false);
            Assert.assertTrue("command delivered after cleanup", delivered.await(10, TimeUnit.SECONDS));
            Assert.assertTrue(cache.getClientSessionInfo(DEVICE).isConnected());
        } finally {
            resume.countDown();
            TestUtils.disconnectAndCloseClient(publisher);
            TestUtils.disconnectAndCloseClient(device);
        }
    }
}
