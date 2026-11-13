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
package org.thingsboard.mqtt.broker.dao.rocksdb;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thingsboard.mqtt.broker.dao.store.BrokerStateStores;
import org.thingsboard.mqtt.broker.dao.store.KeyValueStore;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RocksDbKeyValueStoreTest {

    @TempDir
    Path dir;

    private RocksDbStore store;

    @AfterEach
    void closeStore() {
        if (store != null) {
            store.close();
        }
    }

    @Test
    void putGetAndDeleteRoundTrip() {
        KeyValueStore sessions = open().clientSessions();

        sessions.put("client-1", bytes("v1"));
        assertThat(sessions.get("client-1")).isEqualTo(bytes("v1"));

        sessions.put("client-1", bytes("v2"));
        assertThat(sessions.get("client-1")).isEqualTo(bytes("v2"));

        sessions.delete("client-1");
        assertThat(sessions.get("client-1")).isNull();
    }

    @Test
    void getOfAMissingKeyIsNull() {
        assertThat(open().retainedMessages().get("missing")).isNull();
    }

    @Test
    void deleteOfAMissingKeyIsANoOp() {
        open().blockedClients().delete("missing");
    }

    @Test
    void forEachVisitsEveryEntryOfItsOwnColumnFamilyAndNoneOfAnother() {
        BrokerStateStores stores = open();
        stores.clientSessions().put("a", bytes("1"));
        stores.clientSessions().put("b", bytes("2"));
        stores.clientSubscriptions().put("c", bytes("3"));
        stores.retainedMessages().put("d", bytes("4"));
        stores.blockedClients().put("e", bytes("5"));
        stores.clientSessionAttributes().put("f", bytes("6"));

        assertThat(dump(stores.clientSessions())).containsExactly(Map.entry("a", "1"), Map.entry("b", "2"));
        assertThat(dump(stores.clientSubscriptions())).containsExactly(Map.entry("c", "3"));
        assertThat(dump(stores.retainedMessages())).containsExactly(Map.entry("d", "4"));
        assertThat(dump(stores.blockedClients())).containsExactly(Map.entry("e", "5"));
        assertThat(dump(stores.clientSessionAttributes())).containsExactly(Map.entry("f", "6"));
    }

    @Test
    void forEachOnAnEmptyColumnFamilyVisitsNothing() {
        assertThat(dump(open().retainedMessages())).isEmpty();
    }

    @Test
    void eachStoreIsBackedByItsOwnColumnFamily() {
        BrokerStateStores stores = open();
        stores.clientSessions().put("k", bytes("session"));
        stores.clientSubscriptions().put("k", bytes("subs"));
        stores.retainedMessages().put("k", bytes("retained"));
        stores.blockedClients().put("k", bytes("blocked"));
        stores.clientSessionAttributes().put("k", bytes("attr"));

        assertThat(store.get(StoreColumnFamily.CLIENT_SESSION, bytes("k"))).isEqualTo(bytes("session"));
        assertThat(store.get(StoreColumnFamily.CLIENT_SUBSCRIPTIONS, bytes("k"))).isEqualTo(bytes("subs"));
        assertThat(store.get(StoreColumnFamily.RETAINED_MSG, bytes("k"))).isEqualTo(bytes("retained"));
        assertThat(store.get(StoreColumnFamily.BLOCKED_CLIENT, bytes("k"))).isEqualTo(bytes("blocked"));
        assertThat(store.get(StoreColumnFamily.CLIENT_SESSION_ATTR, bytes("k"))).isEqualTo(bytes("attr"));
    }

    @Test
    void keysAreUtf8AndRoundTripWhateverTheirContent() {
        KeyValueStore retained = open().retainedMessages();
        String slashes = "a/b";
        String cyrillic = "клієнт";
        String longest = "k".repeat(65_535);

        retained.put(slashes, bytes("1"));
        retained.put(cyrillic, bytes("2"));
        retained.put(longest, bytes("3"));

        assertThat(retained.get(slashes)).isEqualTo(bytes("1"));
        assertThat(retained.get(cyrillic)).isEqualTo(bytes("2"));
        assertThat(retained.get(longest)).isEqualTo(bytes("3"));
        assertThat(store.get(StoreColumnFamily.RETAINED_MSG, cyrillic.getBytes(StandardCharsets.UTF_8))).isEqualTo(bytes("2"));
        assertThat(dump(retained)).containsOnlyKeys(slashes, cyrillic, longest);
    }

    @Test
    void valuesSurviveAReopen() {
        BrokerStateStores stores = open();
        stores.clientSessions().put("s", bytes("session"));
        stores.blockedClients().put("b", bytes("blocked"));
        store.close();

        BrokerStateStores reopened = open();

        assertThat(reopened.clientSessions().get("s")).isEqualTo(bytes("session"));
        assertThat(reopened.blockedClients().get("b")).isEqualTo(bytes("blocked"));
    }

    @Test
    void retainKeysDeletesTheKeysThePredicateRejectsAndReturnsTheirNumber() {
        KeyValueStore sessions = open().clientSessions();
        sessions.put("x1", bytes("1"));
        sessions.put("x2", bytes("2"));
        sessions.put("a", bytes("3"));
        sessions.put("b", bytes("4"));
        sessions.put("c", bytes("5"));
        store.put(StoreColumnFamily.CLIENT_SUBSCRIPTIONS, bytes("a"), bytes("other family"));

        int deleted = sessions.retainKeys(k -> k.startsWith("x"));

        assertThat(deleted).isEqualTo(3);
        assertThat(dump(sessions)).containsExactly(Map.entry("x1", "1"), Map.entry("x2", "2"));
        assertThat(store.get(StoreColumnFamily.CLIENT_SUBSCRIPTIONS, bytes("a"))).isEqualTo(bytes("other family"));
    }

    @Test
    void retainKeysThatKeepsEverythingDeletesNothing() {
        KeyValueStore sessions = open().clientSessions();
        sessions.put("a", bytes("1"));

        assertThat(sessions.retainKeys(k -> true)).isZero();
        assertThat(sessions.get("a")).isEqualTo(bytes("1"));
    }

    @Test
    void retainKeysOnAnEmptyColumnFamilyReturnsZero() {
        assertThat(open().clientSessions().retainKeys(k -> false)).isZero();
    }

    @Test
    void aPredicateThatThrowsDeletesNothing() {
        KeyValueStore sessions = open().clientSessions();
        sessions.put("a", bytes("1"));
        sessions.put("b", bytes("2"));

        assertThatThrownBy(() -> sessions.retainKeys(k -> {
            if (k.equals("b")) {
                throw new IllegalArgumentException("boom");
            }
            return false;
        })).isInstanceOf(IllegalArgumentException.class);

        assertThat(dump(sessions)).containsOnlyKeys("a", "b");
    }

    @Test
    void forEachMayWriteToTheStoreItIsReading() {
        KeyValueStore sessions = open().clientSessions();
        sessions.put("a", bytes("1"));

        sessions.forEach((k, v) -> sessions.put(k + "-copy", v));

        assertThat(dump(sessions)).containsOnlyKeys("a", "a-copy");
    }

    @Test
    void anyCallAfterCloseThrowsStoreException() {
        KeyValueStore sessions = open().clientSessions();
        sessions.put("a", bytes("1"));
        store.close();

        assertThatThrownBy(() -> sessions.put("a", bytes("2"))).isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> sessions.get("a")).isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> sessions.delete("a")).isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> sessions.forEach((k, v) -> {
        })).isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> sessions.retainKeys(k -> false)).isInstanceOf(StoreException.class);
    }

    private BrokerStateStores open() {
        StoreProperties properties = new StoreProperties();
        properties.setPath(dir.toString());
        store = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0));
        store.open();
        return new RocksDbBrokerStateStores(store);
    }

    private static Map<String, String> dump(KeyValueStore kv) {
        Map<String, String> all = new TreeMap<>();
        kv.forEach((k, v) -> all.put(k, new String(v, StandardCharsets.UTF_8)));
        return all;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

}
