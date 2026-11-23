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

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMemoryBudget;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMeta;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.rocksdb.backup.CheckpointInfo;
import org.thingsboard.mqtt.broker.dao.rocksdb.backup.StoreCheckpoints;
import org.thingsboard.mqtt.broker.dao.rocksdb.devicemsg.DeviceMsgEntryCodec;
import org.thingsboard.mqtt.broker.dao.rocksdb.disk.TestDiskSpaceMonitors;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A copy of the committed schema 1 store, written by the schema 1 code, opened with this build: the migration brings it
 * to schema 2, keeps every broker-state entry byte for byte, and leaves a checkpoint of the store as it was.
 */
class Schema1FixtureUpgradeTest {

    private static final String FIXTURE = "/fixtures/schema-1/db";
    private static final UUID FIXTURE_INSTALL_ID = UUID.fromString("f61b7502-608d-4b6a-a5c0-3ad396336e04");
    // the broker-state families, every family of the fixture but meta, with the number of entries its README lists
    private static final Map<StoreColumnFamily, Integer> BROKER_STATE = brokerState();
    private static final int RETAINED_VERSION = 1;
    private static final int RETAINED_FLAG_HAS_INTERVAL = 1;
    private static final int RETAINED_HEADER_SIZE = 6;
    private static final int RETAINED_CREATED_TIME_FIELD = 5;
    private static final int DEVICE_MSG_QUEUE_KEY_OFFSET = Long.BYTES + Integer.BYTES;
    // ClientSessionInfoProto.sessionInfo, SessionInfoProto.sessionExpiryInterval, BlockedClientProto.expirationTime
    private static final int CLIENT_SESSION_SESSION_INFO_FIELD = 2;
    private static final int SESSION_INFO_SESSION_EXPIRY_INTERVAL_FIELD = 7;
    private static final int BLOCKED_CLIENT_EXPIRATION_TIME_FIELD = 2;
    private static final long TEN_YEARS_SEC = ChronoUnit.YEARS.getDuration().multipliedBy(10).toSeconds();

    @TempDir
    Path dir;

    private Path pristine;
    private Path upgraded;
    private Path backups;
    private RocksDbStore store;

    @BeforeEach
    void copyTheFixture() throws IOException, URISyntaxException {
        Path fixture = fixturePath();
        // the committed files are never opened: one copy stays as the old code left it, the other is upgraded
        pristine = copy(fixture, dir.resolve("pristine"));
        upgraded = copy(fixture, dir.resolve("store"));
        backups = dir.resolve("backups");
    }

    @AfterEach
    void closeTheStore() {
        if (store != null) {
            store.close();
        }
    }

    @Test
    void everyBrokerStateEntryReadsBackTheSame() {
        open().close();

        Map<String, List<String>> before = readOnly(pristine);
        Map<String, List<String>> after = readOnly(upgraded);
        List<String> fixtureFamilies = new ArrayList<>(List.of("default", StoreColumnFamily.META.columnFamilyName()));
        BROKER_STATE.keySet().forEach(cf -> fixtureFamilies.add(cf.columnFamilyName()));
        assertThat(before.keySet()).as("families of the fixture").containsExactlyInAnyOrderElementsOf(fixtureFamilies);
        BROKER_STATE.forEach((cf, count) -> {
            assertThat(before.get(cf.columnFamilyName())).as("%s in the fixture", cf.columnFamilyName()).hasSize(count);
            assertThat(after.get(cf.columnFamilyName())).as(cf.columnFamilyName()).isEqualTo(before.get(cf.columnFamilyName()));
        });
    }

    @Test
    void theMetaIsAtVersionTwoWithAKey() {
        RocksDbStore opened = open();

        assertThat(opened.meta()).isEqualTo(new StoreMeta(2, RocksDbStore.PRODUCT, FIXTURE_INSTALL_ID));
        assertThat(opened.get(StoreColumnFamily.META, utf8("schema_version"))).isEqualTo(new byte[]{0, 0, 0, 2});
        assertThat(opened.get(StoreColumnFamily.META, utf8("jwt_signing_key"))).hasSize(64);
        assertThat(opened.get(StoreColumnFamily.META, utf8("product"))).isEqualTo(utf8("LITE"));
        assertThat(opened.get(StoreColumnFamily.META, utf8("install_id"))).isEqualTo(utf8(FIXTURE_INSTALL_ID.toString()));
        // the seed is the application's: the store migration does not write it
        assertThat(opened.get(StoreColumnFamily.META, utf8("seeded"))).isNull();
        // removed by the last bump of the upgrade
        assertThat(opened.get(StoreColumnFamily.META, utf8("upgrade_checkpoint"))).isNull();
    }

