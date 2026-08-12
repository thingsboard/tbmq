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

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.common.data.ApplicationSharedSubscription;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.client.application.ApplicationSharedSubscriptionService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationTopicService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DROPPED_MSGS;

/**
 * Proves the two shared-subscription contracts of prepaid charging: a shared subscription group costs ONE outgoing
 * packet per message however many members it has, charged when the publish is processed - and the delivery of those
 * stored messages to the group is then free.
 * <p>
 * The budget is what does the proving here, not the assertions alone. All three candidate charging models are
 * distinguishable at capacity 30 with {@value #MSG_COUNT} messages:
 * <ul>
 *     <li>per unique shared TOPIC, charged once at the fan-out - the shipped model: 1 ingress + 1 group = 2 per message,
 *     24 tokens. Fits, so nothing may be dropped and every message must arrive;</li>
 *     <li>per group MEMBER at the fan-out: 1 + 3 = 4 per message, 48 tokens. Would exhaust the budget and report the
 *     excess as droppedMsgs;</li>
 *     <li>per topic at the fan-out PLUS a second charge when the shared pack is delivered - the model this redesign
 *     removed: 3 per message, 36 tokens. Also would not fit.</li>
 * </ul>
 * Ledger arithmetic (capacity 30, full refill every 600 s = 0.05 tokens/s - negligible over the run, block size 4,
 * lease return disabled): the 24 tokens the correct model needs are drawn in blocks of 4, so the node can leave at most
 * 3 unused in its local pool - a worst case of 27 debited against the shared bucket's 30.
 * <p>
 * Each message goes to exactly ONE member of the group, so the delivered total across all members is the message count.
 * That is asserted as an equality: a group charged once must not be delivered once per member either.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaSharedGroupIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.enabled=true",
        "mqtt.rate-limits.total.config=30:600",
        "mqtt.rate-limits.total.block-size=4",
        "mqtt.rate-limits.total.lease-return-ms=600000"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaSharedGroupIntegrationTestCase extends AbstractPubSubIntegrationTest {

    private static final String TOPIC = "quota/shared";
    private static final String SHARED_FILTER = "$share/qg/quota/shared";
    private static final int MEMBERS = 3;
    static final int MSG_COUNT = 12;
    private static final String APP_USERNAME = "quotaSharedApp";
    private static final String PUB_USERNAME = "quotaSharedPub";
    private static final String PUBLISHING_CLIENT = "quota_shared_pub";

    @Autowired
    private MqttClientCredentialsService credentialsService;
    @Autowired
    private ApplicationSharedSubscriptionService applicationSharedSubscriptionService;
    @Autowired
    private ApplicationTopicService applicationTopicService;
    @Autowired
    private MeterRegistry meterRegistry;

    private final List<MqttClientCredentials> credentials = new ArrayList<>();
    private final List<MqttClient> members = new ArrayList<>();

    @Before
    public void beforeTest() throws Exception {
        for (int i = 0; i < MEMBERS; i++) {
            credentials.add(credentialsService.saveCredentials(
                    TestUtils.createApplicationClientCredentials(memberClientId(i), APP_USERNAME + i)));
        }
        credentials.add(credentialsService.saveCredentials(
                TestUtils.createDeviceClientCredentials(PUBLISHING_CLIENT, PUB_USERNAME)));
        // a persistent APPLICATION client may only join a shared subscription that has been provisioned, so the entity
        // and its Kafka topic have to exist before the SUBSCRIBE or the broker answers 0x80
        ApplicationSharedSubscription subscription = applicationSharedSubscriptionService.findSharedSubscriptionByTopic(TOPIC);
        if (subscription == null) {
            ApplicationSharedSubscription toSave = new ApplicationSharedSubscription();
            toSave.setName("quotaShared");
            toSave.setTopicFilter(TOPIC);
            toSave.setPartitions(1);
            applicationTopicService.createSharedTopic(applicationSharedSubscriptionService.saveSharedSubscription(toSave));
        }
        enableBasicProvider();
    }

    @After
    public void clear() throws Exception {
        for (int i = 0; i < members.size(); i++) {
            // reconnects with cleanSession=true, which wipes the persistent session and the group's leftovers
            TestUtils.clearPersistedClient(members.get(i), memberOptions(i, true));
        }
        for (MqttClientCredentials credential : credentials) {
            credentialsService.deleteCredentials(credential.getId());
        }
    }

    @Test
    public void givenSharedGroup_whenPublishing_thenChargedOncePerMsgAndDeliveredUnmetered() throws Throwable {
        AtomicInteger totalReceived = new AtomicInteger();

        for (int i = 0; i < MEMBERS; i++) {
            MqttClient member = new MqttClient(SERVER_URI + mqttPort, memberClientId(i));
            members.add(member);
            // a per-subscription listener is useless for a shared subscription: Paho routes an incoming message by
            // matching the registered filter against the topic NAME, and "$share/qg/quota/shared" never matches
            // "quota/shared", so the message would be silently discarded by the client. Hence the general callback.
            member.setCallback(countingCallback(totalReceived));
            member.connect(memberOptions(i, false));
            member.subscribe(SHARED_FILTER, 1);
        }

        // the historical droppedMsgs counter is zeroed by the reporting scheduler; this Micrometer counter is monotonic
        double droppedBefore = droppedMsgs();

        MqttClient pub = new MqttClient(SERVER_URI + mqttPort, PUBLISHING_CLIENT);
        MqttConnectOptions pubOptions = new MqttConnectOptions();
        pubOptions.setUserName(PUB_USERNAME);
        pub.connect(pubOptions);
        for (int i = 0; i < MSG_COUNT; i++) {              // 2 charges each: incoming plus ONE charge for the group
            pub.publish(TOPIC, ("msg_" + i).getBytes(), 1, false);
            Thread.sleep(50);                             // pacing so each async draw lands before the next charge
        }
        pub.disconnect();
        pub.close();

        Awaitility.await("shared group delivery")
                .atMost(30, TimeUnit.SECONDS)
                .until(totalReceived::get, greaterThanOrEqualTo(MSG_COUNT));
        Thread.sleep(1000);
        assertEquals("each message must reach exactly one group member, and none may be lost",
                MSG_COUNT, totalReceived.get());
        // 24 tokens of the 30 available. Charging per member (48) or charging the pack delivery a second time (36)
        // would both have exhausted the budget and reported the excess here
        assertEquals("a shared group costs ONE outgoing packet per message, and its delivery costs nothing more",
                droppedBefore, droppedMsgs(), 0.0);
    }

    private MqttCallback countingCallback(AtomicInteger counter) {
        return new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                counter.incrementAndGet();
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        };
    }

    private String memberClientId(int index) {
        return "quota_shared_member_" + index;
    }

    private MqttConnectOptions memberOptions(int index, boolean cleanSession) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(cleanSession);
        options.setUserName(APP_USERNAME + index);
        return options;
    }

    private double droppedMsgs() {
        return meterRegistry.get(DROPPED_MSGS).counter().count();
    }
}
