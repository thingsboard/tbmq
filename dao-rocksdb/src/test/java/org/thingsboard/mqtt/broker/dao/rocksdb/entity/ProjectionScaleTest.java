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

import com.sun.management.HotSpotDiagnosticMXBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreColumnFamily;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMemoryBudget;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreWriteClass;
import org.thingsboard.mqtt.broker.dao.rocksdb.client.RocksDbUnauthorizedClientDao;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.thingsboard.mqtt.broker.dao.rocksdb.disk.TestDiskSpaceMonitors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The projection of 100,000 credentials: built at startup in seconds, small enough for the heap budget, and paged
 * without reading the store beyond the page. The projection of the most unauthorized clients the store keeps: small
 * enough for the heap budget too. The time bounds leave room for a loaded CI agent; the size bounds are the real ones.
 * A size is the growth of the used heap across the build, each side measured after a full collection. It is only
 * meaningful with compressed references (the module's surefire argLine pins the heap for that): without them every
 * reference doubles and so does much of the size.
 */
class ProjectionScaleTest {

    private static final int CREDENTIALS = 100_000;
    private static final int BATCH = 10_000;
    private static final long MAX_PROJECTION_BYTES = 40L << 20;
    private static final int UNAUTHORIZED_CLIENTS = 10_000;
    // measured 34.1 to 34.2 MB; the bound (44.0 MB) leaves about 29% for another JDK or GC
    private static final long MAX_UNAUTHORIZED_PROJECTION_BYTES = 42L << 20;
    // measured 1.03 MB: the order shares the client ids of the projection; the bound (1.31 MB) leaves about 27%
    private static final long MAX_UNAUTHORIZED_ORDER_BYTES = (5L << 20) / 4;

    @TempDir
    Path dir;

    private RocksDbStore rocksDbStore;

    @AfterEach
    void close() {
        rocksDbStore.close();
    }

    @Test
    void oneHundredThousandCredentialsBuildQuicklyAndStaySmall() {
        assertCompressedOops();
        open();
        writeCredentials();
        rocksDbStore.close();
        open();
        RocksEntityStore store = new RocksEntityStore(rocksDbStore);

        long heapBefore = usedHeapAfterFullGc();
        long buildStart = System.nanoTime();
        store.buildProjections();
        long buildMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - buildStart);
        long projectionBytes = usedHeapAfterFullGc() - heapBefore;

        long pageStart = System.nanoTime();
        PageData<MqttClientCredentials> page = store.findPage(EntityDescriptors.MQTT_CLIENT_CREDENTIALS,
                new PageLink(10, 3, "device-0", new SortOrder("name", SortOrder.Direction.DESC)), null);
        long pageMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - pageStart);

        assertThat(store.count(EntityDescriptors.MQTT_CLIENT_CREDENTIALS)).isEqualTo(CREDENTIALS);
        assertThat(page.getTotalElements()).isEqualTo(CREDENTIALS);
        assertThat(page.getData()).extracting(MqttClientCredentials::getName).first().isEqualTo(name(CREDENTIALS - 31));
        assertThat(buildMillis).as("projection build time, ms").isLessThan(30_000);
        assertThat(projectionBytes).as("retained projection size, bytes").isLessThan(MAX_PROJECTION_BYTES);
        assertThat(pageMillis).as("10-item page time, ms").isLessThan(1_000);
    }

    /**
     * The count limit of unauthorized clients at its default, each of them as large as a refusal can make it: a client
     * id and a username at the 255 characters {@code StringUtils.abbreviate} cuts them to, a reason at the 1024 characters
     * the DAO cuts it to, all three outside Latin-1 so that each character takes two bytes, and the longest IPv6 text.
     * Measures the projection and, on top of it, the age order of the DAO.
     */
    @Test
    void tenThousandWorstCaseUnauthorizedClientsStaySmall() {
        assertCompressedOops();
        open();
        writeUnauthorizedClients();
        rocksDbStore.close();
        open();
        RocksEntityStore store = new RocksEntityStore(rocksDbStore);

        long heapBefore = usedHeapAfterFullGc();
        store.buildProjections();
        long projectionBytes = usedHeapAfterFullGc() - heapBefore;
        RocksDbUnauthorizedClientDao dao = new RocksDbUnauthorizedClientDao(store, UNAUTHORIZED_CLIENTS);
        long daoBytes = usedHeapAfterFullGc() - heapBefore - projectionBytes;

        assertThat(store.count(EntityDescriptors.UNAUTHORIZED_CLIENT)).isEqualTo(UNAUTHORIZED_CLIENTS);
        assertThat(dao.findByClientId(unauthorizedClientId(0))).isNotNull();
        assertThat(projectionBytes).as("retained projection size, bytes").isLessThan(MAX_UNAUTHORIZED_PROJECTION_BYTES);
        assertThat(daoBytes).as("retained DAO age order size, bytes").isLessThan(MAX_UNAUTHORIZED_ORDER_BYTES);
    }

    private void writeUnauthorizedClients() {
        rocksDbStore.write(StoreWriteClass.NEW_DATA, batch -> {
            for (int i = 0; i < UNAUTHORIZED_CLIENTS; i++) {
                UnauthorizedClient client = UnauthorizedClient.builder()
                        .clientId(unauthorizedClientId(i))
                        .username(String.format("%s%05d", "\u043a".repeat(250), i))
                        .ipAddress("ffff:ffff:ffff:ffff:ffff:ffff:255.255.255.255")
                        .ts(1_700_000_000_000L + i)
                        .passwordProvided(true)
                        .tlsUsed(true)
                        .reason(String.format("%05d", i) + "\u0440".repeat(1019))
                        .build();
                batch.put(StoreColumnFamily.ENTITY, EntityKeys.entityKey(StoredEntityType.UNAUTHORIZED_CLIENT,
                        EntityDescriptors.UNAUTHORIZED_CLIENT.idBytes(client.getClientId())), StorageObjectMapper.write(client));
            }
        });
    }

    private static String unauthorizedClientId(int i) {
        return String.format("%s%05d", "\u0436".repeat(250), i);
    }

    private static void assertCompressedOops() {
        assertThat(ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class).getVMOption("UseCompressedOops").getValue())
                .as("UseCompressedOops: the size bound assumes compressed references; keep -Xmx below 32g in the surefire argLine")
                .isEqualTo("true");
    }

    // realistic MQTT_BASIC credentials: a user name and a client id, a hashed password and one rule each way
    private void writeCredentials() {
        for (int from = 0; from < CREDENTIALS; from += BATCH) {
            int first = from;
            rocksDbStore.write(StoreWriteClass.NEW_DATA, batch -> {
                for (int i = first; i < first + BATCH; i++) {
                    MqttClientCredentials credentials = credentials(i);
                    batch.put(StoreColumnFamily.ENTITY, EntityKeys.entityKey(StoredEntityType.MQTT_CLIENT_CREDENTIALS,
                            EntityKeys.uuidBytes(credentials.getId())), StorageObjectMapper.write(credentials));
                }
            });
        }
    }

    private static MqttClientCredentials credentials(int i) {
        String userName = String.format("user-%06d", i);
        String clientId = String.format("client-%06d", i);
        MqttClientCredentials credentials = new MqttClientCredentials();
        credentials.setId(UUID.randomUUID());
        credentials.setCreatedTime(1_700_000_000_000L + i);
        credentials.setName(name(i));
        credentials.setCredentialsId("mixed|" + userName + "|" + clientId);
        credentials.setClientType(ClientType.DEVICE);
        credentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        credentials.setCredentialsValue("{\"clientId\":\"" + clientId + "\",\"userName\":\"" + userName + "\","
                + "\"password\":\"$2a$10$8QxW3kU1HSi0L7tEo6l1D.5qTzv8l0H8m1Z2nWJ8VgR3m0w7fGq1a\","
                + "\"authRules\":{\"pubAuthRulePatterns\":[\"devices/.*\"],\"subAuthRulePatterns\":[\"commands/.*\"]}}");
        return credentials;
    }

    private static String name(int i) {
        return String.format("device-%06d", i);
    }

    private void open() {
        StoreProperties properties = new StoreProperties();
        properties.setPath(dir.toString());
        rocksDbStore = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0), TestDiskSpaceMonitors.roomy(properties));
        rocksDbStore.open();
    }

    private static long usedHeapAfterFullGc() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        for (int i = 0; i < 3; i++) {
            memory.gc();
        }
        return memory.getHeapMemoryUsage().getUsed();
    }

}
