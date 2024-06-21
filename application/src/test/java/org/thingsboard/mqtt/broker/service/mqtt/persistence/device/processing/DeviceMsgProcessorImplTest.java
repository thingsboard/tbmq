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
import org.mockito.Mockito;
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

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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
        mockMap = Mockito.mock(Map.class);
    }

    @Test
    public void testGetAndIncrementPacketIdAndSerialNumber_NewClient() {
        when(mockMap.computeIfAbsent(anyString(), any())).thenAnswer(invocation ->
                new PacketIdAndSerialNumber(new AtomicInteger(0), new AtomicLong(-1)));

        PacketIdAndSerialNumberDto result = deviceMsgProcessor.getAndIncrementPacketIdAndSerialNumber(mockMap, "client1");

        assertEquals(1, result.getPacketId());
        assertEquals(0, result.getSerialNumber());
    }

    @Test
    public void testGetAndIncrementPacketIdAndSerialNumber_ExistingClient() {
        PacketIdAndSerialNumber existingPacketIdAndSerialNumber = new PacketIdAndSerialNumber(new AtomicInteger(5), new AtomicLong(10));
        when(mockMap.computeIfAbsent(anyString(), any())).thenReturn(existingPacketIdAndSerialNumber);

        PacketIdAndSerialNumberDto result = deviceMsgProcessor.getAndIncrementPacketIdAndSerialNumber(mockMap, "client1");

        assertEquals(6, result.getPacketId());
        assertEquals(11, result.getSerialNumber());
    }

    @Test
    public void testGetAndIncrementPacketIdAndSerialNumber_WrapAround() {
        PacketIdAndSerialNumber existingPacketIdAndSerialNumber = new PacketIdAndSerialNumber(new AtomicInteger(0xffff), new AtomicLong(10));
        when(mockMap.computeIfAbsent(anyString(), any())).thenReturn(existingPacketIdAndSerialNumber);

        PacketIdAndSerialNumberDto result = deviceMsgProcessor.getAndIncrementPacketIdAndSerialNumber(mockMap, "client1");

//        assertEquals(1, result.getPacketId());
        assertEquals(11, result.getSerialNumber());
    }
}
