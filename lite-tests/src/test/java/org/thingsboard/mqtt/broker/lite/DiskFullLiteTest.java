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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.controller.AbstractWebTest;
import org.thingsboard.mqtt.broker.controller.MqttPublishController;
import org.thingsboard.mqtt.broker.dao.client.unauthorized.UnauthorizedClientService;
import org.thingsboard.mqtt.broker.dao.rocksdb.disk.DiskSpaceMonitor;
import org.thingsboard.mqtt.broker.dao.rocksdb.disk.FreeSpaceProbe;
import org.thingsboard.mqtt.broker.dto.PayloadEncoding;
import org.thingsboard.mqtt.broker.dto.RestPublishRequest;
import org.thingsboard.mqtt.broker.lite.spec.RawMqttClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerService;

import java.io.EOFException;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A Lite broker whose store volume is below the free-space watermark: it refuses what adds data (a QoS 1 publish to a
 * persistent subscriber, a new persistent session, a management write) with a clear code, and keeps serving the rest
 * (non-persistent traffic, a returning persistent subscriber draining its queue, a sysadmin login, an authentication
 * refusal). The test replaces the free-space probe with one it fills and empties, and checks the monitor after each
 * change, so the store, the connect check and the health all see the same disk. Every case ends with the disk empty
 * again, before the base class logs in after it. The watermark is the default one (the other Lite test contexts turn
 * it off), so the full and the roomy disk of the probe fall on either side of it.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = DiskFullLiteTest.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "store.disk.min-free-percent=5",
        "store.disk.min-free-bytes=268435456"
})
@RunWith(SpringRunner.class)
public class DiskFullLiteTest extends AbstractWebTest {

    private static final long GIB = 1L << 30;
    private static final FreeSpaceProbe.Space ROOMY = new FreeSpaceProbe.Space(50 * GIB, 100 * GIB);
    private static final FreeSpaceProbe.Space FULL = new FreeSpaceProbe.Space(GIB / 1024, 100 * GIB);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int SUCCESS = 0x00;
    private static final int QUOTA_EXCEEDED = 0x97;

    private static final AtomicBoolean DISK_FULL = new AtomicBoolean();

    @Autowired
    private DiskSpaceMonitor diskSpaceMonitor;
    @Autowired
    private ClientSessionCache clientSessionCache;
    @Autowired
    private UnauthorizedClientService unauthorizedClientService;
    @Autowired
    private RetainedMsgListenerService retainedMsgListenerService;

    private final List<RawMqttClient> clients = new ArrayList<>();

    /**
     * The probe of this context only: the context is its own (this class is its configuration) and is closed after
     * the class.
     */
    @Bean
    @Primary
    static FreeSpaceProbe diskFullTestProbe() {
        return path -> DISK_FULL.get() ? FULL : ROOMY;
    }

    @Before
    public void startWithRoom() {
        setDiskFull(false);
    }

    @After
    public void freeTheDisk() {
        // before the base class logs in and resets the auth providers, which are writes
        setDiskFull(false);
        clients.forEach(RawMqttClient::close);
        clients.clear();
    }

    @Test
    public void aQos1PublishToAPersistentSubscriberIsRefusedWithQuotaExceededOnMqtt5() throws Exception {
        offlinePersistentSubscriber("disk-full-sub-5", "disk-full/5");
        RawMqttClient publisher = track(RawMqttClient.connectClean(mqttPort, "disk-full-pub-5", MqttVersion.MQTT_5));
        setDiskFull(true);

        assertThat(publisher.publishQos1("disk-full/5", 1, "refused", null)).as("PUBACK reason code").isEqualTo(QUOTA_EXCEEDED);
    }

    @Test
    public void aQos1PublishToAPersistentSubscriberClosesTheConnectionOnMqtt311() throws Exception {
        offlinePersistentSubscriber("disk-full-sub-311", "disk-full/311");
        RawMqttClient publisher = track(RawMqttClient.connectClean(mqttPort, "disk-full-pub-311", MqttVersion.MQTT_3_1_1));
        setDiskFull(true);

        publisher.publish("disk-full/311", 1, 1, "refused", null);

        assertThatThrownBy(() -> publisher.next(TIMEOUT)).isInstanceOf(EOFException.class);
    }

