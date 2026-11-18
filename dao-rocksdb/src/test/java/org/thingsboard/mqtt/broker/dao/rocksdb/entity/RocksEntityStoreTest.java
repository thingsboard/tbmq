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
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketConnection;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscription;
import org.thingsboard.mqtt.broker.dao.exception.EntityConstraintViolationException;
import org.thingsboard.mqtt.broker.dao.rocksdb.RecordingRocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMemoryBudget;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreWriteClass;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors.MQTT_CLIENT_CREDENTIALS;
import static org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors.UNAUTHORIZED_CLIENT;
import static org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors.USER;
import static org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors.USER_CREDENTIALS;
import static org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors.WEBSOCKET_CONNECTION;
import static org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityDescriptors.WEBSOCKET_SUBSCRIPTION;

class RocksEntityStoreTest {

    @TempDir
    Path dir;

    private RecordingRocksDbStore rocksDbStore;
    private RocksEntityStore store;

    @BeforeEach
    void open() {
        StoreProperties properties = new StoreProperties();
        properties.setPath(dir.toString());
        rocksDbStore = new RecordingRocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0));
        rocksDbStore.open();
        store = new RocksEntityStore(rocksDbStore);
    }

    @AfterEach
    void close() {
        rocksDbStore.close();
    }

    @Test
    void saveAssignsAnIdAndCreatedTimeAndFindReadsItBack() {
        long before = System.currentTimeMillis();
        User user = user("a@example.org");

        User saved = store.save(USER, user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedTime()).isBetween(before, System.currentTimeMillis());
        assertThat(user.getId()).as("the argument is not modified").isNull();
        User found = store.findById(USER, saved.getId());
        assertThat(found).isEqualTo(saved).isNotSameAs(saved);
        assertThat(found.getEmail()).isEqualTo("a@example.org");
        assertThat(store.findById(USER, UUID.randomUUID())).isNull();
        assertThat(rocksDbStore.takeWriteClasses()).containsExactly(StoreWriteClass.NEW_DATA);
    }

    @Test
    void aSaveWithAnIdIsAnUpsertThatKeepsItsCreatedTime() {
        UUID id = UUID.randomUUID();
        User user = user("a@example.org");
        user.setId(id);
        user.setCreatedTime(42L);

        store.save(USER, user);
        user.setFirstName("Changed");
        store.save(USER, user);

        assertThat(store.findAll(USER)).singleElement().satisfies(found -> {
            assertThat(found.getId()).isEqualTo(id);
            assertThat(found.getCreatedTime()).isEqualTo(42L);
            assertThat(found.getFirstName()).isEqualTo("Changed");
        });
        assertThat(rocksDbStore.takeWriteClasses()).containsExactly(StoreWriteClass.NEW_DATA, StoreWriteClass.NEW_DATA);
    }

    @Test
    void aUniqueValueOnAnotherIdIsRefusedWithTheSqlConstraintName() {
        UUID userId = UUID.randomUUID();
        store.save(USER_CREDENTIALS, creds(userId, "tok"));

        assertThatThrownBy(() -> store.save(USER_CREDENTIALS, creds(userId, "other")))
                .isInstanceOf(EntityConstraintViolationException.class)
                .extracting(e -> ((EntityConstraintViolationException) e).getConstraintName()).isEqualTo("user_credentials_user_id_key");
        assertThat(store.findAll(USER_CREDENTIALS)).hasSize(1);
        assertThat(store.findByUnique(USER_CREDENTIALS, EntityIndex.USER_CREDENTIALS_ACTIVATE_TOKEN, "other")).isNull();
    }

    @Test
    void savingADocumentAgainKeepsItsOwnUniqueValues() {
        UserCredentials saved = store.save(USER_CREDENTIALS, creds(UUID.randomUUID(), "tok"));
        saved.setEnabled(true);

        store.save(USER_CREDENTIALS, saved);

        assertThat(store.findByUnique(USER_CREDENTIALS, EntityIndex.USER_CREDENTIALS_ACTIVATE_TOKEN, "tok").isEnabled()).isTrue();
    }

    @Test
    void changingAnIndexedValueMovesItsIndexEntry() {
        User saved = store.save(USER, user("a@example.org"));
        saved.setEmail("b@example.org");
        store.save(USER, saved);

        assertThat(store.findByUnique(USER, EntityIndex.USER_EMAIL, "a@example.org")).isNull();
        assertThat(store.existsByIndex(USER, EntityIndex.USER_EMAIL, "a@example.org")).isFalse();
        assertThat(store.findByUnique(USER, EntityIndex.USER_EMAIL, "b@example.org").getId()).isEqualTo(saved.getId());

        User other = store.save(USER, user("a@example.org"));
        assertThat(store.findByUnique(USER, EntityIndex.USER_EMAIL, "a@example.org").getId()).isEqualTo(other.getId());
    }

    @Test
    void aValueSetToNullLeavesNoIndexEntry() {
        UserCredentials saved = store.save(USER_CREDENTIALS, creds(UUID.randomUUID(), "tok"));
        saved.setActivateToken(null);
        store.save(USER_CREDENTIALS, saved);

        assertThat(store.existsByIndex(USER_CREDENTIALS, EntityIndex.USER_CREDENTIALS_ACTIVATE_TOKEN, "tok")).isFalse();
        assertThat(store.save(USER_CREDENTIALS, creds(UUID.randomUUID(), "tok")).getId()).isNotEqualTo(saved.getId());
    }

    @Test
    void aNonUniqueIndexFindsEveryDocumentWithTheValue() {
        MqttClientCredentials first = store.save(MQTT_CLIENT_CREDENTIALS, credentials("id-1", "shared", ClientCredentialsType.MQTT_BASIC));
        MqttClientCredentials second = store.save(MQTT_CLIENT_CREDENTIALS, credentials("id-2", "shared", ClientCredentialsType.X_509));
        store.save(MQTT_CLIENT_CREDENTIALS, credentials("id-3", "shared-not", ClientCredentialsType.MQTT_BASIC));

        assertThat(store.findByIndex(MQTT_CLIENT_CREDENTIALS, EntityIndex.CREDENTIALS_NAME, "shared"))
                .extracting(MqttClientCredentials::getId).containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(store.findByIndex(MQTT_CLIENT_CREDENTIALS, EntityIndex.CREDENTIALS_TYPE, "X_509"))
                .extracting(MqttClientCredentials::getId).containsExactly(second.getId());
        assertThat(store.existsByIndex(MQTT_CLIENT_CREDENTIALS, EntityIndex.CREDENTIALS_TYPE, "SCRAM")).isFalse();
        assertThat(store.existsByIndex(MQTT_CLIENT_CREDENTIALS, EntityIndex.CREDENTIALS_TYPE, "MQTT_BASIC")).isTrue();
        assertThat(store.findByIndex(MQTT_CLIENT_CREDENTIALS, EntityIndex.CREDENTIALS_ID, "id-1"))
                .extracting(MqttClientCredentials::getId).containsExactly(first.getId());
    }

    @Test
    void anIndexTheTypeDoesNotHaveIsRejected() {
        assertThatThrownBy(() -> store.findByIndex(USER, EntityIndex.CREDENTIALS_NAME, "x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CREDENTIALS_NAME");
        assertThatThrownBy(() -> store.findByUnique(MQTT_CLIENT_CREDENTIALS, EntityIndex.CREDENTIALS_NAME, "x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not unique");
    }

    @Test
    void twoThreadsSavingTheSameUniqueValueLeaveExactlyOneDocument() throws Exception {
        int threads = 8;
        int savesPerThread = 200;
        AtomicInteger saved = new AtomicInteger();
        AtomicInteger violations = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < savesPerThread; i++) {
                        try {
                            store.save(MQTT_CLIENT_CREDENTIALS, credentials("dup", "name", ClientCredentialsType.MQTT_BASIC));
                            saved.incrementAndGet();
                        } catch (EntityConstraintViolationException e) {
                            assertThat(e.getConstraintName()).isEqualTo("mqtt_client_credentials_id_unq_key");
                            violations.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(saved).hasValue(1);
        assertThat(violations).hasValue(threads * savesPerThread - 1);
        assertThat(store.findAll(MQTT_CLIENT_CREDENTIALS)).hasSize(1);
        assertThat(indexEntries(EntityIndex.CREDENTIALS_ID)).isEqualTo(1);
        assertThat(indexEntries(EntityIndex.CREDENTIALS_NAME)).isEqualTo(1);
    }

    @Test
    void deletingAUserCascadesItsConnectionsAndTheirSubscriptionsInOneBatch() {
        User user = store.save(USER, user("a@example.org"));
        User otherUser = store.save(USER, user("b@example.org"));
        for (int c = 0; c < 2; c++) {
            WebSocketConnection connection = store.save(WEBSOCKET_CONNECTION, connection(user.getId(), "conn-" + c));
            for (int s = 0; s < 3; s++) {
                store.save(WEBSOCKET_SUBSCRIPTION, subscription(connection.getId()));
            }
        }
        WebSocketConnection kept = store.save(WEBSOCKET_CONNECTION, connection(otherUser.getId(), "conn-0"));
        WebSocketSubscription keptSubscription = store.save(WEBSOCKET_SUBSCRIPTION, subscription(kept.getId()));
        rocksDbStore.takeWriteClasses();

        assertThat(store.removeById(USER, user.getId())).isTrue();

        assertThat(rocksDbStore.takeWriteClasses()).containsExactly(StoreWriteClass.DELETE);
        assertThat(store.findById(USER, user.getId())).isNull();
        assertThat(store.findAll(WEBSOCKET_CONNECTION)).extracting(WebSocketConnection::getId).containsExactly(kept.getId());
        assertThat(store.findAll(WEBSOCKET_SUBSCRIPTION)).extracting(WebSocketSubscription::getId).containsExactly(keptSubscription.getId());
        assertThat(store.existsByIndex(USER, EntityIndex.USER_EMAIL, "a@example.org")).isFalse();
        // only the other user's connection (two entries) and subscription (one entry) are left in codes 10, 11 and 12
        assertThat(indexEntries(EntityIndex.WS_CONNECTION_USER_NAME, EntityIndex.WS_SUBSCRIPTION_CONNECTION)).isEqualTo(3);
        assertThat(store.findByIndex(WEBSOCKET_CONNECTION, EntityIndex.WS_CONNECTION_USER, user.getId().toString())).isEmpty();
    }

    @Test
    void aConnectionNameIsUniquePerUser() {
        UUID userId = UUID.randomUUID();
        store.save(WEBSOCKET_CONNECTION, connection(userId, "conn"));
        store.save(WEBSOCKET_CONNECTION, connection(UUID.randomUUID(), "conn"));

        assertThatThrownBy(() -> store.save(WEBSOCKET_CONNECTION, connection(userId, "conn")))
                .isInstanceOf(EntityConstraintViolationException.class)
                .extracting(e -> ((EntityConstraintViolationException) e).getConstraintName()).isEqualTo("name_unq_key");
        assertThat(store.findByUnique(WEBSOCKET_CONNECTION, EntityIndex.WS_CONNECTION_USER_NAME, userId + "\0conn").getUserId())
                .isEqualTo(userId);
    }

    @Test
    void aRemovedIdReturnsFalse() {
        User user = store.save(USER, user("a@example.org"));
        rocksDbStore.takeWriteClasses();

        assertThat(store.removeById(USER, user.getId())).isTrue();
        assertThat(store.removeById(USER, user.getId())).isFalse();
        assertThat(store.removeById(USER, UUID.randomUUID())).isFalse();
        assertThat(rocksDbStore.takeWriteClasses()).containsExactly(StoreWriteClass.DELETE);
    }

    @Test
    void removeAndSaveIsOneBatch() {
        UUID userId = UUID.randomUUID();
        UserCredentials old = store.save(USER_CREDENTIALS, creds(userId, "old-tok"));
        rocksDbStore.takeWriteClasses();

        UserCredentials replacement = store.removeAndSave(USER_CREDENTIALS, old.getId(), creds(userId, "new-tok"));

        assertThat(rocksDbStore.takeWriteClasses()).containsExactly(StoreWriteClass.NEW_DATA);
        assertThat(replacement.getId()).isNotNull().isNotEqualTo(old.getId());
        assertThat(store.findById(USER_CREDENTIALS, old.getId())).isNull();
        assertThat(store.existsByIndex(USER_CREDENTIALS, EntityIndex.USER_CREDENTIALS_ACTIVATE_TOKEN, "old-tok")).isFalse();
        assertThat(store.findByUnique(USER_CREDENTIALS, EntityIndex.USER_CREDENTIALS_ACTIVATE_TOKEN, "new-tok").getId())
                .isEqualTo(replacement.getId());
        assertThat(store.findByUnique(USER_CREDENTIALS, EntityIndex.USER_CREDENTIALS_USER_ID, userId.toString()).getId())
                .isEqualTo(replacement.getId());
        assertThat(store.findAll(USER_CREDENTIALS)).hasSize(1);
    }

    @Test
    void removeAndSaveStillRefusesAUniqueValueOfAThirdDocument() {
        UUID userId = UUID.randomUUID();
        UserCredentials old = store.save(USER_CREDENTIALS, creds(userId, "old-tok"));
        store.save(USER_CREDENTIALS, creds(UUID.randomUUID(), "taken"));

        assertThatThrownBy(() -> store.removeAndSave(USER_CREDENTIALS, old.getId(), creds(userId, "taken")))
                .isInstanceOf(EntityConstraintViolationException.class)
                .extracting(e -> ((EntityConstraintViolationException) e).getConstraintName()).isEqualTo("user_credentials_activate_token_key");
        assertThat(store.findById(USER_CREDENTIALS, old.getId())).isNotNull();
    }

    @Test
    void anUndecodableDocumentNamesItsTypeAndId() {
        UUID id = UUID.randomUUID();
        rocksDbStore.put(StoreWriteClass.NEW_DATA, StoreColumnFamily.ENTITY,
                EntityKeys.entityKey(StoredEntityType.USER, EntityKeys.uuidBytes(id)), new byte[]{1, '{', 'x'});

        assertThatThrownBy(() -> store.findById(USER, id))
                .isInstanceOf(StoreException.class).hasMessageContaining("USER").hasMessageContaining(id.toString());
        assertThatThrownBy(() -> store.findAll(USER))
                .isInstanceOf(StoreException.class).hasMessageContaining("USER").hasMessageContaining(id.toString());
    }

    @Test
    void unauthorizedClientsAreKeyedByClientId() {
        store.save(UNAUTHORIZED_CLIENT, unauthorized("client-é", "first"));
        store.save(UNAUTHORIZED_CLIENT, unauthorized("client-é", "second"));

        assertThat(store.findAll(UNAUTHORIZED_CLIENT)).singleElement()
                .extracting(UnauthorizedClient::getReason).isEqualTo("second");
        assertThat(store.findById(UNAUTHORIZED_CLIENT, "client-é").getReason()).isEqualTo("second");
        assertThat(store.removeById(UNAUTHORIZED_CLIENT, "client-é")).isTrue();
        assertThat(store.findAll(UNAUTHORIZED_CLIENT)).isEmpty();
    }

    @Test
    void anUnauthorizedClientWithoutAClientIdIsRejected() {
        assertThatThrownBy(() -> store.save(UNAUTHORIZED_CLIENT, unauthorized(null, "r")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("UNAUTHORIZED_CLIENT");
    }

    private int indexEntries(EntityIndex index) {
        return indexEntries(index, index);
    }

    // the entries of the indexes from first to last, both included
    private int indexEntries(EntityIndex first, EntityIndex last) {
        return rocksDbStore.scan(StoreColumnFamily.ENTITY_INDEX, new byte[]{first.code()}, new byte[]{(byte) (last.code() + 1)}, it -> {
            int n = 0;
            for (; it.isValid(); it.next()) {
                n++;
            }
            return n;
        });
    }

    private static User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("First");
        return user;
    }

    private static UserCredentials creds(UUID userId, String activateToken) {
        UserCredentials credentials = new UserCredentials();
        credentials.setUserId(userId);
        credentials.setActivateToken(activateToken);
        return credentials;
    }

    private static MqttClientCredentials credentials(String credentialsId, String name, ClientCredentialsType type) {
        MqttClientCredentials credentials = new MqttClientCredentials();
        credentials.setCredentialsId(credentialsId);
        credentials.setName(name);
        credentials.setCredentialsType(type);
        return credentials;
    }

    private static WebSocketConnection connection(UUID userId, String name) {
        WebSocketConnection connection = new WebSocketConnection();
        connection.setUserId(userId);
        connection.setName(name);
        return connection;
    }

    private static WebSocketSubscription subscription(UUID connectionId) {
        WebSocketSubscription subscription = new WebSocketSubscription();
        subscription.setWebSocketConnectionId(connectionId);
        return subscription;
    }

    private static UnauthorizedClient unauthorized(String clientId, String reason) {
        return UnauthorizedClient.builder().clientId(clientId).reason(reason).ts(1L).build();
    }

}
