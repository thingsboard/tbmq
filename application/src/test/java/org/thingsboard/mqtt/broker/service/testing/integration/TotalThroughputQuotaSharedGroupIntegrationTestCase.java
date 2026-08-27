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
import org.thingsboard.mqtt.broker.common.data.ApplicationSharedSubscription;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.client.application.ApplicationSharedSubscriptionService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.ApplicationTopicService;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * A shared subscription group costs ONE outgoing packet per message however many members it has, charged at the
 * publish - and delivering those stored messages to the group is then free.
 * <p>
 * The budget does the proving, not the assertions alone: at capacity 30 with {@value #MSG_COUNT} messages the three
 * candidate charging models are distinguishable. Per unique shared TOPIC (the shipped model) is 2 per message = 24
 * tokens and fits, so nothing may be dropped; per group MEMBER would be 4 per message = 48; charging the shared
 * pack again at delivery would be 3 per message = 36. Only the first fits, so any droppedMsgs here means the wrong
 * model. Block size 4 means the node can leave at most 3 unused, a worst case of 27 against the bucket's 30.
 * <p>
 * Each message reaches exactly ONE member, so the delivered total is asserted as an equality: charged once must
 * also mean delivered once.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaSharedGroupIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.config=30:600",
        "mqtt.rate-limits.total.block-size=4"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaSharedGroupIntegrationTestCase extends AbstractTotalThroughputQuotaIntegrationTest {

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
        // a persistent APPLICATION client may only join a provisioned shared subscription, so the entity and its
        // Kafka topic must exist before the SUBSCRIBE or the broker answers 0x80
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

        double droppedBefore = droppedMsgs();
        double processedBefore = publishesProcessed();

        MqttClient pub = new MqttClient(SERVER_URI + mqttPort, PUBLISHING_CLIENT);
        MqttConnectOptions pubOptions = new MqttConnectOptions();
        pubOptions.setUserName(PUB_USERNAME);
        pub.connect(pubOptions);
        for (int i = 0; i < MSG_COUNT; i++) {              // 2 charges each: incoming plus ONE charge for the group
            pub.publish(TOPIC, ("msg_" + i).getBytes(), 1, false);
            pacePublish(processedBefore, i);
        }
        pub.disconnect();
        pub.close();

        awaitReceived("shared group delivery", totalReceived, MSG_COUNT);
        // an equality, so it also rules out a message reaching MORE than one member - which is why the count has to
        // be held at the expected value for a while rather than read the moment it first gets there
        assertNothingMoreArrives("each message must reach exactly one group member, and none may be lost",
                totalReceived, MSG_COUNT);
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
}