    @Test
    public void aRetainedQos1PublishIsRefusedWithQuotaExceededOnMqtt5() throws Exception {
        RawMqttClient publisher = track(RawMqttClient.connectClean(mqttPort, "disk-full-retain-pub", MqttVersion.MQTT_5));
        setDiskFull(true);

        assertThat(publisher.publishRetainedQos1("disk-full/retained", 1, "refused")).as("PUBACK reason code").isEqualTo(QUOTA_EXCEEDED);
        assertThat(retainedMsgListenerService.getRetainedMsgForTopic("disk-full/retained")).isNull();
    }

    @Test
    public void aRestPublishToAPersistentSubscriberAndARetainedRestPublishAreRefusedWith507() throws Exception {
        offlinePersistentSubscriber("disk-full-rest-sub", "disk-full/rest");
        setDiskFull(true);
        loginSysAdmin();

        for (RestPublishRequest request : List.of(restRequest("disk-full/rest", false), restRequest("disk-full/rest-retained", true))) {
            JsonNode error = readResponse(doPostAsync(MqttPublishController.PUBLISH_PATH, request, TIMEOUT.toMillis())
                    .andExpect(status().isInsufficientStorage()), JsonNode.class);

            assertThat(error.get("status").asInt()).as(request.getTopic()).isEqualTo(507);
            assertThat(error.get("message").asText()).as(request.getTopic()).startsWith("Not enough free disk space for the store at ");
        }
        assertThat(retainedMsgListenerService.getRetainedMsgForTopic("disk-full/rest-retained")).isNull();
    }

    @Test
    public void aQos0PublishToANonPersistentSubscriberStillArrives() throws Exception {
        RawMqttClient subscriber = track(RawMqttClient.connectClean(mqttPort, "disk-full-clean-sub", MqttVersion.MQTT_5));
        subscriber.subscribe("disk-full/qos0", 0);
        setDiskFull(true);
        RawMqttClient publisher = track(RawMqttClient.connectClean(mqttPort, "disk-full-clean-pub", MqttVersion.MQTT_5));

        publisher.publish("disk-full/qos0", 0, 0, "arrives", null);

        RawMqttClient.Packet publish = subscriber.next(TIMEOUT);
        assertThat(publish.payload()).isEqualTo("arrives");
    }

    @Test
    public void aNewPersistentSessionIsRefusedAndANonPersistentOneConnects() throws Exception {
        setDiskFull(true);

        assertThat(RawMqttClient.connectForReturnCode(mqttPort, "disk-full-new-5", MqttVersion.MQTT_5, false, 300, null, null))
                .isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_QUOTA_EXCEEDED);
        assertThat(RawMqttClient.connectForReturnCode(mqttPort, "disk-full-new-311", MqttVersion.MQTT_3_1_1, false, 0, null, null))
                .isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
        assertThat(clientSessionCache.getClientSessionInfo("disk-full-new-5")).isNull();
        assertThat(clientSessionCache.getClientSessionInfo("disk-full-new-311")).isNull();

