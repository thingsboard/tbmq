/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.integration.service.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.thingsboard.mqtt.broker.common.data.JavaSerDesUtil;
import org.thingsboard.mqtt.broker.common.data.event.ErrorEvent;
import org.thingsboard.mqtt.broker.common.data.event.LifecycleEvent;
import org.thingsboard.mqtt.broker.common.data.event.StatisticsEvent;
import org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationEventProto;
import org.thingsboard.mqtt.broker.integration.api.IntegrationStatistics;
import org.thingsboard.mqtt.broker.integration.service.api.IntegrationApiService;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TbIntegrationExecutorEventStorageServiceTest {

    @Mock
    private ServiceInfoProvider serviceInfoProvider;
    @Mock
    private IntegrationApiService apiService;

    private TbIntegrationExecutorEventStorageService eventStorageService;
    private UUID entityId;

    AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        eventStorageService = new TbIntegrationExecutorEventStorageService(serviceInfoProvider, apiService);
        entityId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void givenEvent_whenPersistLifecycleEvent_thenVerifyResult() {
        when(serviceInfoProvider.getServiceId()).thenReturn("test-service");

        eventStorageService.persistLifecycleEvent(entityId, ComponentLifecycleEvent.CREATED, null);

        ArgumentCaptor<IntegrationEventProto> eventCaptor = ArgumentCaptor.forClass(IntegrationEventProto.class);
        verify(apiService, times(1)).sendEventData(eq(entityId), eventCaptor.capture(), any());

        IntegrationEventProto capturedEvent = eventCaptor.getValue();
        assertNotNull(capturedEvent);
        assertEquals(entityId.getMostSignificantBits(), capturedEvent.getEventSourceIdMSB());
        assertEquals(entityId.getLeastSignificantBits(), capturedEvent.getEventSourceIdLSB());

        LifecycleEvent event = JavaSerDesUtil.decode(capturedEvent.getEvent().toByteArray());
        assertEquals(entityId, event.getEntityId());
        assertEquals("test-service", event.getServiceId());
        assertEquals(ComponentLifecycleEvent.CREATED.name(), event.getLcEventType());
        assertTrue(event.isSuccess());
    }

    @Test
    void givenEvent_whenPersistLifecycleEventWithError_thenVerifyResult() {
        when(serviceInfoProvider.getServiceId()).thenReturn("test-service");
        Exception exception = new RuntimeException("Test Error");

        eventStorageService.persistLifecycleEvent(entityId, ComponentLifecycleEvent.UPDATED, exception);

        ArgumentCaptor<IntegrationEventProto> eventCaptor = ArgumentCaptor.forClass(IntegrationEventProto.class);
        verify(apiService, times(1)).sendEventData(eq(entityId), eventCaptor.capture(), any());

        IntegrationEventProto capturedEvent = eventCaptor.getValue();
        assertNotNull(capturedEvent);

        LifecycleEvent event = JavaSerDesUtil.decode(capturedEvent.getEvent().toByteArray());
        assertEquals(entityId, event.getEntityId());
        assertEquals("test-service", event.getServiceId());
        assertEquals(ComponentLifecycleEvent.UPDATED.name(), event.getLcEventType());
        assertFalse(event.isSuccess());
        assertTrue(event.getError().contains("Test Error"));
    }

    @Test
    void givenEvent_whenPersistStatistics_thenVerifyResult() {
        when(serviceInfoProvider.getServiceId()).thenReturn("test-service");

        IntegrationStatistics statistics = mock(IntegrationStatistics.class);
        when(statistics.getMessagesProcessed()).thenReturn(100L);
        when(statistics.getErrorsOccurred()).thenReturn(5L);

        eventStorageService.persistStatistics(entityId, statistics);

        ArgumentCaptor<IntegrationEventProto> eventCaptor = ArgumentCaptor.forClass(IntegrationEventProto.class);
        verify(apiService, times(1)).sendEventData(eq(entityId), eventCaptor.capture(), any());

        IntegrationEventProto capturedEvent = eventCaptor.getValue();
        assertNotNull(capturedEvent);

        StatisticsEvent event = JavaSerDesUtil.decode(capturedEvent.getEvent().toByteArray());
        assertEquals(entityId, event.getEntityId());
        assertEquals("test-service", event.getServiceId());
        assertEquals(100L, event.getMessagesProcessed());
        assertEquals(5L, event.getErrorsOccurred());
    }

    @Test
    void givenEvent_whenPersistError_thenVerifyResult() {
        ErrorEvent errorEvent = ErrorEvent.builder().entityId(entityId).error("Test error message").build();

        eventStorageService.persistError(entityId, errorEvent);

        ArgumentCaptor<IntegrationEventProto> eventCaptor = ArgumentCaptor.forClass(IntegrationEventProto.class);
        verify(apiService, times(1)).sendEventData(eq(entityId), eventCaptor.capture(), any());

        IntegrationEventProto capturedEvent = eventCaptor.getValue();
        assertNotNull(capturedEvent);
        assertEquals(entityId.getMostSignificantBits(), capturedEvent.getEventSourceIdMSB());
        assertEquals(entityId.getLeastSignificantBits(), capturedEvent.getEventSourceIdLSB());

        ErrorEvent deserializedEvent = JavaSerDesUtil.decode(capturedEvent.getEvent().toByteArray());
        assertNotNull(deserializedEvent);
        assertEquals(entityId, deserializedEvent.getEntityId());
        assertEquals("Test error message", deserializedEvent.getError());
    }

}
