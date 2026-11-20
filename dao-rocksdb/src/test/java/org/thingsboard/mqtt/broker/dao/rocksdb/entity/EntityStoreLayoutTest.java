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
package org.thingsboard.mqtt.broker.dao.rocksdb.entity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProviderType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscription;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMemoryBudget;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.rocksdb.disk.TestDiskSpaceMonitors;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the bytes {@link RocksEntityStore} writes to the {@code entity} and {@code entity_index} column families: one
 * document of every type, saved with a fixed id and created time, and the exact (key, value) pairs scanned back. The
 * expected bytes are written out here from the documented layouts, not computed by {@link EntityKeys}, so this fails
 * when a code, a layout or an indexed value changes. A change to any of them breaks every store already on disk.
 * <p>
 * The ids are 16 bytes {@code 01..0f} then a distinguishing byte, so a swapped half or byte order shows. Layouts:
 * <ul>
 *   <li>{@code entity}: key {@code [type code][id]}, value {@code [0x01][JSON]} (only the format byte is pinned here;
 *   the JSON is pinned by {@code StorageObjectMapperTest});</li>
 *   <li>{@code entity_index}, unique: key {@code [index code][4-byte BE length][UTF-8 value]};</li>
 *   <li>{@code entity_index}, non-unique: the same, then the 16 id bytes;</li>
 *   <li>{@code entity_index} value: the 16 id bytes, for both.</li>
 * </ul>
 */
class EntityStoreLayoutTest {

    // type codes: 0x01 admin settings, 0x02 auth provider, 0x03 client credentials, 0x04 user, 0x05 user credentials,
    // 0x06 web socket connection, 0x07 web socket subscription, 0x08 unauthorized client
    private static final String ADMIN_ID = "0102030405060708090a0b0c0d0e0fa1";
    private static final String PROVIDER_ID = "0102030405060708090a0b0c0d0e0fb1";
    private static final String CREDENTIALS_ID = "0102030405060708090a0b0c0d0e0fc1";
    private static final String USER_ID = "0102030405060708090a0b0c0d0e0fd1";
    private static final String USER_CREDENTIALS_ID = "0102030405060708090a0b0c0d0e0fe1";
    private static final String CONNECTION_ID = "0102030405060708090a0b0c0d0e0ff1";
    private static final String SUBSCRIPTION_ID = "0102030405060708090a0b0c0d0e0ff2";

    private static final String USER_ID_TEXT = "01020304-0506-0708-090a-0b0c0d0e0fd1";
    private static final String CONNECTION_ID_TEXT = "01020304-0506-0708-090a-0b0c0d0e0ff1";

    @TempDir
    Path dir;

    private RocksDbStore rocksDbStore;
    private RocksEntityStore store;

