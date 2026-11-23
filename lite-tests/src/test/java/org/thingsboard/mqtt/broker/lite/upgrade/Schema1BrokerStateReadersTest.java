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

import io.netty.handler.codec.mqtt.MqttProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.cache.Cache;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.CacheProperties;
import org.thingsboard.mqtt.broker.cache.CacheSpecs;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.common.data.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.dao.messages.DeviceQueueInfo;
import org.thingsboard.mqtt.broker.dao.messages.StoredDeviceMsg;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbBrokerStateStores;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMemoryBudget;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.rocksdb.devicemsg.RocksDbDeviceMsgQueueStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.disk.DiskSpaceMonitor;
import org.thingsboard.mqtt.broker.dao.rocksdb.disk.FreeSpaceProbe;
import org.thingsboard.mqtt.broker.dao.rocksdb.migration.Schema1To2Step;
import org.thingsboard.mqtt.broker.dao.store.BrokerStateStores;
import org.thingsboard.mqtt.broker.dao.store.KeyValueStore;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClientType;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.RegexMatchTarget;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.subscription.data.SubscriptionsSource;
import org.thingsboard.mqtt.broker.service.subscription.data.SubscriptionsSourceKey;
import org.thingsboard.mqtt.broker.standalone.cache.StandaloneCacheManager;
import org.thingsboard.mqtt.broker.standalone.device.StandaloneDeviceMsgService;
import org.thingsboard.mqtt.broker.standalone.state.StandaloneBlockedClientConsumerService;
import org.thingsboard.mqtt.broker.standalone.state.StandaloneClientSessionConsumer;
import org.thingsboard.mqtt.broker.standalone.state.StandaloneClientSubscriptionConsumer;
import org.thingsboard.mqtt.broker.standalone.state.StandaloneRetainedMsgConsumer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A copy of the schema 1 store fixture of dao-rocksdb, upgraded by this build and read through the readers the broker
 * loads its state with at startup: every broker-state family decodes to the values its README lists. The readers get
 * the stores read-only, so a reader that would write an entry back (a session stored as connected or under another
 * service id) fails the test instead of hiding a stored value.
 */
class Schema1BrokerStateReadersTest {

    private static final String FIXTURE_PATH = "tbmq.fixtures.schema-1.path";
    private static final String SERVICE_ID = "tbmq-lite";
    // what the fixture clients asked for, 50 years
    private static final int FIXTURE_EXPIRY_INTERVAL_SEC = 1576800000;
    private static final int SUBSCRIPTION_ID = 7;
    private static final int TTL_SEC = 2000000000;

    @TempDir
    static Path dir;

    private static RocksDbStore store;
    private static BrokerStateStores stores;

