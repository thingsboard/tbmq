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
package org.thingsboard.mqtt.broker.standalone.device;

import io.netty.handler.codec.mqtt.MqttProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.dao.rocksdb.RocksDbStore;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreMemoryBudget;
import org.thingsboard.mqtt.broker.dao.rocksdb.StoreProperties;
import org.thingsboard.mqtt.broker.dao.rocksdb.devicemsg.RocksDbDeviceMsgQueueStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class StandaloneDeviceMsgServiceTest {

    @TempDir
    Path dir;

    private RocksDbStore rocksDb;
    private StandaloneDeviceMsgService service;

    @BeforeEach
    void open() {
        StoreProperties properties = new StoreProperties();
        properties.setPath(dir.toString());
        rocksDb = new RocksDbStore(properties, StoreMemoryBudget.forContainerMemory(1L << 30, 0, 0));
        rocksDb.open();
        service = new StandaloneDeviceMsgService(new RocksDbDeviceMsgQueueStore(rocksDb, 10, 600, Clock.systemUTC()));
    }

    @AfterEach
    void close() {
        rocksDb.close();
    }

    @Test
    void removeLastPacketIdIsANoOpWhileMessagesAreStored() {          // Trello #654
        service.saveAndReturnPreviousPacketId("ss_g_t", msgs(2), false).toCompletableFuture().join();

        assertThat(service.removeLastPacketId("ss_g_t").toCompletableFuture().join()).isZero();
        assertThat(service.getLastPacketId("ss_g_t").toCompletableFuture().join()).isEqualTo(2);
    }

    @Test
    void failOnConflictIsIgnored() {
        assertThat(service.saveAndReturnPreviousPacketId("c1", msgs(2), true).toCompletableFuture().join()).isZero();
        assertThat(service.saveAndReturnPreviousPacketId("c1", msgs(1), true).toCompletableFuture().join()).isEqualTo(2);
    }

    @Test
    void theMessageExpiryIntervalIsKeptOnTheStoredMessage() {
        DevicePublishMsg msg = msgs(1).get(0);
        msg.setMsgExpiryInterval(30);

        service.saveAndReturnPreviousPacketId("c1", List.of(msg), false).toCompletableFuture().join();

        DevicePublishMsg found = service.findPersistedMessages("c1").toCompletableFuture().join().get(0);
        assertThat(found.getMsgExpiryInterval()).isEqualTo(30);
        assertThat(found.getTime()).isEqualTo(msg.getTime());
    }

    @Test
    void aStoreErrorBecomesAFailedStage() {
        rocksDb.close();

        assertThat(service.findPersistedMessages("c1")).failsWithin(Duration.ofSeconds(1));
        assertThat(service.saveAndReturnPreviousPacketId("c1", msgs(1), false)).failsWithin(Duration.ofSeconds(1));
        assertThat(service.getLastPacketId("c1")).failsWithin(Duration.ofSeconds(1));
    }

    private static List<DevicePublishMsg> msgs(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new DevicePublishMsg("c1", "t/" + i, System.currentTimeMillis(), 1, BrokerConstants.BLANK_PACKET_ID,
                        PersistedPacketType.PUBLISH, ("p" + i).getBytes(StandardCharsets.UTF_8), new MqttProperties(), false))
                .toList();
    }

}