    @Test
    void failedStartsResumeWithTheCheckpointOfTheFirstAndAFinishedUpgradeRemovesItsRecord() {
        StoreMigrationStep partlyWritingThenFailing = new StoreMigrationStep() {
            @Override
            public int fromVersion() {
                return 1;
            }

            @Override
            public void apply(MigrationContext ctx) {
                ctx.put(StoreColumnFamily.CLIENT_SESSION, utf8("partly-migrated"), utf8("by a failed step"));
                throw new IllegalStateException("boom");
            }
        };
        for (int start = 0; start < 3; start++) {
            assertThatThrownBy(() -> open(List.of(partlyWritingThenFailing)))
                    .isInstanceOf(StoreException.class)
                    .hasMessageContaining("boom");
            store = null;
            // the 8-byte big-endian latest sequence number of the store, written again by the failed step, then the
            // directory name of the checkpoint in UTF-8
            long latest = withReadOnly(upgraded, (db, handles) -> db.getLatestSequenceNumber());
            byte[] name = utf8("pre-upgrade-1-2");
            assertThat(meta(upgraded, "upgrade_checkpoint"))
                    .isEqualTo(ByteBuffer.allocate(Long.BYTES + name.length).putLong(latest).put(name).array());
            assertThat(meta(upgraded, "schema_version")).isEqualTo(new byte[]{0, 0, 0, 1});
        }

        RocksDbStore opened = open();

        assertThat(opened.meta().schemaVersion()).isEqualTo(2);
        assertThat(opened.get(StoreColumnFamily.META, utf8("upgrade_checkpoint"))).isNull();
        assertThat(opened.get(StoreColumnFamily.CLIENT_SESSION, utf8("partly-migrated"))).isEqualTo(utf8("by a failed step"));
        Path checkpoint = backups.resolve("pre-upgrade-1-2");
        assertThat(StoreCheckpoints.list(backups)).extracting(CheckpointInfo::path).containsExactly(checkpoint);
        // the store before any step wrote, without the record of the upgrade
        Map<String, List<String>> fixture = readOnly(pristine);
        Map<String, List<String>> checkpointed = readOnly(checkpoint);
        assertThat(checkpointed.get("meta")).isEqualTo(fixture.get("meta"));
        for (StoreColumnFamily cf : BROKER_STATE.keySet()) {
            assertThat(checkpointed.get(cf.columnFamilyName())).as(cf.columnFamilyName()).isEqualTo(fixture.get(cf.columnFamilyName()));
        }
    }

    @Test
    void thePreUpgradeCheckpointOpensAsASchemaOneStore() {
        open().close();
        Path checkpoint = backups.resolve("pre-upgrade-1-2");

        Map<String, List<String>> fixture = readOnly(pristine);
        Map<String, List<String>> checkpointed = readOnly(checkpoint);
        assertThat(checkpointed.get("meta")).isEqualTo(fixture.get("meta"));
        assertThat(meta(checkpoint, "schema_version")).isEqualTo(new byte[]{0, 0, 0, 1});
        assertThat(meta(checkpoint, "jwt_signing_key")).isNull();
        for (StoreColumnFamily cf : BROKER_STATE.keySet()) {
            assertThat(checkpointed.get(cf.columnFamilyName())).as(cf.columnFamilyName()).isEqualTo(fixture.get(cf.columnFamilyName()));
        }
    }

    @Test
    void aStoreStartedFromACheckpointDirectoryIsNotDeletedByTheRetentionOfItsOwnUpgrade() throws IOException, URISyntaxException {
        // restored by pointing store.path at a checkpoint of a schema 1 store, with store.backups.keep=1
        Path running = copy(fixturePath(), backups.resolve("pre-upgrade-1-2"));

        RocksDbStore opened = open(running, 1, List.of(new Schema1To2Step()));

        assertThat(opened.meta().schemaVersion()).isEqualTo(2);
        List<CheckpointInfo> checkpoints = StoreCheckpoints.list(backups);
        assertThat(checkpoints).extracting(CheckpointInfo::path).contains(running).hasSize(2);
        assertThat(checkpoints).extracting(CheckpointInfo::name).anyMatch(name -> name.startsWith("pre-upgrade-1-2-"));
        opened.close();
        store = null;
        BROKER_STATE.keySet().forEach(cf -> assertThat(readOnly(running).get(cf.columnFamilyName()))
                .as(cf.columnFamilyName()).isEqualTo(readOnly(pristine).get(cf.columnFamilyName())));
    }