    @BeforeAll
    static void openAnUpgradedCopyOfTheFixture() throws IOException {
        String fixture = System.getProperty(FIXTURE_PATH);
        assertThat(fixture).as("system property %s (set by the surefire configuration of this module)", FIXTURE_PATH).isNotBlank();
        Path copy = Files.createDirectories(dir.resolve("store"));
        try (Stream<Path> files = Files.list(Path.of(fixture))) {
            for (Path file : files.toList()) {
                Files.copy(file, copy.resolve(file.getFileName()));
            }
        }
        StoreProperties properties = new StoreProperties();
        properties.setPath(copy.toString());
        properties.getBackups().setPath(dir.resolve("backups").toString());
        store = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0),
                new DiskSpaceMonitor(properties, path -> new FreeSpaceProbe.Space(Long.MAX_VALUE, Long.MAX_VALUE)),
                List.of(new Schema1To2Step()));
        read("the store (open and upgrade)", () -> {
            store.open();
            return null;
        });
        assertThat(store.meta().schemaVersion()).as("schema version after the upgrade").isEqualTo(RocksDbStore.SCHEMA_VERSION);
        stores = readOnly(new RocksDbBrokerStateStores(store));
    }

    @AfterAll
    static void closeTheStore() {
        if (store != null) {
            store.close();
        }
    }

    @Test
    void clientSessions() {
        ServiceInfoProvider serviceInfo = mock(ServiceInfoProvider.class);
        when(serviceInfo.getServiceId()).thenReturn(SERVICE_ID);

        Map<String, ClientSessionInfo> sessions = read("client_session through StandaloneClientSessionConsumer",
                () -> new StandaloneClientSessionConsumer(stores, serviceInfo).initLoad());

        assertThat(sessions).containsOnlyKeys("fx-dev-1", "fx-dev-2");
        ClientSessionInfo dev1 = sessions.get("fx-dev-1");
        assertThat(dev1.getClientId()).isEqualTo("fx-dev-1");
        assertThat(dev1.getType()).isEqualTo(ClientType.DEVICE);
        assertThat(dev1.isCleanStart()).as("Clean Start of fx-dev-1").isFalse();
        assertThat(dev1.getSessionExpiryInterval()).as("Session Expiry Interval of fx-dev-1").isEqualTo(FIXTURE_EXPIRY_INTERVAL_SEC);
        assertThat(dev1.isConnected()).as("connected of fx-dev-1").isFalse();
        assertThat(dev1.getDisconnectedAt()).as("disconnect time of fx-dev-1").isPositive();
        assertThat(dev1.getServiceId()).isEqualTo(SERVICE_ID);
        assertThat(dev1.getSessionId()).isNotNull();
        ClientSessionInfo dev2 = sessions.get("fx-dev-2");
        assertThat(dev2.getClientId()).isEqualTo("fx-dev-2");
        assertThat(dev2.getType()).isEqualTo(ClientType.DEVICE);
        assertThat(dev2.isCleanStart()).as("clean session of fx-dev-2").isFalse();
        assertThat(dev2.safeGetSessionExpiryInterval()).as("Session Expiry Interval of fx-dev-2").isZero();
        assertThat(dev2.isNotCleanSession()).as("persistent MQTT 3.1.1 session of fx-dev-2").isTrue();
        assertThat(dev2.isConnected()).as("connected of fx-dev-2").isFalse();
        assertThat(dev2.getDisconnectedAt()).as("disconnect time of fx-dev-2").isPositive();
        assertThat(dev2.getServiceId()).isEqualTo(SERVICE_ID);
        assertThat(dev2.getSessionId()).isNotNull();
    }

    @Test
    void clientSubscriptions() {
        Map<SubscriptionsSourceKey, Set<TopicSubscription>> subscriptions = read(
                "client_subscriptions through StandaloneClientSubscriptionConsumer",
                () -> new StandaloneClientSubscriptionConsumer(stores).initLoad());

        assertThat(subscriptions.keySet()).extracting(SubscriptionsSourceKey::getId).containsExactlyInAnyOrder("fx-dev-1", "fx-dev-2");
        assertThat(subscriptions.keySet()).extracting(SubscriptionsSourceKey::getSource).containsOnly(SubscriptionsSource.MQTT_CLIENT);
        Map<String, TopicSubscription> dev1 = byFilter(subscriptions.get(SubscriptionsSourceKey.newInstance("fx-dev-1")));
        assertThat(dev1).containsOnlyKeys("fx/a/#", "fx/b");
        assertSubscription(dev1.get("fx/a/#"), 1, null, SUBSCRIPTION_ID);
        assertSubscription(dev1.get("fx/b"), 2, null, -1);
        Map<String, TopicSubscription> dev2 = byFilter(subscriptions.get(SubscriptionsSourceKey.newInstance("fx-dev-2")));
        assertThat(dev2).containsOnlyKeys("fx/a/#", "fx/b", "fx/s");
        assertSubscription(dev2.get("fx/a/#"), 1, null, -1);
        assertSubscription(dev2.get("fx/b"), 2, null, -1);
        assertSubscription(dev2.get("fx/s"), 1, "fx-g", -1);
    }

    @Test
    void clientSessionAttributes() {
        CacheProperties cacheProperties = new CacheProperties();
        cacheProperties.setCachePrefix("");
        cacheProperties.setSpecs(Map.of(
                CacheConstants.CLIENT_MQTT_VERSION_CACHE, new CacheSpecs(),
                CacheConstants.CLIENT_SESSION_CREDENTIALS_CACHE, new CacheSpecs()));
        StandaloneCacheManager cacheManager = StandaloneCacheManager.create(cacheProperties, stores.clientSessionAttributes(),
                100, System::nanoTime);
        Cache versions = cacheManager.getCache(CacheConstants.CLIENT_MQTT_VERSION_CACHE);
        Cache credentials = cacheManager.getCache(CacheConstants.CLIENT_SESSION_CREDENTIALS_CACHE);

        Map<String, String> attributes = read("client_session_attr through StandaloneCacheManager", () -> Map.of(
                "version fx-dev-1", versions.get("fx-dev-1", String.class),
                "version fx-dev-2", versions.get("fx-dev-2", String.class),
                "credentials fx-dev-1", credentials.get("fx-dev-1", String.class),
                "credentials fx-dev-2", credentials.get("fx-dev-2", String.class)));

        assertThat(attributes).containsExactlyInAnyOrderEntriesOf(Map.of(
                "version fx-dev-1", "MQTT_5",
                "version fx-dev-2", "MQTT_3_1_1",
                "credentials fx-dev-1", "fx-dev-1-credentials",
                "credentials fx-dev-2", "fx-dev-2-credentials"));
    }

    @Test
    void retainedMessages() {
        Map<String, RetainedMsg> retained = read("retained_msg through StandaloneRetainedMsgConsumer",
                () -> new StandaloneRetainedMsgConsumer(stores).initLoad());

        assertThat(retained).containsOnlyKeys("fx/r/1", "fx/r/2");
        RetainedMsg r1 = retained.get("fx/r/1");
        assertThat(r1.getTopic()).isEqualTo("fx/r/1");
        assertThat(utf8(r1.getPayload())).isEqualTo("r1");
        assertThat(r1.getQos()).isEqualTo(1);
        assertThat(msgExpiryInterval(r1.getProperties())).as("Message Expiry Interval of fx/r/1").isNull();
        assertThat(r1.getCreatedTime()).isPositive();
        RetainedMsg r2 = retained.get("fx/r/2");
        assertThat(r2.getTopic()).isEqualTo("fx/r/2");
        assertThat(utf8(r2.getPayload())).isEqualTo("r2");
        assertThat(r2.getQos()).isEqualTo(1);
        assertThat(msgExpiryInterval(r2.getProperties())).as("Message Expiry Interval of fx/r/2").isEqualTo(FIXTURE_EXPIRY_INTERVAL_SEC);
        assertThat(r2.getCreatedTime()).isPositive();
    }

    @Test
    void blockedClients() {
        Map<String, BlockedClient> blocked = read("blocked_client through StandaloneBlockedClientConsumerService",
                () -> new StandaloneBlockedClientConsumerService(stores).initLoad());

        assertThat(blocked).containsOnlyKeys("clientId:fx-blocked", "regex:fx-bad-.*:byID");
        BlockedClient byId = blocked.get("clientId:fx-blocked");
        assertThat(byId.getType()).isEqualTo(BlockedClientType.CLIENT_ID);
        assertThat(byId.getValue()).isEqualTo("fx-blocked");
        assertThat(byId.getExpirationTime()).as("expiration time of the block of fx-blocked").isZero();
        assertThat(byId.getKey()).isEqualTo("clientId:fx-blocked");
        BlockedClient byRegex = blocked.get("regex:fx-bad-.*:byID");
        assertThat(byRegex.getType()).isEqualTo(BlockedClientType.REGEX);
        assertThat(byRegex.getValue()).isEqualTo("fx-bad-.*");
        assertThat(byRegex.getRegexMatchTarget()).isEqualTo(RegexMatchTarget.BY_CLIENT_ID);
        assertThat(byRegex.getExpirationTime()).as("expiration time of the block of fx-bad-.*").isZero();
        assertThat(byRegex.getKey()).isEqualTo("regex:fx-bad-.*:byID");
    }

    @Test
    void theDeviceQueueOfFxDev1() {
        RocksDbDeviceMsgQueueStore queues = new RocksDbDeviceMsgQueueStore(store, 100, TTL_SEC, Clock.systemUTC());

        DeviceQueueInfo info = read("device_msg_meta of fx-dev-1", () -> queues.info("fx-dev-1"));
        List<StoredDeviceMsg> entries = read("device_msg of fx-dev-1", () -> queues.read("fx-dev-1", 0, 100));
        List<DevicePublishMsg> msgs = read("device_msg of fx-dev-1 through StandaloneDeviceMsgService",
                () -> new StandaloneDeviceMsgService(queues).findPersistedMessages("fx-dev-1").toCompletableFuture().join());

        assertThat(info).isEqualTo(new DeviceQueueInfo(2, 7, 7, 6, 4));
        assertThat(entries).extracting(StoredDeviceMsg::sequence).containsExactly(2L, 3L, 4L, 5L, 6L, 7L);
        assertThat(entries).extracting(StoredDeviceMsg::packetId).containsExactly(2, 3, 4, 5, 6, 7);
        assertThat(entries).extracting(StoredDeviceMsg::packetType).containsExactly(PersistedPacketType.PUBLISH,
                PersistedPacketType.PUBLISH, PersistedPacketType.PUBREL, PersistedPacketType.PUBLISH,
                PersistedPacketType.PUBLISH, PersistedPacketType.PUBLISH);
        assertThat(entries).extracting(StoredDeviceMsg::sent).containsExactly(true, true, true, false, false, false);
        assertThat(msgs).hasSize(6);
        assertMsg(msgs.get(0), "fx-dev-1", 2, "fx/a/1", "a2", 1, null, List.of(SUBSCRIPTION_ID), Map.of());
        assertMsg(msgs.get(1), "fx-dev-1", 3, "fx/a/1", "a3", 1, null, List.of(SUBSCRIPTION_ID), Map.of());
        assertMsg(msgs.get(2), "fx-dev-1", 4, "fx/b", "b1", 2, null, List.of(), Map.of());
        assertMsg(msgs.get(3), "fx-dev-1", 5, "fx/a/1", "a4", 1, null, List.of(SUBSCRIPTION_ID), Map.of());
        assertMsg(msgs.get(4), "fx-dev-1", 6, "fx/a/1", "a5", 1, null, List.of(SUBSCRIPTION_ID), Map.of());
        assertMsg(msgs.get(5), "fx-dev-1", 7, "fx/b", "b2", 2, FIXTURE_EXPIRY_INTERVAL_SEC, List.of(), Map.of("k", "v"));
        assertThat(msgs.get(2).getPacketType()).isEqualTo(PersistedPacketType.PUBREL);
        assertThat(entries.get(5).msgExpiresAtMs()).as("message expiry time of b2 for fx-dev-1").isPositive();
        assertThat(entries.subList(0, 5)).extracting(StoredDeviceMsg::msgExpiresAtMs).containsOnly(0L);
    }

    @Test
    void theDeviceQueueOfFxDev2() {
        RocksDbDeviceMsgQueueStore queues = new RocksDbDeviceMsgQueueStore(store, 100, TTL_SEC, Clock.systemUTC());

        DeviceQueueInfo info = read("device_msg_meta of fx-dev-2", () -> queues.info("fx-dev-2"));
        List<StoredDeviceMsg> entries = read("device_msg of fx-dev-2", () -> queues.read("fx-dev-2", 0, 100));
        List<DevicePublishMsg> msgs = read("device_msg of fx-dev-2 through StandaloneDeviceMsgService",
                () -> new StandaloneDeviceMsgService(queues).findPersistedMessages("fx-dev-2").toCompletableFuture().join());

        assertThat(info).isEqualTo(new DeviceQueueInfo(1, 7, 7, 7, 0));
        assertThat(entries).extracting(StoredDeviceMsg::sequence).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        assertThat(entries).extracting(StoredDeviceMsg::packetType).containsOnly(PersistedPacketType.PUBLISH);
        assertThat(entries).extracting(StoredDeviceMsg::sent).containsOnly(false);
        assertThat(msgs).hasSize(7);
        assertMsg(msgs.get(0), "fx-dev-2", 1, "fx/a/1", "a1", 1, null, List.of(), Map.of());
        assertMsg(msgs.get(1), "fx-dev-2", 2, "fx/a/1", "a2", 1, null, List.of(), Map.of());
        assertMsg(msgs.get(2), "fx-dev-2", 3, "fx/a/1", "a3", 1, null, List.of(), Map.of());
        assertMsg(msgs.get(3), "fx-dev-2", 4, "fx/b", "b1", 2, null, List.of(), Map.of());
        assertMsg(msgs.get(4), "fx-dev-2", 5, "fx/a/1", "a4", 1, null, List.of(), Map.of());
        assertMsg(msgs.get(5), "fx-dev-2", 6, "fx/a/1", "a5", 1, null, List.of(), Map.of());
        assertMsg(msgs.get(6), "fx-dev-2", 7, "fx/b", "b2", 2, FIXTURE_EXPIRY_INTERVAL_SEC, List.of(), Map.of("k", "v"));
    }

    private static void assertSubscription(TopicSubscription subscription, int qos, String shareName, int subscriptionId) {
        String name = subscription.getTopicFilter();
        assertThat(subscription.getQos()).as("QoS of %s", name).isEqualTo(qos);
        assertThat(subscription.getShareName()).as("shared group of %s", name).isEqualTo(shareName);
        assertThat(subscription.getSubscriptionId()).as("subscription identifier of %s", name).isEqualTo(subscriptionId);
        assertThat(subscription.getOptions()).as("options of %s", name).isEqualTo(SubscriptionOptions.newInstance());
    }

    private static void assertMsg(DevicePublishMsg msg, String clientId, int packetId, String topic, String payload, int qos,
                                  Integer msgExpiryInterval, List<Integer> subscriptionIds, Map<String, String> userProperties) {
        String name = clientId + " packet " + packetId;
        assertThat(msg.getClientId()).as("client of %s", name).isEqualTo(clientId);
        assertThat(msg.getPacketId()).as("packet id of %s", name).isEqualTo(packetId);
        assertThat(msg.getTopicName()).as("topic of %s", name).isEqualTo(topic);
        assertThat(utf8(msg.getPayload())).as("payload of %s", name).isEqualTo(payload);
        assertThat(msg.getQos()).as("QoS of %s", name).isEqualTo(qos);
        assertThat(msg.getMsgExpiryInterval()).as("Message Expiry Interval of %s", name).isEqualTo(msgExpiryInterval);
        assertThat(msg.getTime()).as("time of %s", name).isPositive();
        assertThat(msg.getProperties().getProperties(BrokerConstants.SUBSCRIPTION_IDENTIFIER_PROP_ID))
                .as("subscription identifiers of %s", name)
                .extracting(property -> (Integer) property.value())
                .containsExactlyElementsOf(subscriptionIds);
        MqttProperties.UserProperties stored = (MqttProperties.UserProperties) msg.getProperties()
                .getProperty(MqttProperties.MqttPropertyType.USER_PROPERTY.value());
        Map<String, String> actualUserProperties = stored == null ? Map.of()
                : stored.value().stream().collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
        assertThat(actualUserProperties).as("user properties of %s", name).isEqualTo(userProperties);
    }

    private static Integer msgExpiryInterval(MqttProperties properties) {
        MqttProperties.MqttProperty<?> property = properties.getProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID);
        return property == null ? null : (Integer) property.value();
    }

    private static Map<String, TopicSubscription> byFilter(Set<TopicSubscription> subscriptions) {
        assertThat(subscriptions).isNotNull();
        return subscriptions.stream().collect(Collectors.toMap(TopicSubscription::getTopicFilter, subscription -> subscription));
    }

    private static String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Runs a read of the fixture; a failure names what was read.
     */
    private static <T> T read(String what, Callable<T> read) {
        try {
            return read.call();
        } catch (Exception e) {
            throw new AssertionError("Cannot read " + what + " of the schema 1 fixture: " + e, e);
        }
    }

    private static BrokerStateStores readOnly(BrokerStateStores stores) {
        return new BrokerStateStores() {
            @Override
            public KeyValueStore clientSessions() {
                return new ReadOnlyStore("client_session", stores.clientSessions());
            }

            @Override
            public KeyValueStore clientSubscriptions() {
                return new ReadOnlyStore("client_subscriptions", stores.clientSubscriptions());
            }

            @Override
            public KeyValueStore retainedMessages() {
                return new ReadOnlyStore("retained_msg", stores.retainedMessages());
            }

            @Override
            public KeyValueStore blockedClients() {
                return new ReadOnlyStore("blocked_client", stores.blockedClients());
            }

            @Override
            public KeyValueStore clientSessionAttributes() {
                return new ReadOnlyStore("client_session_attr", stores.clientSessionAttributes());
            }
        };
    }

    /**
     * A store whose writes fail the test: a reader that writes back changed what the fixture holds.
     */
    private record ReadOnlyStore(String family, KeyValueStore delegate) implements KeyValueStore {

        @Override
        public void put(String key, byte[] value) {
            throw new AssertionError("The reader wrote " + family + " entry '" + key + "' back while loading the fixture");
        }

        @Override
        public byte[] get(String key) {
            return delegate.get(key);
        }

        @Override
        public void delete(String key) {
            throw new AssertionError("The reader deleted " + family + " entry '" + key + "' while loading the fixture");
        }

        @Override
        public void forEach(BiConsumer<String, byte[]> consumer) {
            delegate.forEach(consumer);
        }

        @Override
        public int retainKeys(Predicate<String> keep) {
            throw new AssertionError("The reader deleted " + family + " entries while loading the fixture");
        }

    }

}