    @BeforeEach
    void open() {
        StoreProperties properties = new StoreProperties();
        properties.setPath(dir.toString());
        rocksDbStore = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0), TestDiskSpaceMonitors.roomy(properties));
        rocksDbStore.open();
        store = new RocksEntityStore(rocksDbStore);
    }

    @AfterEach
    void close() {
        rocksDbStore.close();
    }

    @Test
    void theEntityColumnFamilyHoldsTheTypeCodeAndIdAsKeyAndTheFormatByteFirst() {
        saveOneOfEveryType();

        List<String> keys = new ArrayList<>();
        List<Byte> formatBytes = new ArrayList<>();
        for (Pair pair : scan(StoreColumnFamily.ENTITY)) {
            keys.add(pair.key());
            formatBytes.add(HexFormat.of().parseHex(pair.value().substring(0, 2))[0]);
        }

        assertThat(keys).containsExactly(
                "01" + ADMIN_ID,
                "02" + PROVIDER_ID,
                "03" + CREDENTIALS_ID,
                "04" + USER_ID,
                "05" + USER_CREDENTIALS_ID,
                "06" + CONNECTION_ID,
                "07" + SUBSCRIPTION_ID,
                // unauthorized client: the type code, then the client id as UTF-8 ("uc-1")
                "08" + ascii("uc-1"));
        assertThat(formatBytes).as("the format byte of every value is 1 (JSON)").containsOnly((byte) 1);
    }

    @Test
    void theIndexColumnFamilyHoldsTheDocumentedKeysAndTheIdBytesAsValue() {
        saveOneOfEveryType();

        assertThat(scan(StoreColumnFamily.ENTITY_INDEX)).containsExactly(
                // 0x01 ADMIN_SETTINGS_KEY, unique: "mail"
                new Pair("01" + "00000004" + ascii("mail"), ADMIN_ID),
                // 0x02 AUTH_PROVIDER_TYPE, unique: the enum name
                new Pair("02" + "0000000a" + ascii("MQTT_BASIC"), PROVIDER_ID),
                // 0x03 CREDENTIALS_ID, unique: "cid-1"
                new Pair("03" + "00000005" + ascii("cid-1"), CREDENTIALS_ID),
                // 0x04 CREDENTIALS_NAME, non-unique: "n1", then the id
                new Pair("04" + "00000002" + ascii("n1") + CREDENTIALS_ID, CREDENTIALS_ID),
                // 0x05 CREDENTIALS_TYPE, non-unique: the enum name, then the id
                new Pair("05" + "0000000a" + ascii("MQTT_BASIC") + CREDENTIALS_ID, CREDENTIALS_ID),
                // 0x06 USER_EMAIL, unique: "a@b.c"
                new Pair("06" + "00000005" + ascii("a@b.c"), USER_ID),
                // 0x07 USER_CREDENTIALS_USER_ID, unique: the user id as lower-case UUID text (36 bytes)
                new Pair("07" + "00000024" + ascii(USER_ID_TEXT), USER_CREDENTIALS_ID),
                // 0x08 USER_CREDENTIALS_ACTIVATE_TOKEN, unique: "act"
                new Pair("08" + "00000003" + ascii("act"), USER_CREDENTIALS_ID),
                // 0x09 USER_CREDENTIALS_RESET_TOKEN: the reset token is null, so there is no entry
                // 0x0a WS_CONNECTION_USER_NAME, unique: the user id text, a zero byte, the name "w1" (39 bytes)
                new Pair("0a" + "00000027" + ascii(USER_ID_TEXT) + "00" + ascii("w1"), CONNECTION_ID),
                // 0x0b WS_CONNECTION_USER, non-unique: the user id text, then the id
                new Pair("0b" + "00000024" + ascii(USER_ID_TEXT) + CONNECTION_ID, CONNECTION_ID),
                // 0x0c WS_SUBSCRIPTION_CONNECTION, non-unique: the connection id text, then the id
                new Pair("0c" + "00000024" + ascii(CONNECTION_ID_TEXT) + SUBSCRIPTION_ID, SUBSCRIPTION_ID));
    }

    @Test
    void theTimeSeriesKeyDictionaryIndexIsNotTheIndexOfAnyDescriptor() {
        assertThat(EntityIndex.TS_KEY_DICTIONARY.code()).isEqualTo((byte) 64);
        for (EntityDescriptor<?, ?> d : EntityDescriptors.ALL) {
            assertThat(d.indexes().keySet()).doesNotContain(EntityIndex.TS_KEY_DICTIONARY);
            assertThat(d.cascades()).extracting(EntityDescriptor.Cascade::childIndex).doesNotContain(EntityIndex.TS_KEY_DICTIONARY);
        }
        assertThatThrownBy(() -> store.findByIndex(EntityDescriptors.USER, EntityIndex.TS_KEY_DICTIONARY, "key"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void saveOneOfEveryType() {
        AdminSettings settings = new AdminSettings(uuid(ADMIN_ID));
        settings.setCreatedTime(1L);
        settings.setKey("mail");
        store.save(EntityDescriptors.ADMIN_SETTINGS, settings);

        MqttAuthProvider provider = new MqttAuthProvider();
        provider.setId(uuid(PROVIDER_ID));
        provider.setCreatedTime(1L);
        provider.setType(MqttAuthProviderType.MQTT_BASIC);
        store.save(EntityDescriptors.MQTT_AUTH_PROVIDER, provider);

        MqttClientCredentials credentials = new MqttClientCredentials(uuid(CREDENTIALS_ID));
        credentials.setCreatedTime(1L);
        credentials.setCredentialsId("cid-1");
        credentials.setName("n1");
        credentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        store.save(EntityDescriptors.MQTT_CLIENT_CREDENTIALS, credentials);

        User user = new User(uuid(USER_ID));
        user.setCreatedTime(1L);
        user.setEmail("a@b.c");
        store.save(EntityDescriptors.USER, user);

        UserCredentials userCredentials = new UserCredentials(uuid(USER_CREDENTIALS_ID));
        userCredentials.setCreatedTime(1L);
        userCredentials.setUserId(uuid(USER_ID));
        userCredentials.setActivateToken("act");
        store.save(EntityDescriptors.USER_CREDENTIALS, userCredentials);

        WebSocketConnection connection = new WebSocketConnection();
        connection.setId(uuid(CONNECTION_ID));
        connection.setCreatedTime(1L);
        connection.setUserId(uuid(USER_ID));
        connection.setName("w1");
        store.save(EntityDescriptors.WEBSOCKET_CONNECTION, connection);

        WebSocketSubscription subscription = new WebSocketSubscription();
        subscription.setId(uuid(SUBSCRIPTION_ID));
        subscription.setCreatedTime(1L);
        subscription.setWebSocketConnectionId(uuid(CONNECTION_ID));
        store.save(EntityDescriptors.WEBSOCKET_SUBSCRIPTION, subscription);

        store.save(EntityDescriptors.UNAUTHORIZED_CLIENT, UnauthorizedClient.builder().clientId("uc-1").reason("r").ts(1L).build());
    }

    private List<Pair> scan(StoreColumnFamily cf) {
        return rocksDbStore.scan(cf, null, null, it -> {
            List<Pair> pairs = new ArrayList<>();
            for (; it.isValid(); it.next()) {
                pairs.add(new Pair(HexFormat.of().formatHex(it.key()), HexFormat.of().formatHex(it.value())));
            }
            return pairs;
        });
    }

    private static UUID uuid(String hex) {
        return new UUID(Long.parseUnsignedLong(hex.substring(0, 16), 16), Long.parseUnsignedLong(hex.substring(16), 16));
    }

    private static String ascii(String text) {
        return HexFormat.of().formatHex(text.getBytes(StandardCharsets.UTF_8));
    }

    // both as lower-case hex
    private record Pair(String key, String value) {
    }

}
