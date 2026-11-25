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
package org.thingsboard.mqtt.broker.lite.upgrade;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.FileSystemUtils;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.controller.AbstractControllerTest;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsDao;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderDao;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsDao;
import org.thingsboard.mqtt.broker.dao.store.StoreSeedState;
import org.thingsboard.mqtt.broker.dao.user.UserDao;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketConnectionDao;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketSubscriptionDao;
import org.thingsboard.mqtt.broker.lite.spec.RawMqttClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The broker started on a copy of the schema 1 store fixture of dao-rocksdb (its README lists the content): the store
 * is migrated to schema 2, the management defaults are seeded, and the broker state the old code wrote serves the
 * clients as before. Every expiry in the fixture is decades away and the session expiry TTL is the one the fixture was
 * written with, so no assertion depends on the day the test runs, other than the Message Expiry Interval left, which is
 * checked against the expiry time the fixture stores.
 */
@TestPropertySource(properties = {
        "lite.test.system-data.enabled=false",
        // the session cleanup applies the current TTL to fx-dev-2, which has no Session Expiry Interval
        "mqtt.client-session-expiry.ttl=2000000000",
        "mqtt.client-session-expiry.max-expiry-interval=2000000000",
        "mqtt.persistent-session.device.persisted-messages.ttl=2000000000",
})
public class Schema1UpgradeLiteTest extends AbstractControllerTest {

    /**
     * Copies the fixture into the empty directory the class rule of the base class points {@code store.path} at, and
     * gives the pre-upgrade checkpoint a directory of its own. The highest order makes it the innermost class rule
     * (JUnit runs a rule of a higher order inside one of a lower order), so it runs after every class rule of the base
     * classes, whatever their order.
     */
    @ClassRule(order = Integer.MAX_VALUE)
    public static final Schema1Store schema1Store = new Schema1Store();

    private static final UUID FIXTURE_INSTALL_ID = UUID.fromString("f61b7502-608d-4b6a-a5c0-3ad396336e04");
    // what the fixture clients asked for, 50 years
    private static final int FIXTURE_EXPIRY_INTERVAL_SEC = 1576800000;
    // the Message Expiry times the fixture stores (2076-11-10): fx/r/2 its created time 1795428998716 plus the interval,
    // b2 the msgExpiresAtMs of its device_msg entries, the created time 1795428997477 plus the interval
    private static final long R2_EXPIRES_AT_MS = 3372228998716L;
    private static final long B2_EXPIRES_AT_MS = 3372228997477L;
    // the broker rounds the time left to whole seconds and the packet travels between the two clock reads
    private static final long EXPIRY_TOLERANCE_SEC = 5;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration QUIET = Duration.ofSeconds(1);

    @Autowired
    private RocksDbStore store;
    @Autowired
    private StoreSeedState storeSeedState;
    @Autowired
    private UserService userService;
    @Autowired
    private AdminSettingsDao adminSettingsDao;
    @Autowired
    private MqttAuthProviderDao mqttAuthProviderDao;
    @Autowired
    private UserDao userDao;
    @Autowired
    private MqttClientCredentialsDao mqttClientCredentialsDao;
    @Autowired
    private WebSocketConnectionDao webSocketConnectionDao;
    @Autowired
    private WebSocketSubscriptionDao webSocketSubscriptionDao;

    private final List<RawMqttClient> clients = new ArrayList<>();
    private UUID disabledBasicProviderId;

    /**
     * The base class disables every auth provider before each test; these tests check the providers as seeded.
     */
    @Override
    public void beforeTest() {
    }

    /**
     * The seeded sysadmin is on the default password and marked to change it, so the base class could not sign in as it.
     * The mark is cleared first, as the password change would do, keeping the password the shared steps use.
     */
    @Override
    @Before
    public void setupWebTest() throws Exception {
        clearPasswordChangeMark();
        super.setupWebTest();
    }

    @Override
    @After
    public void teardownWebTest() throws Exception {
        clearPasswordChangeMark();
        super.teardownWebTest();
    }

    private void clearPasswordChangeMark() {
        UUID sysadminId = userService.findUserByEmail(SYS_ADMIN_EMAIL).getId();
        if (userService.isPasswordChangeRequired(sysadminId)) {
            userService.replaceUserCredentials(userService.findUserCredentialsByUserId(sysadminId));
        }
    }

    @After
    public void restore() throws Exception {
        clients.forEach(RawMqttClient::close);
        clients.clear();
        if (disabledBasicProviderId != null) {
            loginSysAdmin();
            doPost("/api/mqtt/auth/provider/" + disabledBasicProviderId + "/enable").andExpect(status().isOk());
            disabledBasicProviderId = null;
        }
    }

