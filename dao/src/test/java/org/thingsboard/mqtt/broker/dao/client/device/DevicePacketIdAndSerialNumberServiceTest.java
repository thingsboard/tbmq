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

    static final String CLIENT_ID = "clientId";

    @Autowired
    private DevicePacketIdAndSerialNumberService packetIdAndSerialNumberService;
    @Autowired
    private CacheManager cacheManager;

    Cache cache;

    @Before
    public void setUp() {
        cache = cacheManager.getCache(CacheConstants.PACKET_ID_AND_SERIAL_NUMBER_CACHE);
    }

    // TODO: 04/06/2022 add more test cases

    @Test
    public void testCachePersistence() {
        Objects.requireNonNull(cache, "Cache manager is null").evict(CLIENT_ID);

        Map<String, PacketIdAndSerialNumber> toSaveMap = Map.of(CLIENT_ID, PacketIdAndSerialNumber.of(10, 100));
        packetIdAndSerialNumberService.saveLastSerialNumbers(toSaveMap);

        PacketIdAndSerialNumber fromCache = getFromCache();
        Assert.assertNull(fromCache);

        Map<String, PacketIdAndSerialNumber> map = packetIdAndSerialNumberService.getLastPacketIdAndSerialNumber(Set.of(CLIENT_ID));
        Assert.assertEquals(1, map.size());
        Assert.assertEquals(10, map.get(CLIENT_ID).getPacketId().get());
        Assert.assertEquals(100, map.get(CLIENT_ID).getSerialNumber().get());

        fromCache = getFromCache();
        Assert.assertNotNull(fromCache);
        Assert.assertEquals(10, fromCache.getPacketId().get());
        Assert.assertEquals(100, fromCache.getSerialNumber().get());

        toSaveMap = Map.of(CLIENT_ID, PacketIdAndSerialNumber.of(20, 200));
        packetIdAndSerialNumberService.saveLastSerialNumbers(toSaveMap);

        fromCache = getFromCache();
        Assert.assertNull(fromCache);
    }

    private PacketIdAndSerialNumber getFromCache() {
        return cache.get(CLIENT_ID, PacketIdAndSerialNumber.class);
    }


}