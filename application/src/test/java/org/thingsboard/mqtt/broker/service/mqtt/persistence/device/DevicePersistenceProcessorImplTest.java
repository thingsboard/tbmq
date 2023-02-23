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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.device;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.dao.client.device.DeviceSessionCtxService;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DevicePersistenceProcessorImplTest {

    DeviceMsgService deviceMsgService;
    DeviceSessionCtxService deviceSessionCtxService;
    DeviceActorManager deviceActorManager;
    CacheManager cacheManager;
    DevicePersistenceProcessorImpl devicePersistenceProcessor;

    String clientId;

    @Before
    public void setUp() {
        deviceMsgService = mock(DeviceMsgService.class);
        deviceSessionCtxService = mock(DeviceSessionCtxService.class);
        deviceActorManager = mock(DeviceActorManager.class);
        cacheManager = mock(CacheManager.class);
        devicePersistenceProcessor = spy(new DevicePersistenceProcessorImpl(
                deviceMsgService, deviceSessionCtxService, deviceActorManager, cacheManager));

        clientId = "clientId";
    }

    @Test
    public void clearPersistedMsgsTest() {
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache(CacheConstants.PACKET_ID_AND_SERIAL_NUMBER_CACHE)).thenReturn(cache);

        devicePersistenceProcessor.clearPersistedMsgs(clientId);

        verify(deviceMsgService, times(1)).removePersistedMessages(eq(clientId));
        verify(deviceSessionCtxService, times(1)).removeDeviceSessionContext(eq(clientId));
        verify(cacheManager, times(1)).getCache(eq(CacheConstants.PACKET_ID_AND_SERIAL_NUMBER_CACHE));
        verify(cache, times(1)).evict(eq(clientId));
    }

    @Test
    public void processPubAckTest() {
        devicePersistenceProcessor.processPubAck(clientId, 1);

        verify(deviceActorManager, times(1)).notifyPacketAcknowledged(eq(clientId), eq(1));
    }

    @Test
    public void processPubRecTest() {
        devicePersistenceProcessor.processPubRec(clientId, 1);

        verify(deviceActorManager, times(1)).notifyPacketReceived(eq(clientId), eq(1));
    }

    @Test
    public void processPubCompTest() {
        devicePersistenceProcessor.processPubComp(clientId, 1);

        verify(deviceActorManager, times(1)).notifyPacketCompleted(eq(clientId), eq(1));
    }

    @Test
    public void startProcessingPersistedMessagesTest() {
        ClientSessionCtx clientSessionCtx = mock(ClientSessionCtx.class);

        devicePersistenceProcessor.startProcessingPersistedMessages(clientSessionCtx);

        verify(deviceActorManager, times(1)).notifyClientConnected(eq(clientSessionCtx));
    }

    @Test
    public void stopProcessingPersistedMessagesTest() {
        devicePersistenceProcessor.stopProcessingPersistedMessages(clientId);

        verify(deviceActorManager, times(1)).notifyClientDisconnected(eq(clientId));
    }
}