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
package org.thingsboard.mqtt.broker.service.testing.integration;

import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = MqttSubscriptionOptionsIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class MqttSubscriptionOptionsIntegrationTestCase extends AbstractPubSubIntegrationTest {

    static final String GENERIC_USER_NAME = "genericUserName";
    static final String DEV_CLIENT_ID = "devClientId";
    static final String APP_CLIENT_ID = "appClientId";
    static final String MY_TOPIC = "my/topic";
    static final String SHARED_SUBS_PREFIX = "$share/group/";
    static final String MSG_PAYLOAD = "myRetainMsg";

    @Autowired
    private MqttClientCredentialsService credentialsService;

    private MqttClientCredentials genericCredentials;
    private MqttClientCredentials devCredentials;
    private MqttClientCredentials appCredentials;

    private MqttClient devClient;
    private MqttClient appClient;

    @Before
    public void beforeTest() throws Exception {
        genericCredentials = saveCredentials(TestUtils.createDeviceClientCredentials(null, GENERIC_USER_NAME));
        devCredentials = saveCredentials(TestUtils.createDeviceClientCredentials(DEV_CLIENT_ID, null));
        appCredentials = saveCredentials(TestUtils.createApplicationClientCredentials(APP_CLIENT_ID, null));
        enabledBasicProvider();
    }

    private MqttClientCredentials saveCredentials(MqttClientCredentials credentials) {
        return credentialsService.saveCredentials(credentials);
    }

    @After
    public void clear() throws Exception {
        if (devClient != null && devClient.isConnected()) {
            devClient.disconnect();
            awaitUntilDisconnected(devClient);
        }
        if (appClient != null && appClient.isConnected()) {
            appClient.disconnect();
            awaitUntilDisconnected(appClient);
        }
        MqttConnectionOptions persistOptions = new MqttConnectionOptions();
        persistOptions.setCleanStart(true);

        devClient = new MqttClient(SERVER_URI + mqttPort, DEV_CLIENT_ID);
        devClient.connect(persistOptions);
        devClient.disconnect();

        appClient = new MqttClient(SERVER_URI + mqttPort, APP_CLIENT_ID);
        appClient.connect(persistOptions);
        appClient.disconnect();

        credentialsService.deleteCredentials(genericCredentials.getId());
        credentialsService.deleteCredentials(devCredentials.getId());
        credentialsService.deleteCredentials(appCredentials.getId());
    }

    @Test
    public void given3SubClientsWithRetainAsPublished_whenPublishRetainMsg_thenReceiveMsgWithRetainFlag() throws Throwable {
        CountDownLatch latch = new CountDownLatch(3);

        IMqttMessageListener[] listeners = {(topic, message) -> {
            log.error("[{}] Received msg: {}", topic, message.getProperties());
            Assert.assertEquals(MSG_PAYLOAD, new String(message.getPayload()));
            Assert.assertTrue(message.isRetained());

            latch.countDown();
        }};

        process(true, listeners, latch);
    }

    @Test
    public void given3SubClientsWithoutRetainAsPublished_whenPublishRetainMsg_thenReceiveMsgWithoutRetainFlag() throws Throwable {
        CountDownLatch latch = new CountDownLatch(3);

        IMqttMessageListener[] listeners = {(topic, message) -> {
            log.error("[{}] Received msg: {}", topic, message.getProperties());
            Assert.assertEquals(MSG_PAYLOAD, new String(message.getPayload()));
            Assert.assertFalse(message.isRetained());

            latch.countDown();
        }};

        process(false, listeners, latch);
    }

    private void process(boolean retainAsPublished, IMqttMessageListener[] listeners, CountDownLatch latch) throws Throwable {
        MqttSubscription mqttSubscription = new MqttSubscription(MY_TOPIC, 2);
        mqttSubscription.setRetainAsPublished(retainAsPublished);
        MqttSubscription[] subscriptions = {mqttSubscription};

        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, "cleanClient");
        MqttConnectionOptions options = getConnectionOptionsWithGenericUserName();
        subClient.connect(options);
        subClient.subscribe(subscriptions, listeners);

        MqttConnectionOptions persistOptions = new MqttConnectionOptions();
        persistOptions.setCleanStart(false);

        devClient = new MqttClient(SERVER_URI + mqttPort, DEV_CLIENT_ID);
        devClient.connect(persistOptions);
        devClient.subscribe(subscriptions, listeners);

        appClient = new MqttClient(SERVER_URI + mqttPort, APP_CLIENT_ID);
        appClient.connect(persistOptions);
        appClient.subscribe(subscriptions, listeners);

        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, "pubClient");
        pubClient.connect(options);
        pubClient.publish(MY_TOPIC, MSG_PAYLOAD.getBytes(StandardCharsets.UTF_8), 2, true);

        boolean await = latch.await(3, TimeUnit.SECONDS);
        Assert.assertTrue(await);

        disconnectClient(pubClient);

        disconnectClient(devClient);

        disconnectClient(appClient);

        clearRetainedMsg();
    }

    @Test
    public void givenSubClientWithRetainHandling2_whenPublishRetainMsg_thenNotReceiveRetainMsg() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);

        MqttConnectionOptions options = getConnectionOptionsWithGenericUserName();

        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, "pubClient");
        pubClient.connect(options);
        pubClient.publish(MY_TOPIC, MSG_PAYLOAD.getBytes(StandardCharsets.UTF_8), 2, true);

        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, "cleanClient");
        subClient.connect(options);

        MqttSubscription mqttSubscription = new MqttSubscription(MY_TOPIC, 2);
        mqttSubscription.setRetainHandling(2);
        MqttSubscription[] subscriptions = {mqttSubscription};

        IMqttMessageListener[] listeners = {(topic, message) -> latch.countDown()};
        subClient.subscribe(subscriptions, listeners);

        boolean await = latch.await(2, TimeUnit.SECONDS);
        Assert.assertFalse(await);

        disconnectClient(pubClient);

        disconnectClient(subClient);

        clearRetainedMsg();
    }

    @Test
    public void givenSubClientWithRetainHandling1_whenPublishRetainMsgAndSubscriptionNotPresent_thenReceiveRetainMsg() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);

        MqttConnectionOptions options = getConnectionOptionsWithGenericUserName();

        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, "pubClient");
        pubClient.connect(options);
        pubClient.publish(MY_TOPIC, MSG_PAYLOAD.getBytes(StandardCharsets.UTF_8), 2, true);

        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, "cleanClient");
        subClient.connect(options);

        IMqttMessageListener[] listeners = {(topic, message) -> {
            log.error("[{}] Received msg: {}", topic, message.getProperties());
            Assert.assertEquals(MSG_PAYLOAD, new String(message.getPayload()));
            Assert.assertTrue(message.isRetained());

            latch.countDown();
        }};

        MqttSubscription mqttSubscription = new MqttSubscription(MY_TOPIC, 2);
        mqttSubscription.setRetainHandling(1);
        MqttSubscription[] subscriptions = {mqttSubscription};

        subClient.subscribe(subscriptions, listeners);

        boolean await = latch.await(2, TimeUnit.SECONDS);
        Assert.assertTrue(await);

        disconnectClient(pubClient);

        disconnectClient(subClient);

        clearRetainedMsg();
    }

    @Test
    public void givenSubClientWithRetainHandling1_whenPublishRetainMsgAndSubscriptionPresent_thenNotReceiveRetainMsg() throws Throwable {
        CountDownLatch latch = new CountDownLatch(1);

        MqttConnectionOptions options = getConnectionOptionsWithGenericUserName();

        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, "cleanClient");
        subClient.connect(options);

        IMqttMessageListener[] listeners = {(topic, message) -> {
            log.error("[{}] Received msg: {}", topic, message.getProperties());
            Assert.assertEquals(MSG_PAYLOAD, new String(message.getPayload()));
            Assert.assertFalse(message.isRetained());

            latch.countDown();
        }};

        MqttSubscription mqttSubscription = new MqttSubscription(MY_TOPIC, 2);
        mqttSubscription.setRetainHandling(1);
        MqttSubscription[] subscriptions = {mqttSubscription};

        subClient.subscribe(subscriptions, listeners);

        MqttClient pubClient = new MqttClient(SERVER_URI + mqttPort, "pubClient");
        pubClient.connect(options);
        pubClient.publish(MY_TOPIC, MSG_PAYLOAD.getBytes(StandardCharsets.UTF_8), 2, true);

        boolean await = latch.await(2, TimeUnit.SECONDS);
        Assert.assertTrue(await);

        CountDownLatch newLatch = new CountDownLatch(1);
        IMqttMessageListener[] newListeners = {(topic, message) -> newLatch.countDown()};

        MqttSubscription newMqttSubscription = new MqttSubscription(MY_TOPIC, 1);
        newMqttSubscription.setRetainHandling(1);
        MqttSubscription[] newSubscriptions = {newMqttSubscription};
        subClient.subscribe(newSubscriptions, newListeners);

        await = newLatch.await(2, TimeUnit.SECONDS);
        Assert.assertFalse(await);

        disconnectClient(pubClient);

        disconnectClient(subClient);

        clearRetainedMsg();
    }

    @Test(expected = MqttException.class)
    public void givenSubClientWithNoLocalOptionSet_whenSubscribeToSharedSubs_thenSubscriptionFailedClientDisconnected() throws Throwable {
        MqttClient subClient = new MqttClient(SERVER_URI + mqttPort, "sharedSubsNoLocal");
        subClient.connect(getConnectionOptionsWithGenericUserName());

        IMqttMessageListener[] listeners = {(topic, message) -> {
        }};
        MqttSubscription mqttSubscription = new MqttSubscription(SHARED_SUBS_PREFIX + MY_TOPIC, 2);
        mqttSubscription.setNoLocal(true);
        MqttSubscription[] subscriptions = {mqttSubscription};

        subClient.subscribe(subscriptions, listeners);
    }

    private void clearRetainedMsg() throws MqttException {
        MqttClient pubClientClearRetained = new MqttClient(SERVER_URI + mqttPort, "pubClearRetained");
        MqttConnectionOptions clearRetainedOptions = getConnectionOptionsWithGenericUserName();

        pubClientClearRetained.connect(clearRetainedOptions);
        pubClientClearRetained.publish(MY_TOPIC, "".getBytes(StandardCharsets.UTF_8), 0, true);
        disconnectClient(pubClientClearRetained);
    }

    private MqttConnectionOptions getConnectionOptionsWithGenericUserName() {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setUserName(GENERIC_USER_NAME);
        return options;
    }

    private void disconnectClient(MqttClient client) throws MqttException {
        client.disconnect();
        client.close();
    }

    private void awaitUntilDisconnected(MqttClient client) {
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> !client.isConnected());
    }
}