    @Test
    public void theDefaultsAreSeeded() {
        // the store of the fixture, migrated, and not a fresh one
        assertThat(store.meta().schemaVersion()).isEqualTo(RocksDbStore.SCHEMA_VERSION);
        assertThat(store.meta().installId()).isEqualTo(FIXTURE_INSTALL_ID);
        assertThat(storeSeedState.isSeeded()).isTrue();

        assertThat(adminSettingsDao.find()).extracting(AdminSettings::getKey)
                .containsExactlyInAnyOrder("general", "mail", "connectivity", "websocket", "mqttAuthorization");
        assertThat(mqttAuthProviderDao.find()).extracting(MqttAuthProvider::getType)
                .containsExactlyInAnyOrder(MqttAuthProviderType.values());
        assertThat(mqttAuthProviderDao.find()).filteredOn(MqttAuthProvider::isEnabled)
                .extracting(MqttAuthProvider::getType)
                .containsExactly(MqttAuthProviderType.MQTT_BASIC);
        List<User> users = userDao.find();
        assertThat(users).extracting(User::getEmail).containsExactly("sysadmin@thingsboard.org");
        assertThat(mqttClientCredentialsDao.find()).extracting(MqttClientCredentials::getName)
                .containsExactly(BrokerConstants.WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_NAME);
        assertThat(webSocketConnectionDao.find()).singleElement().satisfies(connection -> {
            assertThat(connection.getName()).isEqualTo(BrokerConstants.WEB_SOCKET_DEFAULT_CONNECTION_NAME);
            assertThat(connection.getUserId()).isEqualTo(users.get(0).getId());
            assertThat(webSocketSubscriptionDao.findAllByWebSocketConnectionId(connection.getId(), new PageLink(10)).getData())
                    .singleElement()
                    .satisfies(subscription -> assertThat(subscription.getConfiguration().getTopicFilter())
                            .isEqualTo(BrokerConstants.WEB_SOCKET_DEFAULT_SUBSCRIPTION_TOPIC_FILTER));
        });
    }

    @Test
    public void aPersistentClientGetsItsQueueBack() throws Exception {
        disableBasicThroughRest();

        // fx-dev-1 received 2 and 3 without acknowledging them, and 4 up to the PUBREL; 5 to 7 were never sent
        RawMqttClient dev1 = track(RawMqttClient.connectPersistent(mqttPort, "fx-dev-1", FIXTURE_EXPIRY_INTERVAL_SEC));
        assertThat(dev1.sessionPresent()).as("session present for fx-dev-1").isTrue();
        assertPublish(dev1.next(TIMEOUT), 2, true, 1, "fx/a/1", "a2");
        assertPublish(dev1.next(TIMEOUT), 3, true, 1, "fx/a/1", "a3");
        dev1.expect(MqttMessageType.PUBREL, 4);
        assertPublish(dev1.next(TIMEOUT), 5, false, 1, "fx/a/1", "a4");
        assertPublish(dev1.next(TIMEOUT), 6, false, 1, "fx/a/1", "a5");
        RawMqttClient.Packet b2 = dev1.next(TIMEOUT);
        assertPublish(b2, 7, false, 2, "fx/b", "b2");
        assertExpiryLeft(b2, B2_EXPIRES_AT_MS);
        assertThat(dev1.poll(QUIET)).as("a packet after the queue of fx-dev-1").isEmpty();

        // fx-dev-2 (MQTT 3.1.1) was offline for all seven
        RawMqttClient dev2 = track(RawMqttClient.connectPersistent(mqttPort, "fx-dev-2", MqttVersion.MQTT_3_1_1));
        assertThat(dev2.sessionPresent()).as("session present for fx-dev-2").isTrue();
        assertPublish(dev2.next(TIMEOUT), 1, false, 1, "fx/a/1", "a1");
        assertPublish(dev2.next(TIMEOUT), 2, false, 1, "fx/a/1", "a2");
        assertPublish(dev2.next(TIMEOUT), 3, false, 1, "fx/a/1", "a3");
        assertPublish(dev2.next(TIMEOUT), 4, false, 2, "fx/b", "b1");
        assertPublish(dev2.next(TIMEOUT), 5, false, 1, "fx/a/1", "a4");
        assertPublish(dev2.next(TIMEOUT), 6, false, 1, "fx/a/1", "a5");
        assertPublish(dev2.next(TIMEOUT), 7, false, 2, "fx/b", "b2");
        assertThat(dev2.poll(QUIET)).as("a packet after the queue of fx-dev-2").isEmpty();
    }

    @Test
    public void retainedMessagesAndTheirExpiryAreKept() throws Exception {
        disableBasicThroughRest();
        RawMqttClient reader = track(RawMqttClient.connectClean(mqttPort, "fx-retained-reader", MqttVersion.MQTT_5));

        reader.subscribe("fx/r/#", 1);

        Map<String, RawMqttClient.Packet> retained = Stream.of(reader.next(TIMEOUT), reader.next(TIMEOUT))
                .collect(Collectors.toMap(RawMqttClient.Packet::topic, Function.identity()));
        assertThat(retained).containsOnlyKeys("fx/r/1", "fx/r/2");
        RawMqttClient.Packet r1 = retained.get("fx/r/1");
        assertThat(r1.type()).isEqualTo(MqttMessageType.PUBLISH);
        assertThat(r1.payload()).isEqualTo("r1");
        assertThat(r1.qos()).isEqualTo(1);
        assertThat(r1.msgExpiryInterval()).as("Message Expiry Interval of fx/r/1").isNull();
        RawMqttClient.Packet r2 = retained.get("fx/r/2");
        assertThat(r2.type()).isEqualTo(MqttMessageType.PUBLISH);
        assertThat(r2.payload()).isEqualTo("r2");
        assertThat(r2.qos()).isEqualTo(1);
        assertExpiryLeft(r2, R2_EXPIRES_AT_MS);
    }

