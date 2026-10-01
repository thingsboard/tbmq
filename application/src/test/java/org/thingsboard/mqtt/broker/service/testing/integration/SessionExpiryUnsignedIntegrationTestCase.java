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
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

import java.util.concurrent.TimeUnit;

/**
 * The Session Expiry Interval is a Four Byte Integer, i.e. unsigned [MQTT-5 §1.5.3, §3.1.2.11.2]: 0xFFFFFFFF means the
 * Session does not expire, and the Server may cap any value to its own maximum, telling the Client in CONNACK. The same
 * applies to the interval a Client sets on DISCONNECT.
 * The cap is set low here so that it elapses within a test; the cron never fires, so only the connect path decides.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = SessionExpiryUnsignedIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.client-session-expiry.cron=0 0 0 29 2 ?", // effectively never: midnight on Feb 29th
        "mqtt.client-session-expiry.max-expiry-interval=" + SessionExpiryUnsignedIntegrationTestCase.MAX_EXPIRY_SEC
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class SessionExpiryUnsignedIntegrationTestCase extends AbstractPubSubIntegrationTest {

    static final int MAX_EXPIRY_SEC = 3;
    static final long NEVER_EXPIRES = 0xFFFFFFFFL;
    static final long ABOVE_SIGNED_INT_MAX = 0xFFFFFFFEL;
    static final String MY_TOPIC = "my/topic";
    static final String CLIENT_ID = "unsignedExpiryClient";

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
    public void givenCleanStartAndNeverExpiringSession_whenReconnect_thenSessionPresent() throws Throwable {
        connectSubscribeAndDisconnect(true, NEVER_EXPIRES);

        Assert.assertTrue(reconnectWithoutCleanStart().getSessionPresent());
    }

    @Test
    public void givenNoCleanStartAndExpiryAboveSignedIntMax_whenReconnect_thenSessionPresent() throws Throwable {
        connectSubscribeAndDisconnect(false, ABOVE_SIGNED_INT_MAX);

        Assert.assertTrue(reconnectWithoutCleanStart().getSessionPresent());
    }

    @Test
    public void givenNeverExpiringSession_whenConnect_thenConnAckCarriesTheCappedInterval() throws Throwable {
        IMqttToken token = connectSubscribeAndDisconnect(false, NEVER_EXPIRES);

        Assert.assertEquals(Long.valueOf(MAX_EXPIRY_SEC), token.getResponseProperties().getSessionExpiryInterval());
    }

    @Test
    public void givenNeverExpiringSession_whenTheCapElapses_thenSessionNotPresent() throws Throwable {
        connectSubscribeAndDisconnect(false, NEVER_EXPIRES);
        TimeUnit.SECONDS.sleep(MAX_EXPIRY_SEC + 1);

        Assert.assertFalse(reconnectWithoutCleanStart().getSessionPresent());
    }

    /**
     * Control: an in-range value above the cap is capped, so the harness can tell a cap from no cap.
     */
    @Test
    public void givenExpiryAboveTheCap_whenTheCapElapses_thenSessionNotPresent() throws Throwable {
        IMqttToken token = connectSubscribeAndDisconnect(false, 60L);
        Assert.assertEquals(Long.valueOf(MAX_EXPIRY_SEC), token.getResponseProperties().getSessionExpiryInterval());
        TimeUnit.SECONDS.sleep(MAX_EXPIRY_SEC + 1);

        Assert.assertFalse(reconnectWithoutCleanStart().getSessionPresent());
    }

    @Test
    public void givenDisconnectAskingNeverToExpire_whenReconnectBeforeTheCap_thenSessionPresent() throws Throwable {
        connectSubscribeAndDisconnect(true, 1L, disconnectWithSessionExpiry(NEVER_EXPIRES));
        TimeUnit.SECONDS.sleep(MAX_EXPIRY_SEC - 1);

        Assert.assertTrue(reconnectWithoutCleanStart().getSessionPresent());
    }

    @Test
    public void givenDisconnectWithExpiryAboveTheCap_whenTheCapElapses_thenSessionNotPresent() throws Throwable {
        connectSubscribeAndDisconnect(true, 1L, disconnectWithSessionExpiry(60L));
        TimeUnit.SECONDS.sleep(MAX_EXPIRY_SEC + 1);

        Assert.assertFalse(reconnectWithoutCleanStart().getSessionPresent());
    }

    private IMqttToken connectSubscribeAndDisconnect(boolean cleanStart, long sessionExpiryInterval) throws Throwable {
        return connectSubscribeAndDisconnect(cleanStart, sessionExpiryInterval, new MqttProperties());
    }

    private IMqttToken connectSubscribeAndDisconnect(boolean cleanStart, long sessionExpiryInterval,
                                                     MqttProperties disconnectProperties) throws Throwable {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(cleanStart);
        options.setSessionExpiryInterval(sessionExpiryInterval);

        MqttAsyncClient client = new MqttAsyncClient(SERVER_URI + mqttPort, CLIENT_ID);
        IMqttToken token = client.connect(options);
        token.waitForCompletion();
        client.subscribe(MY_TOPIC, 1).waitForCompletion();
        client.disconnect(0, null, null, 0, disconnectProperties).waitForCompletion();
        client.close();
        return token;
    }

    private static MqttProperties disconnectWithSessionExpiry(long sessionExpiryInterval) {
        MqttProperties properties = new MqttProperties();
        properties.setSessionExpiryInterval(sessionExpiryInterval);
        return properties;
    }

    private IMqttToken reconnectWithoutCleanStart() throws Throwable {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(false);
        MqttClient client = new MqttClient(SERVER_URI + mqttPort, CLIENT_ID);
        IMqttToken token = client.connectWithResult(options);
        client.disconnect();
        client.close();
        return token;
    }
}
