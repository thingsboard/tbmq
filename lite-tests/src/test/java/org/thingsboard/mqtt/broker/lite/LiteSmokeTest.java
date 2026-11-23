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
package org.thingsboard.mqtt.broker.lite;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.ThingsboardMqttBrokerApplication;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.common.util.edition.ClusterComponent;
import org.thingsboard.mqtt.broker.common.util.edition.TbmqEdition;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.client.application.ApplicationSessionCtxService;
import org.thingsboard.mqtt.broker.dao.client.application.ApplicationSharedSubscriptionService;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.dao.event.EventService;
import org.thingsboard.mqtt.broker.dao.integration.IntegrationService;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.mqtt.auth.MqttAuthProviderManagerService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.standalone.application.NoOpApplicationSharedSubscriptionService;
import org.thingsboard.mqtt.broker.testsupport.EditionTest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The first full Lite context: the edition, the absence of cluster beans and the basic MQTT paths, end to end.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ThingsboardMqttBrokerApplication.class)
@ActiveProfiles("test")
@EditionTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class LiteSmokeTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @ClassRule
    public static TempStorePathRule store = new TempStorePathRule();

    @Autowired
    private ApplicationContext context;
    @Autowired
    private ClientSessionCache clientSessionCache;
    @Autowired
    private MqttClientCredentialsService credentialsService;
    @Autowired
    private MqttAuthProviderService authProviderService;
    @Autowired
    private MqttAuthProviderManagerService authProviderManagerService;

    @Value("${listener.tcp.bind_port}")
    private int mqttPort;

    private final List<Object> clients = new ArrayList<>();

    @After
    public void closeClients() {
        clients.forEach(LiteTestClients::closeQuietly);
        clients.clear();
    }

    @Test
    public void theContextReportsLiteAndHasNoClusterBeans() {
        assertThat(context.getBean(TbmqEdition.class)).isEqualTo(TbmqEdition.LITE);
        assertThat(context.getBeansWithAnnotation(ClusterComponent.class)).isEmpty();
        assertThat(context.getBean(ServiceInfoProvider.class).getServiceId()).isEqualTo("tbmq-lite");
    }

    @Test
    public void theContextHasNoIntegrationEventOrApplicationPersistenceService() {
        assertThat(context.getBeanNamesForType(IntegrationService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(EventService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(ApplicationSessionCtxService.class)).isEmpty();
        assertThat(context.getBean(ApplicationSharedSubscriptionService.class)).isInstanceOf(NoOpApplicationSharedSubscriptionService.class);
    }

    @Test
    public void aQos1PublishIsAcknowledgedAndReachesASubscriber() throws Exception {
        List<MqttMessage> received = new CopyOnWriteArrayList<>();
        MqttClient subscriber = v5Client("smoke-a-sub");
        subscriber.connect(cleanStart());
        subscriber.subscribe(new MqttSubscription[]{new MqttSubscription("smoke/a", 1)},
                new IMqttMessageListener[]{(topic, message) -> received.add(message)});

        MqttAsyncClient publisher = new MqttAsyncClient(serverUri(), "smoke-a-pub",
                new MemoryPersistence());
        clients.add(publisher);
        publisher.connect(cleanStart()).waitForCompletion(TIMEOUT.toMillis());
        IMqttToken token = publisher.publish("smoke/a", payload("a"), 1, false);
        token.waitForCompletion(TIMEOUT.toMillis());

        assertThat(token.isComplete()).isTrue();
        assertThat(token.getException()).isNull();
        assertThat(token.getReasonCodes()).containsExactly(0);
        await().atMost(TIMEOUT).until(() -> !received.isEmpty());
        assertThat(received).singleElement().satisfies(message -> {
            assertThat(message.getQos()).isEqualTo(1);
            assertThat(new String(message.getPayload(), StandardCharsets.UTF_8)).isEqualTo("a");
        });
        publisher.disconnect().waitForCompletion(TIMEOUT.toMillis());
        subscriber.disconnect();
    }

    @Test
    public void aPersistentSubscriberGetsWhatWasPublishedWhileItWasAway() throws Exception {
        List<String> received = new CopyOnWriteArrayList<>();
        org.eclipse.paho.client.mqttv3.MqttClient p1 =
                new org.eclipse.paho.client.mqttv3.MqttClient(serverUri(), "p1", new org.eclipse.paho.client.mqttv3.persist.MemoryPersistence());
        clients.add(p1);
        p1.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
            }

            @Override
            public void messageArrived(String topic, org.eclipse.paho.client.mqttv3.MqttMessage message) {
                received.add(new String(message.getPayload(), StandardCharsets.UTF_8));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });
        MqttConnectOptions persistent = new MqttConnectOptions();
        persistent.setCleanSession(false);
        p1.connect(persistent);
        p1.subscribe("smoke/b", 1);
        p1.disconnect();
        await().atMost(TIMEOUT).until(() -> isDisconnected("p1"));

        MqttClient publisher = v5Client("smoke-b-pub");
        publisher.connect(cleanStart());
        for (int i = 0; i < 20; i++) {
            publisher.publish("smoke/b", payload(String.valueOf(i)), 1, false);
        }
        publisher.disconnect();

        p1.connect(persistent);

        await().atMost(TIMEOUT).until(() -> received.size() >= 20);
        assertThat(received).containsExactlyElementsOf(IntStream.range(0, 20).mapToObj(String::valueOf).toList());
        p1.disconnect();
    }

    @Test
    public void aRetainedMessageIsDeliveredToALateSubscriber() throws Exception {
        MqttClient publisher = v5Client("smoke-c-pub");
        publisher.connect(cleanStart());
        publisher.publish("smoke/c", payload("retained"), 1, true);
        publisher.disconnect();

        List<MqttMessage> received = new CopyOnWriteArrayList<>();
        MqttClient subscriber = v5Client("smoke-c-sub");
        subscriber.connect(cleanStart());
        subscriber.subscribe(new MqttSubscription[]{new MqttSubscription("smoke/c", 1)},
                new IMqttMessageListener[]{(topic, message) -> received.add(message)});

        await().atMost(TIMEOUT).until(() -> !received.isEmpty());
        assertThat(received).singleElement().satisfies(message -> {
            assertThat(message.isRetained()).isTrue();
            assertThat(new String(message.getPayload(), StandardCharsets.UTF_8)).isEqualTo("retained");
        });
        subscriber.disconnect();
    }

    @Test
    public void anApplicationCredentialConnectsAsDevice() throws Exception {
        String clientId = "smoke-app";
        MqttClientCredentials credentials = credentialsService.saveCredentials(applicationCredentials(clientId, "smoke-app-user"));
        MqttAuthProvider basicProvider = authProviderService.getAuthProviderByType(MqttAuthProviderType.MQTT_BASIC).orElseThrow();
        boolean basicWasEnabled = basicProvider.isEnabled();
        if (!basicWasEnabled) {
            authProviderManagerService.enableAuthProvider(basicProvider.getId());
        }
        try {
            MqttClient client = v5Client(clientId);
            MqttConnectionOptions options = cleanStart();
            options.setUserName("smoke-app-user");
            client.connect(options);

            await().atMost(TIMEOUT).until(() -> isConnected(clientId));
            assertThat(clientSessionCache.getClientSessionInfo(clientId).getType()).isEqualTo(ClientType.DEVICE);
            client.disconnect();
        } finally {
            if (!basicWasEnabled) {
                authProviderManagerService.disableAuthProvider(basicProvider.getId());
            }
            credentialsService.deleteCredentials(credentials.getId());
        }
    }

    private boolean isConnected(String clientId) {
        ClientSessionInfo info = clientSessionCache.getClientSessionInfo(clientId);
        return info != null && info.isConnected();
    }

    private boolean isDisconnected(String clientId) {
        ClientSessionInfo info = clientSessionCache.getClientSessionInfo(clientId);
        return info != null && !info.isConnected();
    }

    private MqttClient v5Client(String clientId) throws Exception {
        MqttClient client = new MqttClient(serverUri(), clientId, new MemoryPersistence());
        clients.add(client);
        return client;
    }

    private static MqttConnectionOptions cleanStart() {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(true);
        return options;
    }

    private static MqttClientCredentials applicationCredentials(String clientId, String username) {
        MqttClientCredentials credentials = new MqttClientCredentials();
        credentials.setName("smoke-" + clientId);
        credentials.setClientType(ClientType.APPLICATION);
        credentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        credentials.setCredentialsValue(JacksonUtil.toString(BasicMqttCredentials.newInstance(clientId, username, null, List.of(".*"))));
        return credentials;
    }

    private String serverUri() {
        return "tcp://localhost:" + mqttPort;
    }

    private static byte[] payload(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

}
