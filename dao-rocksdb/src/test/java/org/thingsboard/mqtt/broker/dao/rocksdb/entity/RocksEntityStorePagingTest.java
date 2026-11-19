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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscription;
import org.thingsboard.mqtt.broker.dao.rocksdb.RecordingRocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreBatch;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMemoryBudget;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreWriteClass;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors.MQTT_CLIENT_CREDENTIALS;
import static org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors.USER;
import static org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors.USER_CREDENTIALS;
import static org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors.WEBSOCKET_CONNECTION;
import static org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors.WEBSOCKET_SUBSCRIPTION;

class RocksEntityStorePagingTest {

    @TempDir
    Path dir;

    private FailingStore rocksDbStore;
    private RocksEntityStore store;

    @BeforeEach
    void open() {
        reopen();
    }

    @AfterEach
    void close() {
        rocksDbStore.close();
    }

    @Test
    void pagesAreSlicedAfterSortingAndCarryTotals() {
        for (int i = 0; i < 25; i++) {
            save(String.format("cred-%02d", 24 - i));
        }
        SortOrder byName = new SortOrder("name", SortOrder.Direction.ASC);

        PageData<MqttClientCredentials> first = store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10, 0, null, byName), null);
        PageData<MqttClientCredentials> second = store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10, 1, null, byName), null);
        PageData<MqttClientCredentials> third = store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10, 2, null, byName), null);

        assertThat(List.of(first, second, third)).extracting(page -> page.getData().size()).containsExactly(10, 10, 5);
        assertThat(List.of(first, second, third)).extracting(PageData::getTotalElements).containsOnly(25L);
        assertThat(List.of(first, second, third)).extracting(PageData::getTotalPages).containsOnly(3);
        assertThat(List.of(first, second, third)).extracting(PageData::hasNext).containsExactly(true, true, false);
        assertThat(first.getData()).extracting(MqttClientCredentials::getName).first().isEqualTo("cred-00");
        assertThat(third.getData()).extracting(MqttClientCredentials::getName).last().isEqualTo("cred-24");

        PageData<MqttClientCredentials> descending = store.findPage(MQTT_CLIENT_CREDENTIALS,
                new PageLink(3, 0, null, new SortOrder("name", SortOrder.Direction.DESC)), null);
        assertThat(descending.getData()).extracting(MqttClientCredentials::getName).containsExactly("cred-24", "cred-23", "cred-22");
    }

    @Test
    void textSearchIsCaseInsensitiveContainsOnTheName() {
        save("Sensor-A");
        save("sensor-b");
        save("other");

        PageData<MqttClientCredentials> found = store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10, 0, "SENSOR"), null);

        assertThat(found.getData()).extracting(MqttClientCredentials::getName).containsExactlyInAnyOrder("Sensor-A", "sensor-b");
        assertThat(found.getTotalElements()).isEqualTo(2);
        assertThat(store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10, 0, ""), null).getTotalElements()).isEqualTo(3);
        assertThat(store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10, 0, null), null).getTotalElements()).isEqualTo(3);
    }

    @Test
    void textSearchRunsOnTheFieldOfTheSqlQuery() {
        User user = user("Admin@Example.org");
        user.setFirstName("search-me");
        store.save(USER, user);

        assertThat(store.findPage(USER, new PageLink(10, 0, "example"), null).getTotalElements()).isEqualTo(1);
        assertThat(store.findPage(USER, new PageLink(10, 0, "search-me"), null).getTotalElements()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "a_b", "\\", "\"", "Ü", "straße", "клієнт"})
    void searchCharactersMatchLiterally(String search) {
        save("pre" + search + "post");
        save(wildcardTwin(search));

        PageData<MqttClientCredentials> found = store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10, 0, search), null);

        assertThat(found.getData()).extracting(MqttClientCredentials::getName).containsExactly("pre" + search + "post");
    }

    @Test
    void aPagePastTheEndIsEmptyWithTotals() {
        for (int i = 0; i < 25; i++) {
            save("cred-" + i);
        }

        PageData<MqttClientCredentials> page = store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10, 9), null);

        assertThat(page.getData()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(25);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.hasNext()).isFalse();
        PageData<MqttClientCredentials> last = store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10, Integer.MAX_VALUE), null);
        assertThat(last.getData()).isEmpty();
        assertThat(last.hasNext()).isFalse();
    }

    @Test
    void anEmptyTypeHasNoPages() {
        PageData<MqttClientCredentials> page = store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10), null);

        assertThat(page.getData()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getTotalPages()).isZero();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void pageSizeOneOverEqualSortKeysVisitsEveryDocumentOnce() {
        for (int i = 0; i < 5; i++) {
            save("same");
        }
        Set<UUID> seen = new HashSet<>();

        for (int page = 0; page < 5; page++) {
            PageData<MqttClientCredentials> found = store.findPage(MQTT_CLIENT_CREDENTIALS,
                    new PageLink(1, page, null, new SortOrder("name")), null);
            assertThat(found.getData()).hasSize(1);
            seen.add(found.getData().get(0).getId());
        }

        assertThat(seen).hasSize(5);
    }

    @Test
    void anUnknownSortPropertyIsRejected() {
        save("a");

        assertThatThrownBy(() -> store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10, 0, null, new SortOrder("nope")), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown sort property: nope");
        assertThatThrownBy(() -> store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10, 0, null, new SortOrder("additionalInfo")), null))
                .as("a JSON column is not sortable").isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withoutASortOrderThePageIsOrderedById() {
        for (int i = 0; i < 25; i++) {
            save("cred-" + i);
        }
        List<UUID> expected = store.findAll(MQTT_CLIENT_CREDENTIALS).stream().map(MqttClientCredentials::getId)
                .sorted((a, b) -> Arrays.compareUnsigned(EntityKeys.uuidBytes(a), EntityKeys.uuidBytes(b)))
                .limit(10).toList();

        PageData<MqttClientCredentials> page = store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10), null);

        assertThat(page.getData()).extracting(MqttClientCredentials::getId).containsExactlyElementsOf(expected);
    }

    @Test
    void sortingByAUuidPropertyUsesTheUnsignedByteOrder() {
        UUID low = savedUser(new UUID(0x7FFF_FFFF_FFFF_FFFFL, 0), "low@example.org");
        UUID high = savedUser(new UUID(0x8000_0000_0000_0000L, 0), "high@example.org");
        store.save(WEBSOCKET_CONNECTION, connection(high, "high"));
        store.save(WEBSOCKET_CONNECTION, connection(low, "low"));

        PageData<WebSocketConnection> page = store.findPage(WEBSOCKET_CONNECTION, new PageLink(10, 0, null, new SortOrder("userId")), null);

        assertThat(page.getData()).extracting(WebSocketConnection::getName).containsExactly("low", "high");
    }

    @Test
    void theFilterSeesTheProjectedFields() {
        UUID userId = store.save(USER, user("mine@example.org")).getId();
        WebSocketConnection mine = store.save(WEBSOCKET_CONNECTION, connection(userId, "mine"));
        store.save(WEBSOCKET_CONNECTION, connection(store.save(USER, user("other@example.org")).getId(), "other"));
        List<RocksEntityStore.ProjectedEntity> seen = new ArrayList<>();

        PageData<WebSocketConnection> page = store.findPage(WEBSOCKET_CONNECTION, new PageLink(10), projected -> {
            seen.add(projected);
            return userId.equals(projected.filterValues().get("userId"));
        });

        assertThat(page.getData()).extracting(WebSocketConnection::getName).containsExactly("mine");
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(seen).hasSize(2).anySatisfy(projected -> {
            assertThat(projected.id()).isEqualTo(mine.getId());
            assertThat(projected.createdTime()).isEqualTo(mine.getCreatedTime());
            assertThat(projected.textSearch()).isEqualTo("mine");
            assertThat(projected.sortValues()).containsEntry("name", "mine").containsEntry("userId", userId)
                    .containsEntry("id", mine.getId()).containsEntry("createdTime", mine.getCreatedTime());
        });
    }

    @Test
    void credentialsFilterFieldsAreParsedFromTheCredentialsValue() {
        MqttClientCredentials basic = credentials("basic", ClientCredentialsType.MQTT_BASIC);
        basic.setCredentialsValue("{\"clientId\":\"Client-1\",\"userName\":\"User-1\",\"password\":null}");
        store.save(MQTT_CLIENT_CREDENTIALS, basic);
        MqttClientCredentials broken = credentials("broken", ClientCredentialsType.MQTT_BASIC);
        broken.setCredentialsValue("{not json");
        store.save(MQTT_CLIENT_CREDENTIALS, broken);
        Map<String, Map<String, Object>> filterValues = new HashMap<>();

        store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10), projected -> {
            filterValues.put(projected.textSearch(), projected.filterValues());
            return true;
        });

        assertThat(filterValues.get("basic")).containsEntry("userName", "User-1").containsEntry("clientId", "Client-1")
                .containsEntry("certCnPattern", null).containsEntry("credentialsType", ClientCredentialsType.MQTT_BASIC);
        assertThat(filterValues.get("broken")).containsEntry("userName", null).containsEntry("clientId", null);
    }

    @Test
    void onlyThePageDocumentsAreRead() {
        List<UUID> ids = new ArrayList<>();
        rocksDbStore.write(StoreWriteClass.NEW_DATA, batch -> {
            for (int i = 0; i < 1000; i++) {
                MqttClientCredentials credentials = credentials("cred-" + i, ClientCredentialsType.MQTT_BASIC);
                credentials.setId(UUID.randomUUID());
                ids.add(credentials.getId());
                batch.put(StoreColumnFamily.ENTITY, EntityKeys.entityKey(StoredEntityType.MQTT_CLIENT_CREDENTIALS,
                        EntityKeys.uuidBytes(credentials.getId())), StorageObjectMapper.write(credentials));
            }
        });
        restart();
        rocksDbStore.takeGets();

        PageData<MqttClientCredentials> page = store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10, 3, null, new SortOrder("name")), null);

        assertThat(page.getData()).hasSize(10);
        assertThat(page.getTotalElements()).isEqualTo(1000);
        assertThat(rocksDbStore.takeGets()).isEqualTo(10);
        assertThat(ids).containsAll(page.getData().stream().map(MqttClientCredentials::getId).toList());
    }

    @Test
    void theProjectionFollowsSavesAndRemoves() {
        MqttClientCredentials first = save("first");
        save("second");
        first.setName("renamed");
        store.save(MQTT_CLIENT_CREDENTIALS, first);

        assertThat(store.count(MQTT_CLIENT_CREDENTIALS)).isEqualTo(2);
        assertThat(store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10, 0, "renamed"), null).getTotalElements()).isEqualTo(1);
        assertThat(store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10, 0, "first"), null).getTotalElements()).isZero();

        store.removeById(MQTT_CLIENT_CREDENTIALS, first.getId());

        assertThat(store.count(MQTT_CLIENT_CREDENTIALS)).isEqualTo(1);
        assertThat(store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10), null).getData())
                .extracting(MqttClientCredentials::getName).containsExactly("second");
    }

    @Test
    void cascadedRemovalsLeaveTheProjectionsOfTheChildren() {
        User user = store.save(USER, user("a@example.org"));
        WebSocketConnection connection = store.save(WEBSOCKET_CONNECTION, connection(user.getId(), "conn"));
        WebSocketSubscription subscription = new WebSocketSubscription();
        subscription.setWebSocketConnectionId(connection.getId());
        store.save(WEBSOCKET_SUBSCRIPTION, subscription);

        store.removeById(USER, user.getId());

        assertThat(store.count(USER)).isZero();
        assertThat(store.count(WEBSOCKET_CONNECTION)).isZero();
        assertThat(store.count(WEBSOCKET_SUBSCRIPTION)).isZero();
        assertThat(store.findPage(WEBSOCKET_SUBSCRIPTION, new PageLink(10), null).getData()).isEmpty();
    }

    @Test
    void removeAndSaveSwapsTheProjectedDocuments() {
        MqttClientCredentials old = save("old");

        MqttClientCredentials replacement = store.removeAndSave(MQTT_CLIENT_CREDENTIALS, old.getId(), credentials("new", ClientCredentialsType.MQTT_BASIC));

        assertThat(store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10), null).getData())
                .extracting(MqttClientCredentials::getId).containsExactly(replacement.getId());
        assertThat(store.removeAndSave(MQTT_CLIENT_CREDENTIALS, UUID.randomUUID(), credentials("third", ClientCredentialsType.MQTT_BASIC)))
                .as("a missing removed id is skipped").isNotNull();
        assertThat(store.count(MQTT_CLIENT_CREDENTIALS)).isEqualTo(2);
    }

    @Test
    void aFailedWriteLeavesTheProjectionAsItWas() {
        MqttClientCredentials kept = save("kept");
        rocksDbStore.failWrites = true;

        assertThatThrownBy(() -> save("lost")).isInstanceOf(StoreException.class);
        assertThatThrownBy(() -> store.removeById(MQTT_CLIENT_CREDENTIALS, kept.getId())).isInstanceOf(StoreException.class);

        assertThat(store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10), null).getData())
                .extracting(MqttClientCredentials::getId).containsExactly(kept.getId());
    }

    @Test
    void theProjectionIsRebuiltFromTheStoreOnRestart() {
        save("a");
        save("b");
        save("c");
        List<Map<String, Comparable<?>>> before = sortValues();

        restart();

        assertThat(store.count(MQTT_CLIENT_CREDENTIALS)).isEqualTo(3);
        assertThat(sortValues()).containsExactlyInAnyOrderElementsOf(before);
        assertThat(store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10, 0, null, new SortOrder("name")), null).getData())
                .extracting(MqttClientCredentials::getName).containsExactly("a", "b", "c");
    }

    @Test
    void anUndecodableDocumentFailsTheStartupBuildNamingIt() {
        UUID id = UUID.randomUUID();
        rocksDbStore.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.ENTITY,
                EntityKeys.entityKey(StoredEntityType.MQTT_CLIENT_CREDENTIALS, EntityKeys.uuidBytes(id)), new byte[]{1, '{', 'x'});
        RocksEntityStore restarted = new RocksEntityStore(rocksDbStore);

        assertThatThrownBy(restarted::buildProjections)
                .isInstanceOf(StoreException.class).hasMessageContaining("MQTT_CLIENT_CREDENTIALS").hasMessageContaining(id.toString());
    }

    @Test
    void aTypeWithoutAProjectionCannotBePaged() {
        assertThatThrownBy(() -> store.findPage(USER_CREDENTIALS, new PageLink(10), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("USER_CREDENTIALS");
        assertThatThrownBy(() -> store.count(USER_CREDENTIALS))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("USER_CREDENTIALS");
    }

    // the filter sees the documents in the projection's own order, which is unspecified, so compare them in any order
    private List<Map<String, Comparable<?>>> sortValues() {
        List<Map<String, Comparable<?>>> values = new ArrayList<>();
        PageData<MqttClientCredentials> page = store.findPage(MQTT_CLIENT_CREDENTIALS, new PageLink(10), projected -> {
            values.add(new HashMap<>(projected.sortValues()));
            return true;
        });
        assertThat(page.getData()).hasSize(values.size());
        return values;
    }

    private void restart() {
        rocksDbStore.close();
        reopen();
    }

    private void reopen() {
        StoreProperties properties = new StoreProperties();
        properties.setPath(dir.toString());
        rocksDbStore = new FailingStore(properties);
        rocksDbStore.open();
        store = new RocksEntityStore(rocksDbStore);
        store.buildProjections();
    }

    private static final class FailingStore extends RecordingRocksDbStore {

        volatile boolean failWrites;

        FailingStore(StoreProperties properties) {
            super(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0));
        }

        @Override
        public void write(StoreWriteClass writeClass, Consumer<StoreBatch> body) {
            if (failWrites) {
                throw new StoreException("Injected write failure");
            }
            super.write(writeClass, body);
        }

    }

    private MqttClientCredentials save(String name) {
        return store.save(MQTT_CLIENT_CREDENTIALS, credentials(name, ClientCredentialsType.MQTT_BASIC));
    }

    // a name the search would match only if its % or _ were wildcards
    private static String wildcardTwin(String search) {
        String twin = search.replace('%', 'x').replace('_', 'x');
        return twin.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)) ? "plain" : twin;
    }

    private static MqttClientCredentials credentials(String name, ClientCredentialsType type) {
        MqttClientCredentials credentials = new MqttClientCredentials();
        credentials.setCredentialsId(UUID.randomUUID().toString());
        credentials.setName(name);
        credentials.setCredentialsType(type);
        return credentials;
    }

    private static User user(String email) {
        User user = new User();
        user.setEmail(email);
        return user;
    }

    // a connection needs its user: the store refuses a child whose parent is missing, as the SQL foreign key does
    private UUID savedUser(UUID id, String email) {
        User user = user(email);
        user.setId(id);
        user.setCreatedTime(1L);
        return store.save(USER, user).getId();
    }

    private static WebSocketConnection connection(UUID userId, String name) {
        WebSocketConnection connection = new WebSocketConnection();
        connection.setUserId(userId);
        connection.setName(name);
        return connection;
    }

}
