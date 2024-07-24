/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao.messages.sql;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.client.device.DeviceSessionCtxRepository;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgCleanUpDao;
import org.thingsboard.mqtt.broker.dao.model.DeviceSessionCtxEntity;
import org.thingsboard.mqtt.broker.dao.model.sql.DevicePublishMsgEntity;
import org.thingsboard.mqtt.broker.dao.service.AbstractServiceTest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@DaoSqlTest
public class SqlDeviceMsgCleanUpDaoTest extends AbstractServiceTest {

    @Autowired
    private DeviceMsgRepository deviceMsgRepository;
    @Autowired
    private DeviceSessionCtxRepository deviceSessionCtxRepository;

    @Autowired
    private DeviceMsgCleanUpDao deviceMsgCleanUpDao;

    @Before
    public void setUp() {
        deviceMsgRepository.deleteAll();
        deviceSessionCtxRepository.deleteAll();

        DeviceSessionCtxEntity deviceSessionCtxEntity = new DeviceSessionCtxEntity();
        deviceSessionCtxEntity.setClientId("testClientId");
        deviceSessionCtxRepository.save(deviceSessionCtxEntity);

        for (int i = 0; i < 20; i++) {
            DevicePublishMsgEntity devicePublishMsgEntity = new DevicePublishMsgEntity();
            devicePublishMsgEntity.setClientId("testClientId");
            devicePublishMsgEntity.setSerialNumber((long) i);
            devicePublishMsgEntity.setTopic("testTopic");
            devicePublishMsgEntity.setTime(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(i));
            devicePublishMsgEntity.setPacketId(i + 1);
            devicePublishMsgEntity.setPacketType(PersistedPacketType.PUBLISH);
            devicePublishMsgEntity.setQos(1);
            devicePublishMsgEntity.setPayload("payload".getBytes(StandardCharsets.UTF_8));
            deviceMsgRepository.save(devicePublishMsgEntity);
        }
    }

    @After
    public void tearDown() {
        deviceMsgRepository.deleteAll();
        deviceSessionCtxRepository.deleteAll();
    }

    @Test
    public void testCleanUpByTime() {
        long ttl = 10 * 24 * 60 * 60; // 10 days in seconds

        deviceMsgCleanUpDao.cleanUpByTime(ttl);

        List<DevicePublishMsgEntity> remainingMessages = deviceMsgRepository.findAll();
        Assert.assertEquals(10, remainingMessages.size());

        for (DevicePublishMsgEntity msg : remainingMessages) {
            Assert.assertTrue(msg.getTime() > System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(ttl));
        }
    }

    @Test
    public void testCleanUpBySize() {
        int maxPersistedMessages = 10;

        deviceMsgCleanUpDao.cleanUpBySize(maxPersistedMessages);

        List<DevicePublishMsgEntity> remainingMessages = deviceMsgRepository.findAll();
        Assert.assertEquals(maxPersistedMessages, remainingMessages.size());

        for (int i = 0; i < maxPersistedMessages; i++) {
            Assert.assertEquals(i + 10, remainingMessages.get(i).getSerialNumber().intValue());
        }
    }

    @Test
    public void testCleanUpBySize_NoMessagesToRemove() {
        int maxPersistedMessages = 20;

        deviceMsgCleanUpDao.cleanUpBySize(maxPersistedMessages);

        List<DevicePublishMsgEntity> remainingMessages = deviceMsgRepository.findAll();
        Assert.assertEquals(20, remainingMessages.size());
    }

}
