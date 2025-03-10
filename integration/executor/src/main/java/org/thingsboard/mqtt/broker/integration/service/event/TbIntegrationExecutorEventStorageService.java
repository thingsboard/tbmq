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
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.JavaSerDesUtil;
import org.thingsboard.mqtt.broker.common.data.event.ErrorEvent;
import org.thingsboard.mqtt.broker.common.data.event.LifecycleEvent;
import org.thingsboard.mqtt.broker.common.data.event.StatisticsEvent;
import org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent;
import org.thingsboard.mqtt.broker.common.util.DeduplicationUtil;
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
        log.trace("[{}][{}] Sending Lifecycle event", entityId, lcEvent);
        if (DeduplicationUtil.alreadyProcessed(getDeduplicationKey(entityId, lcEvent), 500)) {
            log.info("[{}][{}] Lifecycle event already sent within 500ms", entityId, lcEvent);
            return;
        }

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
        log.trace("[{}][{}] Persist Statistics event", entityId, statistics);
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
    }

    @Override
    public void persistError(UUID entityId, ErrorEvent event) {
        log.trace("[{}][{}] Persist Error event", entityId, event);
        TbEventSourceProto eventSource = TbEventSourceProto.INTEGRATION;

        var builder = IntegrationEventProto.newBuilder()
                .setSource(eventSource)
                .setEvent(ByteString.copyFrom(JavaSerDesUtil.encode(event)))
                .setEventSourceIdMSB(entityId.getMostSignificantBits())
                .setEventSourceIdLSB(entityId.getLeastSignificantBits());

        apiService.sendEventData(entityId, builder.build(), EMPTY_CALLBACK);
    }

    private Object getDeduplicationKey(UUID entityId, ComponentLifecycleEvent lcEvent) {
        return Pair.of(entityId, lcEvent);
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
