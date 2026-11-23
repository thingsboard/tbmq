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
package org.thingsboard.mqtt.broker.dao.rocksdb.migration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thingsboard.mqtt.broker.common.data.GenericClientSessionCtx;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMemoryBudget;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMeta;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreWriteClass;
import org.thingsboard.mqtt.broker.dao.rocksdb.client.RocksDbGenericClientSessionCtxDao;
import org.thingsboard.mqtt.broker.dao.rocksdb.disk.TestDiskSpaceMonitors;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityIndex;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityKeys;
import org.thingsboard.mqtt.broker.dao.rocksdb.entity.RocksEntityStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.timeseries.RocksDbKeyDictionaryDao;
import org.thingsboard.mqtt.broker.dao.rocksdb.timeseries.TsFixtures;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.thingsboard.mqtt.broker.dao.rocksdb.migration.RawStores.bytes;

class Schema1To2StepTest {

    private static final String JWT_KEY = "jwt_signing_key";
    private static final String SEEDED = "seeded";
    private static final List<StoreColumnFamily> BROKER_STATE = List.of(StoreColumnFamily.CLIENT_SESSION,
            StoreColumnFamily.CLIENT_SUBSCRIPTIONS, StoreColumnFamily.RETAINED_MSG, StoreColumnFamily.BLOCKED_CLIENT,
            StoreColumnFamily.CLIENT_SESSION_ATTR, StoreColumnFamily.DEVICE_MSG, StoreColumnFamily.DEVICE_MSG_META);

    @TempDir
    Path dir;

    private final List<RocksDbStore> opened = new ArrayList<>();

    @AfterEach
    void closeAll() {
        opened.forEach(RocksDbStore::close);
        opened.clear();
    }

    @Test
    void itBringsSchemaOneToTwo() {
        assertThat(new Schema1To2Step().fromVersion()).isEqualTo(1);
    }

    @Test
    void itAddsTheKeyAndTouchesNoBrokerStateFamily() {
        Path path = dir.resolve("store");
        StoreMeta before = open(path, List.of()).meta();
        for (StoreColumnFamily cf : BROKER_STATE) {
            opened.get(0).put(StoreWriteClass.NEW_DATA, cf,
                    deviceShapedKey(cf.ordinal()), bytes("value of " + cf.columnFamilyName()));
        }
        closeAll();
        // the meta that the schema-1 code left: no key
        RawStores.deleteMeta(path, JWT_KEY);
        RawStores.putMeta(path, "schema_version", new byte[]{0, 0, 0, 1});
        Map<StoreColumnFamily, List<String>> beforeMigration = RawStores.snapshot(path);

        RocksDbStore store = open(path, List.of(new Schema1To2Step()));

        assertThat(store.meta()).isEqualTo(new StoreMeta(2, before.product(), before.installId()));
        assertThat(store.get(StoreColumnFamily.META, bytes(JWT_KEY))).hasSize(64);
        closeAll();
        Map<StoreColumnFamily, List<String>> afterMigration = RawStores.snapshot(path);
        for (StoreColumnFamily cf : StoreColumnFamily.values()) {
            if (cf != StoreColumnFamily.META) {
                assertThat(afterMigration.get(cf)).as(cf.columnFamilyName()).isEqualTo(beforeMigration.get(cf));
            }
        }
        for (StoreColumnFamily cf : BROKER_STATE) {
            assertThat(afterMigration.get(cf)).as(cf.columnFamilyName()).hasSize(1);
        }
        assertThat(RawStores.getMeta(path, "product")).isEqualTo(bytes(before.product()));
        assertThat(RawStores.getMeta(path, "install_id")).isEqualTo(bytes(before.installId().toString()));
        assertThat(RawStores.getMeta(path, "schema_version")).isEqualTo(new byte[]{0, 0, 0, 2});
        assertThat(RawStores.getMeta(path, SEEDED)).isNull();
        // the checkpoint holds the store as the schema-1 code left it
        Path checkpoint = dir.resolve("backups").resolve("pre-upgrade-1-2");
        assertThat(RawStores.getMeta(checkpoint, "schema_version")).isEqualTo(new byte[]{0, 0, 0, 1});
        assertThat(RawStores.getMeta(checkpoint, JWT_KEY)).isNull();
        assertThat(RawStores.snapshot(checkpoint).get(StoreColumnFamily.CLIENT_SESSION)).isEqualTo(beforeMigration.get(StoreColumnFamily.CLIENT_SESSION));
    }

