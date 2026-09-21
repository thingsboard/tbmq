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
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
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
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;

import java.util.concurrent.TimeUnit;

/**
 * Reproduces <a href="https://github.com/thingsboard/tbmq/issues/372">#372</a>: the Session Expiry Interval must be
 * honoured at connect time, independently of the periodic cleanup job. The cron is set to a date that effectively never occurs so
 * that only the connect path can discard the expired session.
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

    @Autowired
    private ClientSessionCache clientSessionCache;
    @Autowired
    private ClientSubscriptionCache clientSubscriptionCache;

    @Test
    public void givenExpiredSession_whenReconnectWithoutCleanStart_thenSessionNotPresentAndSubscriptionsCleared() throws Throwable {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        options.setSessionExpiryInterval(1L);

        MqttClient client = new MqttClient(SERVER_URI + mqttPort, CLIENT_ID);
        client.connect(options);
        client.subscribe(new MqttSubscription[]{new MqttSubscription(MY_TOPIC, 1)}, new IMqttMessageListener[]{(topic, message) -> {
        }});
        client.disconnect();
        client.close();

        Assert.assertNotNull(clientSessionCache.getClientSession(CLIENT_ID));
        Assert.assertEquals(1, clientSubscriptionCache.getClientSubscriptions(CLIENT_ID).size());

        // wait past the granted interval; the cleanup cron never fires in this test
        Thread.sleep(TimeUnit.SECONDS.toMillis(3));
        Assert.assertNotNull(clientSessionCache.getClientSession(CLIENT_ID));

        client = new MqttClient(SERVER_URI + mqttPort, CLIENT_ID);
        options.setCleanStart(false);
        IMqttToken token = client.connectWithResult(options);

        Assert.assertFalse(token.getSessionPresent());
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> clientSubscriptionCache.getClientSubscriptions(CLIENT_ID).isEmpty());

        client.disconnect();
        client.close();
    }

    @Test
    public void givenNotExpiredSession_whenReconnectWithoutCleanStart_thenSessionPresentAndSubscriptionsKept() throws Throwable {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        options.setSessionExpiryInterval(60L);

        MqttClient client = new MqttClient(SERVER_URI + mqttPort, CLIENT_ID);
        client.connect(options);
        client.subscribe(new MqttSubscription[]{new MqttSubscription(MY_TOPIC, 1)}, new IMqttMessageListener[]{(topic, message) -> {
        }});
        client.disconnect();
        client.close();

        client = new MqttClient(SERVER_URI + mqttPort, CLIENT_ID);
        options.setCleanStart(false);
        IMqttToken token = client.connectWithResult(options);

        Assert.assertTrue(token.getSessionPresent());
        Assert.assertEquals(1, clientSubscriptionCache.getClientSubscriptions(CLIENT_ID).size());

        client.disconnect();
        client.close();

        // clean up so the next test starts without a stored session
        client = new MqttClient(SERVER_URI + mqttPort, CLIENT_ID);
        options.setCleanStart(true);
        options.setSessionExpiryInterval(0L);
        client.connect(options);
        client.disconnect();
        client.close();
    }
}
