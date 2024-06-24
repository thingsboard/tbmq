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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.device.processing;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.dao.DbConnectionChecker;
import org.thingsboard.mqtt.broker.dao.client.device.DevicePacketIdAndSerialNumberService;
import org.thingsboard.mqtt.broker.dao.client.device.PacketIdAndSerialNumber;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.dto.PacketIdAndSerialNumberDto;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.processing.downlink.DownLinkProxy;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
public class DeviceMsgProcessorImplTest {

    @MockBean
    ClientSessionCache clientSessionCache;
    @MockBean
    ClientLogger clientLogger;
    @MockBean
    DbConnectionChecker dbConnectionChecker;
    @MockBean
    DownLinkProxy downLinkProxy;
    @MockBean
    DeviceMsgAcknowledgeStrategyFactory ackStrategyFactory;
    @MockBean
    DeviceMsgService deviceMsgService;
    @MockBean
    DevicePacketIdAndSerialNumberService serialNumberService;

    @SpyBean
    DeviceMsgProcessorImpl deviceMsgProcessor;

    private Map<String, PacketIdAndSerialNumber> mockMap;

    @Before
    public void setUp() {
        mockMap = new HashMap<>(Map.of("client1", PacketIdAndSerialNumber.initialInstance()));
    }

    @Test
    public void testGetAndIncrementPacketIdAndSerialNumber_NewClient() {
        PacketIdAndSerialNumberDto result = deviceMsgProcessor.getAndIncrementPacketIdAndSerialNumber(mockMap, "client1");

        assertEquals(1, result.getPacketId());
        assertEquals(0, result.getSerialNumber());

        result = deviceMsgProcessor.getAndIncrementPacketIdAndSerialNumber(mockMap, "client1");

        assertEquals(2, result.getPacketId());
        assertEquals(1, result.getSerialNumber());

        result = deviceMsgProcessor.getAndIncrementPacketIdAndSerialNumber(mockMap, "client1");

        assertEquals(3, result.getPacketId());
        assertEquals(2, result.getSerialNumber());
    }

    @Test
    public void testGetAndIncrementPacketIdAndSerialNumber_ExistingClient() {
        PacketIdAndSerialNumber existingPacketIdAndSerialNumber = new PacketIdAndSerialNumber(new AtomicInteger(50), new AtomicLong(60));
        mockMap = new HashMap<>(Map.of("client1", existingPacketIdAndSerialNumber));

        PacketIdAndSerialNumberDto result = deviceMsgProcessor.getAndIncrementPacketIdAndSerialNumber(mockMap, "client1");

        assertEquals(51, result.getPacketId());
        assertEquals(61, result.getSerialNumber());

        result = deviceMsgProcessor.getAndIncrementPacketIdAndSerialNumber(mockMap, "client1");

        assertEquals(52, result.getPacketId());
        assertEquals(62, result.getSerialNumber());
    }

    @Test
    public void testGetAndIncrementPacketIdAndSerialNumber_WrapAround() {
        PacketIdAndSerialNumber existingPacketIdAndSerialNumber = new PacketIdAndSerialNumber(new AtomicInteger(0xfffe), new AtomicLong(10));
        mockMap = new HashMap<>(Map.of("client1", existingPacketIdAndSerialNumber));

        PacketIdAndSerialNumberDto result = deviceMsgProcessor.getAndIncrementPacketIdAndSerialNumber(mockMap, "client1");

        assertEquals(0xffff, result.getPacketId());
        assertEquals(11, result.getSerialNumber());

        result = deviceMsgProcessor.getAndIncrementPacketIdAndSerialNumber(mockMap, "client1");

        assertEquals(1, result.getPacketId());
        assertEquals(12, result.getSerialNumber());

        result = deviceMsgProcessor.getAndIncrementPacketIdAndSerialNumber(mockMap, "client1");

        assertEquals(2, result.getPacketId());
        assertEquals(13, result.getSerialNumber());
    }
}
