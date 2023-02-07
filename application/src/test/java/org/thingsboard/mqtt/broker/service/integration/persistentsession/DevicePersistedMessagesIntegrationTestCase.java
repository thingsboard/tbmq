/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.integration.persistentsession;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.AbstractPubSubIntegrationTest;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.device.DevicePacketIdAndSerialNumberService;
import org.thingsboard.mqtt.broker.dao.client.device.PacketIdAndSerialNumber;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.DevicePersistenceProcessor;

import java.util.Map;
import java.util.Set;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = DevicePersistedMessagesIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@DaoSqlTest
@RunWith(SpringRunner.class)
public class DevicePersistedMessagesIntegrationTestCase extends AbstractPubSubIntegrationTest {

    public static final String CLIENT_ID = "CLIENT_ID";

    @Autowired
    private DevicePacketIdAndSerialNumberService devicePacketIdAndSerialNumberService;
    @Autowired
    private DevicePersistenceProcessor devicePersistenceProcessor;
    @Autowired
    private CacheManager cacheManager;

    Cache packetIdSerialNumbCache;

    @Before
    public void init() {
        packetIdSerialNumbCache = cacheManager.getCache(CacheConstants.PACKET_ID_AND_SERIAL_NUMBER_CACHE);
    }

    @After
    public void clear() {
    }

    @Test
    public void testPacketIdAndSerialNumberCachePersistence() {
        PacketIdAndSerialNumber fromCache = getFromCache();
        Assert.assertNull(fromCache);

        PacketIdAndSerialNumber packetIdAndSerialNumber = PacketIdAndSerialNumber.newInstance(1, 0);
        devicePacketIdAndSerialNumberService.saveLastSerialNumbers(Map.of(CLIENT_ID, packetIdAndSerialNumber));
        fromCache = getFromCache();
        Assert.assertNotNull(fromCache);
        verifyEqualsFromDbAndCache(fromCache, packetIdAndSerialNumber);

        clearFromCache();

        Map<String, PacketIdAndSerialNumber> fromDb = getFromDb();
        fromCache = getFromCache();
        Assert.assertNotNull(fromDb);
        Assert.assertNotNull(fromCache);
        verifyEqualsFromDbAndCache(fromCache, fromDb.get(CLIENT_ID));

        devicePersistenceProcessor.clearPersistedMsgs(CLIENT_ID);
        fromCache = getFromCache();
        Assert.assertNull(fromCache);
        fromDb = getFromDb();
        Assert.assertTrue(fromDb.isEmpty());
    }

    private void verifyEqualsFromDbAndCache(PacketIdAndSerialNumber fromCache, PacketIdAndSerialNumber packetIdAndSerialNumber) {
        Assert.assertEquals(fromCache.getPacketId().get(), packetIdAndSerialNumber.getPacketId().get());
        Assert.assertEquals(fromCache.getSerialNumber().get(), packetIdAndSerialNumber.getSerialNumber().get());
    }

    private Map<String, PacketIdAndSerialNumber> getFromDb() {
        return devicePacketIdAndSerialNumberService.getLastPacketIdAndSerialNumber(Set.of(CLIENT_ID));
    }

    private PacketIdAndSerialNumber getFromCache() {
        return packetIdSerialNumbCache.get(CLIENT_ID, PacketIdAndSerialNumber.class);
    }

    private void clearFromCache() {
        packetIdSerialNumbCache.evict(CLIENT_ID);
    }

}
