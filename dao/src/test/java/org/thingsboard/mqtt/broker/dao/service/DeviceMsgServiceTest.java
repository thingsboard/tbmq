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
package org.thingsboard.mqtt.broker.dao.service;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@DaoSqlTest
public class DeviceMsgServiceTest extends AbstractServiceTest {
    @Autowired
    private DeviceMsgService deviceMsgService;

    private static final String TEST_CLIENT_ID = "testClientId";
    private static final byte[] TEST_PAYLOAD = "testPayload".getBytes();
    private static final List<DevicePublishMsg> TEST_MESSAGES = Arrays.asList(
            new DevicePublishMsg(TEST_CLIENT_ID, UUID.randomUUID().toString(), 0L, 0L, 0, 0, PersistedPacketType.PUBLISH, TEST_PAYLOAD),
            new DevicePublishMsg(TEST_CLIENT_ID, UUID.randomUUID().toString(), 1L, 0L, 0, 0, PersistedPacketType.PUBLISH, TEST_PAYLOAD),
            new DevicePublishMsg(TEST_CLIENT_ID, UUID.randomUUID().toString(), 2L, 0L, 0, 0, PersistedPacketType.PUBLISH, TEST_PAYLOAD),
            new DevicePublishMsg(TEST_CLIENT_ID, UUID.randomUUID().toString(), 3L, 0L, 0, 0, PersistedPacketType.PUBLISH, TEST_PAYLOAD),
            new DevicePublishMsg(TEST_CLIENT_ID, UUID.randomUUID().toString(), 4L, 0L, 0, 0, PersistedPacketType.PUBLISH, TEST_PAYLOAD)
    );

    @After
    public void clearState() {
        deviceMsgService.removePersistedMessages(TEST_CLIENT_ID);
    }

    @Test
    public void testFindAllInRange() {
        Assert.assertTrue(deviceMsgService.findPersistedMessages(TEST_CLIENT_ID, 0, 5).isEmpty());
        deviceMsgService.save(TEST_MESSAGES);
        Assert.assertEquals(TEST_MESSAGES, deviceMsgService.findPersistedMessages(TEST_CLIENT_ID, 0, 5));
    }

    @Test
    public void testFindSomeInRange_1() {
        deviceMsgService.save(TEST_MESSAGES);
        Assert.assertEquals(TEST_MESSAGES.subList(0, 3), deviceMsgService.findPersistedMessages(TEST_CLIENT_ID, 0, 3));
    }

    @Test
    public void testFindSomeInRange_2() {
        deviceMsgService.save(TEST_MESSAGES);
        Assert.assertEquals(TEST_MESSAGES.subList(1, 3), deviceMsgService.findPersistedMessages(TEST_CLIENT_ID, 1, 3));
    }

    @Test
    public void testFindSomeInRange_3() {
        deviceMsgService.save(TEST_MESSAGES);
        Assert.assertEquals(TEST_MESSAGES.subList(3, 5), deviceMsgService.findPersistedMessages(TEST_CLIENT_ID, 3, 5));
    }

    @Test
    public void testFindNoneInRange() {
        deviceMsgService.save(TEST_MESSAGES);
        Assert.assertEquals(Collections.emptyList(), deviceMsgService.findPersistedMessages(TEST_CLIENT_ID, 5, 10));
    }
}
