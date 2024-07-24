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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.device;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cache.Cache;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.CacheNameResolver;
import org.thingsboard.mqtt.broker.dao.client.device.DeviceSessionCtxService;
import org.thingsboard.mqtt.broker.dao.messages.DeviceMsgService;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.Set;

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
    CacheNameResolver cacheNameResolver;
    DevicePersistenceProcessorImpl devicePersistenceProcessor;

    String clientId;

    @Before
    public void setUp() {
        deviceMsgService = mock(DeviceMsgService.class);
        deviceSessionCtxService = mock(DeviceSessionCtxService.class);
        deviceActorManager = mock(DeviceActorManager.class);
        cacheNameResolver = mock(CacheNameResolver.class);
        devicePersistenceProcessor = spy(new DevicePersistenceProcessorImpl(
                deviceMsgService, deviceSessionCtxService, deviceActorManager, cacheNameResolver));

        clientId = "clientId";
    }

    @Test
    public void clearPersistedMsgsTest() {
        Cache cache = mock(Cache.class);
        when(cacheNameResolver.getCache(CacheConstants.PACKET_ID_AND_SERIAL_NUMBER_CACHE)).thenReturn(cache);

        devicePersistenceProcessor.clearPersistedMsgs(clientId);

        verify(deviceMsgService, times(1)).removePersistedMessages(eq(clientId));
        verify(deviceSessionCtxService, times(1)).removeDeviceSessionContext(eq(clientId));
        verify(cacheNameResolver, times(1)).getCache(eq(CacheConstants.PACKET_ID_AND_SERIAL_NUMBER_CACHE));
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
    public void processPubRecNoPubRelDeliveryTest() {
        devicePersistenceProcessor.processPubRecNoPubRelDelivery(clientId, 1);

        verify(deviceActorManager, times(1)).notifyPacketReceivedNoDelivery(eq(clientId), eq(1));
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
    public void startProcessingSharedSubscriptionsTest() {
        ClientSessionCtx clientSessionCtx = mock(ClientSessionCtx.class);
        Set<TopicSharedSubscription> subscriptions = Set.of(new TopicSharedSubscription("tf", "sn"));

        devicePersistenceProcessor.startProcessingSharedSubscriptions(clientSessionCtx, subscriptions);

        verify(deviceActorManager, times(1)).notifySubscribeToSharedSubscriptions(eq(clientSessionCtx), eq(subscriptions));
    }

    @Test
    public void stopProcessingPersistedMessagesTest() {
        devicePersistenceProcessor.stopProcessingPersistedMessages(clientId);

        verify(deviceActorManager, times(1)).notifyClientDisconnected(eq(clientId));
    }
}