    /**
     * The store keeps absolute expiry times, so a fixture written with the default TTLs would expire a week later and
     * fail the upgrade tests for a reason that has nothing to do with the upgrade. This test names the entry instead.
     * The Session Expiry Interval of fx-dev-1 counts from its stored disconnect time and a block ends at its stored
     * expiration time, so those are checked as well.
     */
    @Test
    void theFixtureIsNotAboutToExpire() {
        long bound = ZonedDateTime.now(ZoneOffset.UTC).plusYears(10).toInstant().toEpochMilli();
        List<String> expiring = new ArrayList<>();
        int deviceEntries = withReadOnly(pristine, (db, handles) -> {
            int count = 0;
            try (RocksIterator it = db.newIterator(handles.get(StoreColumnFamily.DEVICE_MSG.columnFamilyName()))) {
                for (it.seekToFirst(); it.isValid(); it.next()) {
                    count++;
                    String entry = deviceMsgEntryName(it.key());
                    DeviceMsgEntryCodec.Header header = DeviceMsgEntryCodec.header(it.value());
                    if (header.ttlExpiresAtMs() <= bound) {
                        expiring.add(entry + ": TTL time " + Instant.ofEpochMilli(header.ttlExpiresAtMs()));
                    }
                    if (header.msgExpiresAtMs() != 0 && header.msgExpiresAtMs() <= bound) {
                        expiring.add(entry + ": message expiry time " + Instant.ofEpochMilli(header.msgExpiresAtMs()));
                    }
                }
                it.status();
            }
            return count;
        });
        int retainedWithInterval = withReadOnly(pristine, (db, handles) -> {
            int count = 0;
            try (RocksIterator it = db.newIterator(handles.get(StoreColumnFamily.RETAINED_MSG.columnFamilyName()))) {
                for (it.seekToFirst(); it.isValid(); it.next()) {
                    String topic = new String(it.key(), StandardCharsets.UTF_8);
                    Long expiresAtMs = retainedExpiresAtMs(topic, it.value());
                    if (expiresAtMs != null) {
                        count++;
                        if (expiresAtMs <= bound) {
                            expiring.add("retained message " + topic + ": message expiry time " + Instant.ofEpochMilli(expiresAtMs));
                        }
                    }
                }
                it.status();
            }
            return count;
        });

        Long sessionExpiryInterval = withReadOnly(pristine, (db, handles) -> {
            byte[] session = db.get(handles.get(StoreColumnFamily.CLIENT_SESSION.columnFamilyName()), utf8("fx-dev-1"));
            assertThat(session).as("client session fx-dev-1").isNotNull();
            byte[] sessionInfo = protoBytes(session, CLIENT_SESSION_SESSION_INFO_FIELD, "client session fx-dev-1");
            assertThat(sessionInfo).as("session info of the client session fx-dev-1").isNotNull();
            return protoVarint(sessionInfo, SESSION_INFO_SESSION_EXPIRY_INTERVAL_FIELD, "client session fx-dev-1");
        });
        if (sessionExpiryInterval == null || sessionExpiryInterval < TEN_YEARS_SEC) {
            expiring.add("client session fx-dev-1: Session Expiry Interval " + sessionExpiryInterval + " s");
        }
        int blocked = withReadOnly(pristine, (db, handles) -> {
            int count = 0;
            try (RocksIterator it = db.newIterator(handles.get(StoreColumnFamily.BLOCKED_CLIENT.columnFamilyName()))) {
                for (it.seekToFirst(); it.isValid(); it.next()) {
                    count++;
                    String name = "blocked client " + new String(it.key(), StandardCharsets.UTF_8);
                    Long expirationTime = protoVarint(it.value(), BLOCKED_CLIENT_EXPIRATION_TIME_FIELD, name);
                    // proto3 leaves a 0 out
                    long expiresAtMs = expirationTime == null ? 0 : expirationTime;
                    if (expiresAtMs != 0 && expiresAtMs <= bound) {
                        expiring.add(name + ": expiration time " + Instant.ofEpochMilli(expiresAtMs));
                    }
                }
                it.status();
            }
            return count;
        });

        assertThat(deviceEntries).as("device_msg entries").isEqualTo(BROKER_STATE.get(StoreColumnFamily.DEVICE_MSG));
        assertThat(blocked).as("blocked_client entries").isEqualTo(BROKER_STATE.get(StoreColumnFamily.BLOCKED_CLIENT));
        assertThat(retainedWithInterval).as("retained messages with a Message Expiry Interval").isEqualTo(1);
        assertThat(expiring).as("fixture entries that expire within 10 years").isEmpty();
    }

