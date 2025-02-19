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

import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.JavaSerDesUtil;
import org.thingsboard.mqtt.broker.common.data.event.ErrorEvent;
import org.thingsboard.mqtt.broker.common.data.event.LifecycleEvent;
import org.thingsboard.mqtt.broker.common.data.event.StatisticsEvent;
import org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent;
import org.thingsboard.mqtt.broker.common.util.EventUtil;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationEventProto;
import org.thingsboard.mqtt.broker.gen.integration.TbEventSourceProto;
import org.thingsboard.mqtt.broker.integration.api.IntegrationCallback;
import org.thingsboard.mqtt.broker.integration.api.IntegrationStatistics;
import org.thingsboard.mqtt.broker.integration.service.api.IntegrationApiService;
import org.thingsboard.mqtt.broker.queue.TbmqIntegrationExecutorComponent;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.EventStorageService;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@TbmqIntegrationExecutorComponent
public class TbIntegrationExecutorEventStorageService implements EventStorageService {

    private final ServiceInfoProvider serviceInfoProvider;
    private final IntegrationApiService apiService;

    @Override
    public void persistLifecycleEvent(UUID entityId, ComponentLifecycleEvent lcEvent, Exception e) {
        TbEventSourceProto eventSource = TbEventSourceProto.INTEGRATION;

        var event = LifecycleEvent.builder()
                .entityId(entityId)
                .serviceId(serviceInfoProvider.getServiceId())
                .lcEventType(lcEvent.name());
        if (e != null) {
            event.success(false).error(EventUtil.toString(e));
        } else {
            event.success(true);
        }

        var builder = IntegrationEventProto.newBuilder()
                .setSource(eventSource)
                .setEvent(ByteString.copyFrom(JavaSerDesUtil.encode(event.build())))
                .setEventSourceIdMSB(entityId.getMostSignificantBits())
                .setEventSourceIdLSB(entityId.getLeastSignificantBits());

        apiService.sendEventData(entityId, builder.build(), EMPTY_CALLBACK);
    }

    @Override
    public void persistStatistics(UUID entityId, IntegrationStatistics statistics) {
        var builder = IntegrationEventProto.newBuilder()
                .setSource(TbEventSourceProto.INTEGRATION)
                .setEvent(ByteString.copyFrom(JavaSerDesUtil.encode(StatisticsEvent.builder()
                        .entityId(entityId)
                        .serviceId(serviceInfoProvider.getServiceId())
                        .messagesProcessed(statistics.getMessagesProcessed())
                        .errorsOccurred(statistics.getErrorsOccurred())
                        .build())))
                .setEventSourceIdMSB(entityId.getMostSignificantBits())
                .setEventSourceIdLSB(entityId.getLeastSignificantBits());

        apiService.sendEventData(entityId, builder.build(), EMPTY_CALLBACK);

        // Do we need to save time series data as well?
    }

    @Override
    public void persistError(UUID entityId, ErrorEvent event) {
        TbEventSourceProto eventSource = TbEventSourceProto.INTEGRATION;

        var builder = IntegrationEventProto.newBuilder()
                .setSource(eventSource)
                .setEvent(ByteString.copyFrom(JavaSerDesUtil.encode(event)))
                .setEventSourceIdMSB(entityId.getMostSignificantBits())
                .setEventSourceIdLSB(entityId.getLeastSignificantBits());

        apiService.sendEventData(entityId, builder.build(), EMPTY_CALLBACK);
    }

    private static final IntegrationCallback<Void> EMPTY_CALLBACK = new IntegrationCallback<>() {
        @Override
        public void onSuccess(Void msg) {
            log.debug("Successfully delivered uplink event to tbmq");
        }

        @Override
        public void onError(Throwable e) {
            log.error("Failed to deliver uplink event to tbmq", e);
        }
    };
}
