/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao.client.device;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.service.AbstractServiceTest;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

@DaoSqlTest
public class DevicePacketIdAndSerialNumberServiceTest extends AbstractServiceTest {

    static final String CLIENT_ID1 = "clientId1";
    static final String CLIENT_ID2 = "clientId2";

    @Autowired
    private DevicePacketIdAndSerialNumberService packetIdAndSerialNumberService;
    @Autowired
    private CacheManager cacheManager;

    Cache cache;

    @Before
    public void setUp() {
        cache = cacheManager.getCache(CacheConstants.PACKET_ID_AND_SERIAL_NUMBER_CACHE);
    }

    @Test
    public void testCachePersistence() {
        checkCacheNonNullAndEvict(CLIENT_ID1);

        Map<String, PacketIdAndSerialNumber> toSaveMap = prepareOneClientMapToSave(10, 100);
        saveToDbAndCache(toSaveMap);

        Map<String, PacketIdAndSerialNumber> map = getFromDb(CLIENT_ID1);
        assertResultFromDb(map, CLIENT_ID1, 10, 100);

        PacketIdAndSerialNumber fromCache = getFromCacheAndAssertNotNull(CLIENT_ID1);
        Assert.assertEquals(10, fromCache.getPacketId().get());
        Assert.assertEquals(100, fromCache.getSerialNumber().get());

        toSaveMap = prepareOneClientMapToSave(20, 200);
        saveToDbAndCache(toSaveMap);

        fromCache = getFromCacheAndAssertNotNull(CLIENT_ID1);
        Assert.assertEquals(20, fromCache.getPacketId().get());
        Assert.assertEquals(200, fromCache.getSerialNumber().get());
    }

    private Map<String, PacketIdAndSerialNumber> prepareOneClientMapToSave(int packetId, int serialNumber) {
        return Map.of(CLIENT_ID1, PacketIdAndSerialNumber.of(packetId, serialNumber));
    }

    @Test
    public void testCachePersistenceTwoClients() {
        checkCacheNonNullAndEvict(CLIENT_ID1);
        checkCacheNonNullAndEvict(CLIENT_ID2);

        Map<String, PacketIdAndSerialNumber> toSaveMap = prepareTwoClientsMapToSave(10, 20);
        saveToDbAndCache(toSaveMap);

        Map<String, PacketIdAndSerialNumber> map = getFromDb(CLIENT_ID1);
        assertResultFromDb(map, CLIENT_ID1, 10, 100);

        PacketIdAndSerialNumber fromCache = getFromCacheAndAssertNotNull(CLIENT_ID1);
        Assert.assertEquals(10, fromCache.getPacketId().get());
        Assert.assertEquals(100, fromCache.getSerialNumber().get());

        map = getFromDb(CLIENT_ID2);
        assertResultFromDb(map, CLIENT_ID2, 20, 200);

        fromCache = getFromCacheAndAssertNotNull(CLIENT_ID2);
        Assert.assertEquals(20, fromCache.getPacketId().get());
        Assert.assertEquals(200, fromCache.getSerialNumber().get());

        toSaveMap = prepareTwoClientsMapToSave(30, 40);
        saveToDbAndCache(toSaveMap);

        getFromCacheAndAssertNotNull(CLIENT_ID1);
        fromCache = getFromCacheAndAssertNotNull(CLIENT_ID2);

        Assert.assertEquals(40, fromCache.getPacketId().get());
        Assert.assertEquals(400, fromCache.getSerialNumber().get());
    }

    private void saveToDbAndCache(Map<String, PacketIdAndSerialNumber> toSaveMap) {
        packetIdAndSerialNumberService.saveLastSerialNumbers(toSaveMap);
    }

    private Map<String, PacketIdAndSerialNumber> getFromDb(String clientId) {
        return packetIdAndSerialNumberService.getLastPacketIdAndSerialNumber(Set.of(clientId));
    }

    private void assertResultFromDb(Map<String, PacketIdAndSerialNumber> map, String clientId, int expectedPacketId, int expectedSerialNumb) {
        Assert.assertEquals(1, map.size());
        Assert.assertEquals(expectedPacketId, map.get(clientId).getPacketId().get());
        Assert.assertEquals(expectedSerialNumb, map.get(clientId).getSerialNumber().get());
    }

    private Map<String, PacketIdAndSerialNumber> prepareTwoClientsMapToSave(int packetId1, int packetId2) {
        return Map.of(
                CLIENT_ID1, PacketIdAndSerialNumber.of(packetId1, packetId1 * 10L),
                CLIENT_ID2, PacketIdAndSerialNumber.of(packetId2, packetId2 * 10L)
        );
    }

    private PacketIdAndSerialNumber getFromCacheAndAssertNotNull(String clientId) {
        PacketIdAndSerialNumber fromCache = getFromCache(clientId);
        Assert.assertNotNull(fromCache);
        return fromCache;
    }

    private PacketIdAndSerialNumber getFromCache(String clientId) {
        return cache.get(clientId, PacketIdAndSerialNumber.class);
    }

    private void checkCacheNonNullAndEvict(String clientId) {
        Objects.requireNonNull(cache, "Cache manager is null").evict(clientId);
    }
}