    private RocksDbStore open() {
        return open(List.of(new Schema1To2Step()));
    }

    private RocksDbStore open(List<StoreMigrationStep> steps) {
        return open(upgraded, 3, steps);
    }

    private RocksDbStore open(Path path, int keep, List<StoreMigrationStep> steps) {
        StoreProperties properties = new StoreProperties();
        properties.setPath(path.toString());
        properties.getBackups().setPath(backups.toString());
        properties.getBackups().setKeep(keep);
        store = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0),
                TestDiskSpaceMonitors.roomy(properties), steps);
        store.open();
        return store;
    }

    /**
     * {@code <queue key>#<sequence> (packet id <id>)}: the key is the 8-byte hash, the 4-byte length and the queue key,
     * then the 8-byte sequence.
     */
    private static String deviceMsgEntryName(byte[] key) {
        ByteBuffer buffer = ByteBuffer.wrap(key);
        int length = buffer.getInt(Long.BYTES);
        String queueKey = new String(key, DEVICE_MSG_QUEUE_KEY_OFFSET, length, StandardCharsets.UTF_8);
        long sequence = buffer.getLong(key.length - Long.BYTES);
        return "device_msg entry " + queueKey + "#" + sequence;
    }

    /**
     * The expiry time of a retained message, or {@code null} when it has no Message Expiry Interval. The value is a
     * version byte, a flags byte (bit 0: has an interval), the 4-byte interval in seconds and the retained message
     * proto, whose field 5 is the created time in ms.
     */
    private static Long retainedExpiresAtMs(String topic, byte[] value) {
        assertThat(value.length).as("length of the retained message %s", topic).isGreaterThanOrEqualTo(RETAINED_HEADER_SIZE);
        ByteBuffer buffer = ByteBuffer.wrap(value);
        assertThat(buffer.get(0)).as("version of the retained message %s", topic).isEqualTo((byte) RETAINED_VERSION);
        if ((buffer.get(1) & RETAINED_FLAG_HAS_INTERVAL) == 0) {
            return null;
        }
        long intervalSec = Integer.toUnsignedLong(buffer.getInt(2));
        try {
            CodedInputStream proto = CodedInputStream.newInstance(value, RETAINED_HEADER_SIZE, value.length - RETAINED_HEADER_SIZE);
            for (int tag = proto.readTag(); tag != 0; tag = proto.readTag()) {
                if (WireFormat.getTagFieldNumber(tag) == RETAINED_CREATED_TIME_FIELD) {
                    return proto.readInt64() + intervalSec * 1000;
                }
                proto.skipField(tag);
            }
        } catch (IOException e) {
            throw new AssertionError("The retained message " + topic + " is not a retained message proto", e);
        }
        throw new AssertionError("The retained message " + topic + " has no created time");
    }

    /**
     * The value of the varint field {@code field} of a proto, or {@code null} when the proto leaves it out.
     */
    private static Long protoVarint(byte[] proto, int field, String name) {
        try {
            CodedInputStream in = CodedInputStream.newInstance(proto);
            for (int tag = in.readTag(); tag != 0; tag = in.readTag()) {
                if (WireFormat.getTagFieldNumber(tag) == field) {
                    return in.readInt64();
                }
                in.skipField(tag);
            }
            return null;
        } catch (IOException e) {
            throw new AssertionError("The " + name + " is not a proto: " + e, e);
        }
    }

    /**
     * The bytes of the length-delimited field {@code field} of a proto, or {@code null} when the proto leaves it out.
     */
    private static byte[] protoBytes(byte[] proto, int field, String name) {
        try {
            CodedInputStream in = CodedInputStream.newInstance(proto);
            for (int tag = in.readTag(); tag != 0; tag = in.readTag()) {
                if (WireFormat.getTagFieldNumber(tag) == field) {
                    return in.readByteArray();
                }
                in.skipField(tag);
            }
            return null;
        } catch (IOException e) {
            throw new AssertionError("The " + name + " is not a proto: " + e, e);
        }
    }

    private static byte[] meta(Path path, String key) {
        return withReadOnly(path, (db, handles) -> db.get(handles.get(StoreColumnFamily.META.columnFamilyName()), utf8(key)));
    }

    /**
     * Every column family of the store at {@code path}, by name, as hex {@code key=value} lines in key order.
     */
    private static Map<String, List<String>> readOnly(Path path) {
        return withReadOnly(path, (db, handles) -> {
            Map<String, List<String>> snapshot = new LinkedHashMap<>();
            for (Map.Entry<String, ColumnFamilyHandle> family : handles.entrySet()) {
                List<String> entries = new ArrayList<>();
                try (RocksIterator it = db.newIterator(family.getValue())) {
                    for (it.seekToFirst(); it.isValid(); it.next()) {
                        entries.add(HexFormat.of().formatHex(it.key()) + "=" + HexFormat.of().formatHex(it.value()));
                    }
                    it.status();
                }
                snapshot.put(family.getKey(), entries);
            }
            return snapshot;
        });
    }

    private interface ReadOnlyAction<T> {
        T run(RocksDB db, Map<String, ColumnFamilyHandle> handles) throws RocksDBException;
    }

    /**
     * Opens the store at {@code path} read-only with the column families it has, whatever this build knows.
     */
    private static <T> T withReadOnly(Path path, ReadOnlyAction<T> action) {
        RocksDB.loadLibrary();
        try (Options listOptions = new Options();
             DBOptions dbOptions = new DBOptions();
             ColumnFamilyOptions options = new ColumnFamilyOptions()) {
            List<ColumnFamilyDescriptor> descriptors = new ArrayList<>();
            for (byte[] name : RocksDB.listColumnFamilies(listOptions, path.toString())) {
                descriptors.add(new ColumnFamilyDescriptor(name, options));
            }
            List<ColumnFamilyHandle> opened = new ArrayList<>();
            try (RocksDB db = RocksDB.openReadOnly(dbOptions, path.toString(), descriptors, opened)) {
                try {
                    Map<String, ColumnFamilyHandle> handles = new LinkedHashMap<>();
                    for (int i = 0; i < descriptors.size(); i++) {
                        handles.put(new String(descriptors.get(i).getName(), StandardCharsets.UTF_8), opened.get(i));
                    }
                    return action.run(db, handles);
                } finally {
                    opened.forEach(ColumnFamilyHandle::close);
                }
            }
        } catch (RocksDBException e) {
            throw new IllegalStateException("Cannot read the store at " + path + ": " + e.getMessage(), e);
        }
    }

    private static Path fixturePath() throws URISyntaxException {
        URL url = Schema1FixtureUpgradeTest.class.getResource(FIXTURE);
        assertThat(url).as("the schema 1 fixture %s on the test classpath", FIXTURE).isNotNull();
        return Path.of(url.toURI());
    }

    private static Path copy(Path from, Path to) throws IOException {
        Files.createDirectories(to);
        try (Stream<Path> files = Files.list(from)) {
            for (Path file : files.toList()) {
                Files.copy(file, to.resolve(file.getFileName()));
            }
        }
        return to;
    }

    private static Map<StoreColumnFamily, Integer> brokerState() {
        Map<StoreColumnFamily, Integer> counts = new LinkedHashMap<>();
        counts.put(StoreColumnFamily.CLIENT_SESSION, 2);
        counts.put(StoreColumnFamily.CLIENT_SUBSCRIPTIONS, 2);
        counts.put(StoreColumnFamily.RETAINED_MSG, 2);
        counts.put(StoreColumnFamily.BLOCKED_CLIENT, 2);
        counts.put(StoreColumnFamily.CLIENT_SESSION_ATTR, 4);
        counts.put(StoreColumnFamily.DEVICE_MSG, 13);
        counts.put(StoreColumnFamily.DEVICE_MSG_META, 2);
        return counts;
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

}
