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
package org.thingsboard.mqtt.broker.service.integration;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.Futures;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.integration.IntegrationSubscriptionUpdateService;
import org.thingsboard.mqtt.broker.common.data.JavaSerDesUtil;
import org.thingsboard.mqtt.broker.common.data.event.LifecycleEvent;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.event.EventService;
import org.thingsboard.mqtt.broker.dao.integration.IntegrationService;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationEventProto;
import org.thingsboard.mqtt.broker.gen.integration.TbEventSourceProto;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.SystemInfoService;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultPlatformIntegrationServiceTest {

    @Mock
    private IntegrationService integrationService;
    @Mock
    private EventService eventService;
    @Mock
    private IntegrationSubscriptionUpdateService integrationSubscriptionUpdateService;
    @Mock
    private IntegrationDownlinkQueueService ieDownlinkQueueService;
    @Mock
    private ServiceInfoProvider serviceInfoProvider;
    @Mock
    IntegrationCleanupServiceImpl integrationCleanupService;
    @Mock
    SystemInfoService systemInfoService;

    private DefaultPlatformIntegrationService platformIntegrationService;
    private UUID integrationId;
    private Integration integration;

    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        platformIntegrationService = new DefaultPlatformIntegrationService(
                integrationService, eventService, integrationSubscriptionUpdateService,
                ieDownlinkQueueService, serviceInfoProvider, integrationCleanupService, systemInfoService
        );

        integrationId = UUID.randomUUID();
        integration = new Integration();
        integration.setId(integrationId);
        integration.setName("TestIntegration");
        ObjectNode configuration = JacksonUtil.newObjectNode();
        configuration.set("topicFilters", JacksonUtil.newArrayNode());
        integration.setConfiguration(configuration);

        when(eventService.saveAsync(any())).thenReturn(Futures.immediateFuture(null));
    }

    @AfterEach
    void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    void testProcessIntegrationUpdate_Created() {
        platformIntegrationService.processIntegrationUpdate(integration, true);

        verify(integrationSubscriptionUpdateService, times(1)).processSubscriptionsUpdate(anyString(), anySet());
        verify(ieDownlinkQueueService, times(1)).send(eq(integration), eq(ComponentLifecycleEvent.CREATED));
    }

    @Test
    void testProcessIntegrationUpdate_Updated() {
        platformIntegrationService.processIntegrationUpdate(integration, false);

        verify(integrationSubscriptionUpdateService, times(1)).processSubscriptionsUpdate(anyString(), anySet());
        verify(ieDownlinkQueueService, times(1)).send(eq(integration), eq(ComponentLifecycleEvent.UPDATED));
    }

    @Test
    void testProcessIntegrationDelete_Removed() {
        platformIntegrationService.processIntegrationDelete(integration, true);

        verify(integrationSubscriptionUpdateService, times(1)).processSubscriptionsUpdate(anyString(), eq(Collections.emptySet()));
        verify(ieDownlinkQueueService, times(1)).send(eq(integration), eq(ComponentLifecycleEvent.DELETED));
    }

    @Test
    void testProcessIntegrationDelete_NotRemoved() {
        platformIntegrationService.processIntegrationDelete(integration, false);

        verify(integrationSubscriptionUpdateService, never()).processSubscriptionsUpdate(anyString(), anySet());
        verify(ieDownlinkQueueService, never()).send(any(), any());
    }

    @Test
    void testProcessIntegrationRestart_Enabled() throws ThingsboardException {
        integration.setEnabled(true);

        platformIntegrationService.processIntegrationRestart(integration);

        verify(integrationSubscriptionUpdateService, times(1)).processSubscriptionsUpdate(anyString(), anySet());
        verify(ieDownlinkQueueService, times(1)).send(eq(integration), eq(ComponentLifecycleEvent.REINIT));
    }

    @Test
    void testProcessIntegrationRestart_Disabled() {
        integration.setEnabled(false);

        ThingsboardException exception = assertThrows(ThingsboardException.class, () ->
                platformIntegrationService.processIntegrationRestart(integration));

        assertEquals("Integration is disabled", exception.getMessage());
    }

    @Test
    void testUpdateSubscriptions_WithTopicFilters() {
        ObjectNode config = JacksonUtil.newObjectNode();
        ArrayNode topicFilters = JacksonUtil.newArrayNode();
        topicFilters.add("topic1");
        topicFilters.add("topic2");
        config.set("topicFilters", topicFilters);
        integration.setConfiguration(config);

        platformIntegrationService.updateSubscriptions(integration);

        ArgumentCaptor<Set<TopicSubscription>> captor = ArgumentCaptor.forClass(Set.class);
        verify(integrationSubscriptionUpdateService, times(1)).processSubscriptionsUpdate(eq(integration.getIdStr()), captor.capture());

        Set<TopicSubscription> capturedSubscriptions = captor.getValue();
        assertEquals(2, capturedSubscriptions.size());
    }

    @Test
    void testUpdateSubscriptions_NoTopicFilters() {
        ObjectNode config = JacksonUtil.newObjectNode();
        integration.setConfiguration(config);

        platformIntegrationService.updateSubscriptions(integration);

        verify(integrationSubscriptionUpdateService, never()).processSubscriptionsUpdate(anyString(), anySet());
    }

    @Test
    void testRemoveSubscriptions() {
        platformIntegrationService.removeSubscriptions(integration.getIdStr());

        verify(integrationSubscriptionUpdateService, times(1)).processSubscriptionsUpdate(integration.getIdStr(), Collections.emptySet());
    }

    @Test
    void testProcessUplinkData_ValidSource() {
        IntegrationEventProto eventProto = IntegrationEventProto.newBuilder()
                .setEventSourceIdMSB(integrationId.getMostSignificantBits())
                .setEventSourceIdLSB(integrationId.getLeastSignificantBits())
                .setSource(TbEventSourceProto.INTEGRATION)
                .setEvent(ByteString.copyFrom(JavaSerDesUtil.encode(LifecycleEvent.builder().build())))
                .build();

        IntegrationApiCallback callback = mock(IntegrationApiCallback.class);

        platformIntegrationService.processUplinkData(eventProto, callback);

        verify(eventService, times(1)).saveAsync(any());
    }

}