    @Test
    public void blockedClientsAreStillRefused() throws Exception {
        // the block by client id and the one by a client id pattern
        for (String clientId : List.of("fx-blocked", "fx-bad-1")) {
            assertThat(RawMqttClient.connectForReturnCode(mqttPort, clientId, MqttVersion.MQTT_5, true, 0, null, null))
                    .as("CONNACK of %s", clientId)
                    .isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_BANNED);
        }
    }

    /**
     * The fixture clients have no credentials in the store, so they connect only without Basic, which is disabled as
     * the sysadmin would do it. Waits until a client without credentials is accepted.
     */
    private void disableBasicThroughRest() throws Exception {
        loginSysAdmin();
        MqttAuthProvider basic = doGet("/api/mqtt/auth/provider/type/" + MqttAuthProviderType.MQTT_BASIC, MqttAuthProvider.class);
        doPost("/api/mqtt/auth/provider/" + basic.getId() + "/disable").andExpect(status().isOk());
        disabledBasicProviderId = basic.getId();
        await("a client without credentials accepted").atMost(TIMEOUT).until(() ->
                RawMqttClient.connectForReturnCode(mqttPort, "fx-probe", MqttVersion.MQTT_5, true, 0, null, null)
                        == MqttConnectReturnCode.CONNECTION_ACCEPTED);
    }

    /**
     * The Message Expiry Interval of {@code packet} is the time left until the expiry time the fixture stores: an
     * upgrade or a load that restarted the expiry clock would send almost the full interval instead.
     */
    private static void assertExpiryLeft(RawMqttClient.Packet packet, long storedExpiresAtMs) {
        long expected = (storedExpiresAtMs - System.currentTimeMillis()) / 1000;
        assertThat(packet.msgExpiryInterval()).as("Message Expiry Interval left of %s", packet)
                .isNotNull()
                .satisfies(left -> assertThat(left.longValue()).isBetween(expected - EXPIRY_TOLERANCE_SEC, expected + EXPIRY_TOLERANCE_SEC));
    }

    private static void assertPublish(RawMqttClient.Packet packet, int packetId, boolean dup, int qos, String topic, String payload) {
        assertThat(packet.type()).as("type of %s", packet).isEqualTo(MqttMessageType.PUBLISH);
        assertThat(packet.packetId()).as("packet id of %s", packet).isEqualTo(packetId);
        assertThat(packet.dup()).as("DUP of %s", packet).isEqualTo(dup);
        assertThat(packet.qos()).as("QoS of %s", packet).isEqualTo(qos);
        assertThat(packet.topic()).as("topic of %s", packet).isEqualTo(topic);
        assertThat(packet.payload()).as("payload of %s", packet).isEqualTo(payload);
    }

    private RawMqttClient track(RawMqttClient client) {
        clients.add(client);
        return client;
    }

    /**
     * Copies the fixture, which the build names in {@value #FIXTURE_PATH}, into the empty store directory and sets
     * {@value #BACKUPS_PATH} to a temporary directory; restores the property and deletes that directory afterwards.
     * The store directory is deleted by the rule that created it.
     */
    public static final class Schema1Store extends ExternalResource {

        static final String FIXTURE_PATH = "tbmq.fixtures.schema-1.path";
        static final String STORE_PATH = "store.path";
        static final String BACKUPS_PATH = "store.backups.path";

        private Path backups;
        private String previousBackups;

        @Override
        protected void before() throws IOException {
            String fixture = System.getProperty(FIXTURE_PATH);
            assertThat(fixture).as("system property %s (set by the lite-tests surefire configuration)", FIXTURE_PATH).isNotBlank();
            String storePath = System.getProperty(STORE_PATH);
            assertThat(storePath).as("system property %s, set by the class rule of the base class", STORE_PATH).isNotBlank();
            Path store = Path.of(storePath);
            try (Stream<Path> existing = Files.list(store)) {
                assertThat(existing).as("the store directory %s before the fixture is copied in", store).isEmpty();
            }
            try (Stream<Path> files = Files.list(Path.of(fixture))) {
                for (Path file : files.toList()) {
                    Files.copy(file, store.resolve(file.getFileName()));
                }
            }
            backups = Files.createTempDirectory("tbmq-store-backups");
            previousBackups = System.setProperty(BACKUPS_PATH, backups.toString());
        }

        @Override
        protected void after() {
            if (previousBackups == null) {
                System.clearProperty(BACKUPS_PATH);
            } else {
                System.setProperty(BACKUPS_PATH, previousBackups);
            }
            try {
                FileSystemUtils.deleteRecursively(backups);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to delete the backups directory " + backups, e);
            }
        }

    }

}