        track(RawMqttClient.connectClean(mqttPort, "disk-full-new-clean", MqttVersion.MQTT_5));
    }

    @Test
    public void anOfflinePersistentSubscriberReconnectsAndDrainsItsQueue() throws Exception {
        offlinePersistentSubscriber("disk-full-drain", "disk-full/drain");
        RawMqttClient publisher = track(RawMqttClient.connectClean(mqttPort, "disk-full-drain-pub", MqttVersion.MQTT_5));
        assertThat(publisher.publishQos1("disk-full/drain", 1, "queued", null)).isEqualTo(SUCCESS);
        setDiskFull(true);

        RawMqttClient subscriber = track(RawMqttClient.connectPersistent(mqttPort, "disk-full-drain", MqttVersion.MQTT_5));

        assertThat(subscriber.sessionPresent()).isTrue();
        RawMqttClient.Packet publish = subscriber.next(TIMEOUT);
        assertThat(publish.payload()).isEqualTo("queued");
        subscriber.pubAck(publish.packetId());
        // the acknowledgement is a delete, which goes on: the message is not sent again on the next connection
        disconnect(subscriber, "disk-full-drain");
        RawMqttClient again = track(RawMqttClient.connectPersistent(mqttPort, "disk-full-drain", MqttVersion.MQTT_5));
        assertThat(again.poll(Duration.ofSeconds(1))).isEmpty();
    }

    @Test
    public void managementWritesAreRefusedWith507AndTheSysadminStillLogsIn() throws Exception {
        setDiskFull(true);

        loginSysAdmin();
        MqttClientCredentials credentials = basicCredentials("disk-full-creds", "disk-full-user", "secret");
        JsonNode error = readResponse(doPost("/api/mqtt/client/credentials", credentials).andExpect(status().isInsufficientStorage()),
                JsonNode.class);

        assertThat(error.get("status").asInt()).isEqualTo(507);
        assertThat(error.get("message").asText()).startsWith("Not enough free disk space for the store at ");
        assertThat(mqttClientCredentialsService.findCredentialsByName("disk-full-creds")).isNull();
    }

    @Test
    public void aConnectWithWrongCredentialsStillGetsItsRefusal() throws Exception {
        enableBasicProvider();
        setDiskFull(true);

        assertThat(RawMqttClient.connectForReturnCode(mqttPort, "disk-full-intruder", MqttVersion.MQTT_5, true, 0, "nobody", "wrong"))
                .isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED_5);
        // the record of the refused client is new data: skipped while the disk is full
        assertThat(unauthorizedClientService.findUnauthorizedClient("disk-full-intruder")).isEmpty();

        setDiskFull(false);
        assertThat(RawMqttClient.connectForReturnCode(mqttPort, "disk-full-intruder", MqttVersion.MQTT_5, true, 0, "nobody", "wrong"))
                .isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED_5);
        await("unauthorized client stored once there is room").atMost(TIMEOUT)
                .until(() -> unauthorizedClientService.findUnauthorizedClient("disk-full-intruder").isPresent());
    }

    @Test
    public void publishesSucceedOnceSpaceIsBack() throws Exception {
        offlinePersistentSubscriber("disk-full-back", "disk-full/back");
        RawMqttClient publisher = track(RawMqttClient.connectClean(mqttPort, "disk-full-back-pub", MqttVersion.MQTT_5));
        setDiskFull(true);
        assertThat(publisher.publishQos1("disk-full/back", 1, "refused", null)).isEqualTo(QUOTA_EXCEEDED);

        setDiskFull(false);

        assertThat(publisher.publishQos1("disk-full/back", 2, "stored", null)).isEqualTo(SUCCESS);
        RawMqttClient back = track(RawMqttClient.connectPersistent(mqttPort, "disk-full-back", MqttVersion.MQTT_5));
        RawMqttClient.Packet publish = back.next(TIMEOUT);
        assertThat(publish.payload()).isEqualTo("stored");
        back.pubAck(publish.packetId());
    }

    private void setDiskFull(boolean full) {
        DISK_FULL.set(full);
        diskSpaceMonitor.check();
        assertThat(diskSpaceMonitor.belowWatermark()).as("below the watermark").isEqualTo(full);
    }

    /**
     * An MQTT 5 client with a session that outlives its connection, subscribed at QoS 1 and disconnected, so each
     * publish to the topic must be stored for it.
     */
    private void offlinePersistentSubscriber(String clientId, String topicFilter) throws IOException {
        RawMqttClient subscriber = track(RawMqttClient.connectPersistent(mqttPort, clientId, MqttVersion.MQTT_5));
        subscriber.subscribe(topicFilter, 1);
        disconnect(subscriber, clientId);
    }

    private void disconnect(RawMqttClient client, String clientId) throws IOException {
        client.disconnect();
        await("session of " + clientId + " disconnected").atMost(TIMEOUT).until(() -> {
            ClientSessionInfo session = clientSessionCache.getClientSessionInfo(clientId);
            return session != null && !session.isConnected();
        });
    }

    private static RestPublishRequest restRequest(String topic, boolean retain) {
        RestPublishRequest request = new RestPublishRequest();
        request.setTopic(topic);
        request.setPayload(new TextNode("refused"));
        request.setPayloadEncoding(PayloadEncoding.TEXT);
        request.setQos(1);
        request.setRetain(retain);
        return request;
    }

    private static MqttClientCredentials basicCredentials(String name, String username, String password) {
        MqttClientCredentials credentials = new MqttClientCredentials();
        credentials.setName(name);
        credentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        credentials.setCredentialsValue(JacksonUtil.toString(BasicMqttCredentials.newInstance(null, username, password, null)));
        return credentials;
    }

    private RawMqttClient track(RawMqttClient client) {
        clients.add(client);
        return client;
    }

}
