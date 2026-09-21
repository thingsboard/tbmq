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
import org.awaitility.Awaitility;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reproduces <a href="https://github.com/thingsboard/tbmq/issues/372">#372</a>: the Session Expiry Interval must be
 * honoured at connect time, independently of the periodic cleanup job. The cron is set to a date that effectively
 * never occurs so that only the connect path can discard the expired session.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = SessionExpiryOnConnectIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.client-session-expiry.cron=0 0 0 29 2 ?" // effectively never: midnight on Feb 29th
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class SessionExpiryOnConnectIntegrationTestCase extends AbstractPubSubIntegrationTest {

    static final String MY_TOPIC = "my/topic";
    static final String CLIENT_ID = "expiryOnConnectClient";
    static final String PUBLISHER_ID = "expiryOnConnectPublisher";

    @Autowired
    private ClientSessionCache clientSessionCache;
    @Autowired
    private ClientSubscriptionCache clientSubscriptionCache;
    @Autowired
    private DeviceMsgService deviceMsgService;

    @After
    public void clearStoredSession() throws Throwable {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        options.setSessionExpiryInterval(0L);
        MqttClient client = new MqttClient(SERVER_URI + mqttPort, CLIENT_ID);
        client.connect(options);
        client.disconnect();
        client.close();
    }

    @Test
    public void givenExpiredSession_whenReconnectWithoutCleanStart_thenSessionNotPresentAndNothingReplayed() throws Throwable {
        connectSubscribeAndDisconnect(1L);
        awaitSessionExpired();

        // message published while the client is offline; a resumed session would replay it after the reconnect.
        // Routing and the persisted-message write are asynchronous to the PUBACK, so wait for the write before
        // reconnecting; otherwise the reconnect's clear could race the write and the message would be replayed
        publishQos1("after-expiry");
        awaitPersistedMessages(1);

        AtomicInteger received = new AtomicInteger();
        MqttClient client = new MqttClient(SERVER_URI + mqttPort, CLIENT_ID);
        client.setCallback(countingCallback(received));
        IMqttToken token = client.connectWithResult(noCleanStart());

        Assert.assertFalse(token.getSessionPresent());
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> clientSubscriptionCache.getClientSubscriptions(CLIENT_ID).isEmpty());
        Awaitility.await()
                .during(2, TimeUnit.SECONDS)
                .atMost(3, TimeUnit.SECONDS)
                .until(() -> received.get() == 0);

        client.disconnect();
        client.close();
    }

    @Test
    public void givenNotExpiredSession_whenReconnectWithoutCleanStart_thenSessionPresentAndSubscriptionsKept() throws Throwable {
        connectSubscribeAndDisconnect(60L);

        MqttClient client = new MqttClient(SERVER_URI + mqttPort, CLIENT_ID);
        IMqttToken token = client.connectWithResult(noCleanStart());

        Assert.assertTrue(token.getSessionPresent());
        Assert.assertEquals(1, clientSubscriptionCache.getClientSubscriptions(CLIENT_ID).size());

        client.disconnect();
        client.close();
    }

    @Test
    public void givenMqtt3NotCleanSession_whenReconnect_thenSessionPresentAndSubscriptionsKept() throws Throwable {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(false);

        Mqtt3Connection first = connectMqtt3(options);
        first.client().subscribe(MY_TOPIC, 1);
        first.client().disconnect();
        first.client().close();

        // MQTTv3 cleanSession=false has no Session Expiry Interval: the connect path must never treat it as expired,
        // whatever the elapsed time, so there is no state transition to wait for - the broker-side rule is asserted directly
        ClientSessionInfo stored = clientSessionCache.getClientSessionInfo(CLIENT_ID);
        Assert.assertNotNull(stored);
        Assert.assertEquals(ClientSessionInfo.NO_SESSION_END, stored.getSessionEndTs(ClientSessionInfo.NO_TTL));

        Mqtt3Connection second = connectMqtt3(options);

        Assert.assertTrue(second.sessionPresent());
        Assert.assertEquals(1, clientSubscriptionCache.getClientSubscriptions(CLIENT_ID).size());

        second.client().disconnect();
        second.client().close();
    }

    private void connectSubscribeAndDisconnect(long sessionExpiryInterval) throws Throwable {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        options.setSessionExpiryInterval(sessionExpiryInterval);

        MqttClient client = new MqttClient(SERVER_URI + mqttPort, CLIENT_ID);
        client.connect(options);
        client.subscribe(new MqttSubscription[]{new MqttSubscription(MY_TOPIC, 1)}, new IMqttMessageListener[]{(topic, message) -> {
        }});
        client.disconnect();
        client.close();

        Assert.assertNotNull(clientSessionCache.getClientSession(CLIENT_ID));
        Assert.assertEquals(1, clientSubscriptionCache.getClientSubscriptions(CLIENT_ID).size());
    }

    /**
     * The v3 and v5 Paho clients share simple names, so the v3 client is created in one place and stays fully qualified.
     */
    private Mqtt3Connection connectMqtt3(MqttConnectOptions options) throws Throwable {
        org.eclipse.paho.client.mqttv3.MqttClient client = new org.eclipse.paho.client.mqttv3.MqttClient(SERVER_URI + mqttPort, CLIENT_ID);
        org.eclipse.paho.client.mqttv3.IMqttToken token = client.connectWithResult(options);
        return new Mqtt3Connection(client, token.getSessionPresent());
    }

    private record Mqtt3Connection(org.eclipse.paho.client.mqttv3.MqttClient client, boolean sessionPresent) {
    }

    private void awaitSessionExpired() {
        // waits exactly as long as needed and proves disconnectedAt was stamped; the cleanup cron never fires here
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> {
                    ClientSessionInfo info = clientSessionCache.getClientSessionInfo(CLIENT_ID);
                    return info != null && info.isExpired(System.currentTimeMillis(), ClientSessionInfo.NO_TTL);
                });
    }

    private void awaitPersistedMessages(int expected) {
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> deviceMsgService.findPersistedMessages(CLIENT_ID).toCompletableFuture().get().size() == expected);
    }

    private void publishQos1(String payload) throws Throwable {
        MqttClient publisher = new MqttClient(SERVER_URI + mqttPort, PUBLISHER_ID);
        publisher.connect();
        publisher.publish(MY_TOPIC, payload.getBytes(), 1, false);
        publisher.disconnect();
        publisher.close();
    }

    private static MqttConnectionOptions noCleanStart() {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(false);
        return options;
    }

    private static MqttCallback countingCallback(AtomicInteger counter) {
        return new MqttCallback() {
            @Override
            public void disconnected(MqttDisconnectResponse response) {
            }

            @Override
            public void mqttErrorOccurred(MqttException e) {
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                counter.incrementAndGet();
            }

            @Override
            public void deliveryComplete(IMqttToken token) {
            }

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
            }

            @Override
            public void authPacketArrived(int reasonCode, MqttProperties properties) {
            }
        };
    }
}
