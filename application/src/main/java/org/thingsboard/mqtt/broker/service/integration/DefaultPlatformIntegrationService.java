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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.integration.IntegrationSubscriptionUpdateService;
import org.thingsboard.mqtt.broker.common.data.JavaSerDesUtil;
import org.thingsboard.mqtt.broker.common.data.event.Event;
import org.thingsboard.mqtt.broker.common.data.event.EventType;
import org.thingsboard.mqtt.broker.common.data.event.LifecycleEvent;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.subscription.IntegrationTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.event.EventService;
import org.thingsboard.mqtt.broker.dao.integration.IntegrationService;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationEventProto;
import org.thingsboard.mqtt.broker.gen.integration.TbEventSourceProto;
import org.thingsboard.mqtt.broker.gen.queue.ServiceInfo;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.system.SystemInfoService;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent.FAILED;
import static org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent.STARTED;
import static org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent.STOPPED;
import static org.thingsboard.mqtt.broker.common.util.DonAsynchron.withCallback;

@Slf4j
@Service
@Data
public class DefaultPlatformIntegrationService implements PlatformIntegrationService {

    private final IntegrationService integrationService;
    private final EventService eventService;
    private final IntegrationSubscriptionUpdateService integrationSubscriptionUpdateService;
    private final IntegrationDownlinkQueueService ieDownlinkQueueService;
    private final ServiceInfoProvider serviceInfoProvider;
    private final IntegrationCleanupServiceImpl integrationCleanupService;
    private final SystemInfoService systemInfoService;

    @Override
    public void processIntegrationUpdate(Integration integration, boolean created) {
        log.trace("processIntegrationUpdate: [{}][{}]", integration, created);
        updateSubscriptions(integration);

        var event = created ? ComponentLifecycleEvent.CREATED : ComponentLifecycleEvent.UPDATED;
        sendToIntegrationExecutor(integration, event);
    }

    @Override
    public void processIntegrationDelete(Integration integration, boolean removed) {
        log.trace("processIntegrationDelete: [{}][{}]", integration, removed);
        if (removed) {
            removeSubscriptions(integration.getIdStr());
            sendToIntegrationExecutor(integration, ComponentLifecycleEvent.DELETED);
            if (!integration.isEnabled()) {
                integrationCleanupService.deleteIntegrationTopic(integration.getIdStr());
            }
        }
    }

    @Override
    public void processIntegrationRestart(Integration integration) throws ThingsboardException {
        log.trace("processIntegrationRestart: [{}]", integration);
        if (!integration.isEnabled()) {
            throw new ThingsboardException("Integration is disabled", ThingsboardErrorCode.GENERAL);
        }
        updateSubscriptions(integration);

        sendToIntegrationExecutor(integration, ComponentLifecycleEvent.REINIT);
    }

    @Override
    public void processUplinkData(IntegrationEventProto data, IntegrationApiCallback callback) {
        log.trace("processUplinkData: [{}]", data);
        var eventSource = data.getSource();
        UUID entityId;
        if (eventSource == TbEventSourceProto.INTEGRATION) {
            entityId = new UUID(data.getEventSourceIdMSB(), data.getEventSourceIdLSB());
        } else {
            callback.onError(new IllegalArgumentException("Not supported event source: " + eventSource));
            return;
        }

        saveEvent(entityId, data, callback);
    }

    @Override
    public void processServiceInfo(ServiceInfo serviceInfo) {
        systemInfoService.processIeServiceInfo(serviceInfo);
    }

    @Override
    public void updateSubscriptions(Integration integration) {
        JsonNode configuration = integration.getConfiguration();
        if (!configuration.has("topicFilters")) {
            log.error("[{}][{}] Topic filters not configured", integration.getId(), integration.getName());
            return;
        }
        ArrayNode topicFiltersArrayNode = (ArrayNode) configuration.get("topicFilters");

        Set<TopicSubscription> subscriptions = Sets.newHashSetWithExpectedSize(topicFiltersArrayNode.size());
        topicFiltersArrayNode.forEach(topicFilter -> subscriptions.add(new IntegrationTopicSubscription(topicFilter.asText())));
        integrationSubscriptionUpdateService.processSubscriptionsUpdate(integration.getIdStr(), subscriptions);
    }

    @Override
    public void removeSubscriptions(String integrationId) {
        integrationSubscriptionUpdateService.processSubscriptionsUpdate(integrationId, Collections.emptySet());
    }

    private void saveEvent(UUID entityId, IntegrationEventProto proto, IntegrationApiCallback callback) {
        try {
            Event event = JavaSerDesUtil.decode(proto.getEvent().toByteArray());
            if (event == null) {
                log.warn("[{}] Could not convert proto to event {}", entityId, proto);
                return;
            }
            log.trace("Process saveEvent from IE: [{}][{}]", entityId, event);
            if (event.getEntityId() == null) {
                event.setEntityId(entityId);
            }

            ListenableFuture<Void> saveEventFuture = eventService.saveAsync(event);

            if (event.getType().equals(EventType.LC_EVENT)) {
                LifecycleEvent lcEvent = (LifecycleEvent) event;

                ListenableFuture<Integration> findIntegrationFuture = Futures.transformAsync(saveEventFuture,
                        __ -> integrationService.findIntegrationByIdAsync(entityId), MoreExecutors.directExecutor());

                withCallback(findIntegrationFuture, integration -> {
                    if (integration == null) {
                        if (log.isDebugEnabled()) {
                            log.debug("[{}] Could not find integration to update its status {}", entityId, lcEvent);
                        } else {
                            log.info("[{}][{}] Could not find integration to update its status", entityId, lcEvent.getLcEventType());
                        }
                    } else {
                        ObjectNode value = null;
                        if (lcEvent.getLcEventType().equals(STARTED.name()) || lcEvent.getLcEventType().equals(FAILED.name())) {
                            value = getStatusValue(lcEvent);
                        } else if (lcEvent.getLcEventType().equals(STOPPED.name())) {
                            // Should we set some other value instead of null?
                        }
                        log.debug("[{}] Updating integration status: {}", integration, value);
                        integrationService.saveIntegrationStatus(integration, value);
                    }
                }, callback::onError);
            } else {
                withCallback(saveEventFuture, callback::onSuccess, callback::onError);
            }

        } catch (Exception t) {
            log.warn("[{}][{}] Failed to save event and update integration status!", entityId, proto.getEvent(), t);
            callback.onError(t);
            throw t;
        }
    }

    private ObjectNode getStatusValue(LifecycleEvent lcEvent) {
        ObjectNode value = JacksonUtil.newObjectNode();
        if (lcEvent.isSuccess()) {
            value.put("success", true);
        } else {
            value.put("success", false);
            value.put("serviceId", lcEvent.getServiceId());
            value.put("error", lcEvent.getError());
        }
        return value;
    }

    private void sendToIntegrationExecutor(Integration integration, ComponentLifecycleEvent event) {
        saveEvent(integration, event);
        ieDownlinkQueueService.send(integration, event);
    }

    private void saveEvent(Integration result, ComponentLifecycleEvent event) {
        log.trace("saveEvent: [{}][{}]", result, event);
        var lcEvent = LifecycleEvent.builder()
                .entityId(result.getId())
                .serviceId(serviceInfoProvider.getServiceId())
                .lcEventType(event.name())
                .success(true).build();
        withCallback(eventService.saveAsync(lcEvent), __ -> {
        }, ___ -> {
        });
    }

}