    @Test
    void aSchemaOneStoreWithEntitiesAndAKeyButNoSeedMarkerOpens() {
        Path path = dir.resolve("store");
        RocksDbStore writer = open(path, List.of());
        writeWhatDevelopmentBuildsWrote(writer);
        closeAll();
        // those builds wrote schema 1 and the key, and never seeded
        RawStores.putMeta(path, "schema_version", new byte[]{0, 0, 0, 1});
        byte[] key = RawStores.getMeta(path, JWT_KEY);
        assertThat(key).hasSize(64);
        assertThat(RawStores.getMeta(path, SEEDED)).isNull();
        Map<StoreColumnFamily, List<String>> beforeMigration = RawStores.snapshot(path);
        assertThat(beforeMigration.get(StoreColumnFamily.ENTITY)).hasSize(2);
        assertThat(beforeMigration.get(StoreColumnFamily.ENTITY_INDEX))
                .anySatisfy(entry -> assertThat(entry).startsWith(String.format("%02x", EntityIndex.TS_KEY_DICTIONARY.code())));
        for (StoreColumnFamily cf : Set.of(StoreColumnFamily.SESSION_CTX, StoreColumnFamily.TS, StoreColumnFamily.TS_LATEST)) {
            assertThat(beforeMigration.get(cf)).as(cf.columnFamilyName()).isNotEmpty();
        }

        RocksDbStore store = open(path, List.of(new Schema1To2Step()));

        assertThat(store.meta().schemaVersion()).isEqualTo(2);
        assertThat(store.get(StoreColumnFamily.META, bytes(JWT_KEY))).isEqualTo(key);
        assertThat(store.get(StoreColumnFamily.META, bytes(SEEDED))).isNull();
        assertThat(new RocksEntityStore(store).findByUnique(EntityDescriptors.USER, EntityIndex.USER_EMAIL, "a@b.c")).isNotNull();
        closeAll();
        Map<StoreColumnFamily, List<String>> afterMigration = RawStores.snapshot(path);
        for (StoreColumnFamily cf : StoreColumnFamily.values()) {
            if (cf != StoreColumnFamily.META) {
                assertThat(afterMigration.get(cf)).as(cf.columnFamilyName()).isEqualTo(beforeMigration.get(cf));
            }
        }
    }

    /**
     * Entity documents with their indexes, time series keys in the dictionary, time series and latest values, and
     * session contexts, each written with the layout the store code uses.
     */
    private static void writeWhatDevelopmentBuildsWrote(RocksDbStore store) {
        RocksEntityStore entities = new RocksEntityStore(store);
        User user = new User(UUID.fromString("01020304-0506-0708-090a-0b0c0d0e0fd1"));
        user.setCreatedTime(1L);
        user.setEmail("a@b.c");
        entities.save(EntityDescriptors.USER, user);
        MqttClientCredentials credentials = new MqttClientCredentials(UUID.fromString("01020304-0506-0708-090a-0b0c0d0e0fc1"));
        credentials.setCreatedTime(1L);
        credentials.setCredentialsId("cid-1");
        credentials.setName("n1");
        credentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        entities.save(EntityDescriptors.MQTT_CLIENT_CREDENTIALS, credentials);

        RocksDbKeyDictionaryDao dictionary = new RocksDbKeyDictionaryDao(store);
        int incoming = dictionary.getOrSaveKeyId("incomingMsgs");
        int outgoing = dictionary.getOrSaveKeyId("outgoingMsgs");
        assertThat(store.get(StoreColumnFamily.ENTITY_INDEX, EntityKeys.uniqueIndexKey(EntityIndex.TS_KEY_DICTIONARY, "incomingMsgs")))
                .isNotNull();

        String node = "tbmq-node-1";
        for (long ts = 60_000; ts <= 180_000; ts += 60_000) {
            store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.TS,
                    TsFixtures.tsKey(node, incoming, ts), TsFixtures.tsValue(new LongDataEntry("incomingMsgs", ts / 1000)));
        }
        store.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.TS_LATEST,
                TsFixtures.latestKey(node, outgoing),
                TsFixtures.latestValue(new BasicTsKvEntry(180_000, new LongDataEntry("outgoingMsgs", 7L))));

        RocksDbGenericClientSessionCtxDao sessionContexts = new RocksDbGenericClientSessionCtxDao(store);
        sessionContexts.save(new GenericClientSessionCtx("client-1", 1_000L, Set.of(1, 2)));
        sessionContexts.save(new GenericClientSessionCtx("client-2", 2_000L, Set.of()));
    }

    private static byte[] deviceShapedKey(long prefix) {
        // an 8-byte queue prefix and a sequence: the device_msg layout, harmless in the other families
        return ByteBuffer.allocate(12).putLong(prefix).putInt(1).array();
    }

    private RocksDbStore open(Path path, List<StoreMigrationStep> steps) {
        StoreProperties properties = new StoreProperties();
        properties.setPath(path.toString());
        properties.getBackups().setPath(dir.resolve("backups").toString());
        RocksDbStore store = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0),
                TestDiskSpaceMonitors.roomy(properties), steps);
        opened.add(store);
        store.open();
        return store;
    }

}
