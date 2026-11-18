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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thingsboard.mqtt.broker.common.data.ClientType;
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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The projection of 100,000 credentials: built at startup in seconds, small enough for the heap budget, and paged
 * without reading the store beyond the page. The time bounds leave room for a loaded CI agent; the size bound is the
 * real one. The size is the growth of the used heap across the build, each side measured after a full collection.
 */
class ProjectionScaleTest {

    private static final int CREDENTIALS = 100_000;
    private static final int BATCH = 10_000;
    private static final long MAX_PROJECTION_BYTES = 40L << 20;

    @TempDir
    Path dir;

    private RocksDbStore rocksDbStore;

    @AfterEach
    void close() {
        rocksDbStore.close();
    }

    @Test
    void oneHundredThousandCredentialsBuildQuicklyAndStaySmall() {
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
        rocksDbStore = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0));
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
