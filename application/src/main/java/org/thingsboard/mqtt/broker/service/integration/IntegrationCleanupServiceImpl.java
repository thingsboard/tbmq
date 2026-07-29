/**
 * Copyright © 2016-2026 The Thingsboard Authors
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.integration.IntegrationSubscriptionUpdateService;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.dao.integration.IntegrationService;
import org.thingsboard.mqtt.broker.gen.queue.IntegrationLifecycleConfigProto;
import org.thingsboard.mqtt.broker.gen.queue.InternodeNotificationProto;
import org.thingsboard.mqtt.broker.service.notification.InternodeNotificationsService;
import org.thingsboard.mqtt.broker.service.queue.IntegrationTopicService;

import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@Component
@Slf4j
@RequiredArgsConstructor
public class IntegrationCleanupServiceImpl {

    private final IntegrationService integrationService;
    private final IntegrationTopicService integrationTopicService;
    private final IntegrationSubscriptionUpdateService integrationSubscriptionUpdateService;
    private final IntegrationLifecycleEventTypeCache lifecycleEventTypeCache;
    private final InternodeNotificationsService internodeNotificationsService;
    private final IntegrationExpiryChecker expiryChecker;

    @Scheduled(fixedRateString = "${integrations.cleanup.period}", timeUnit = TimeUnit.SECONDS)
    public void cleanUp() {
        if (!expiryChecker.isCleanupEnabled()) {
            log.debug("Integrations cleanup is disabled");
            return;
        }
        log.info("Starting cleaning up expired disconnected integrations");

        int count = 0;
        try {
            PageLink pageLink = new PageLink(BrokerConstants.DEFAULT_PAGE_SIZE);
            PageData<Integration> pageData;
            do {
                pageData = integrationService.findIntegrations(pageLink);
                for (Integration integration : pageData.getData()) {
                    if (cleanUpIfExpired(integration)) {
                        count++;
                    }
                }
                pageLink = pageLink.nextPageLink();
            } while (pageData.hasNext());
        } catch (Throwable t) {
            log.warn("Failed to clean up expired disconnected integrations", t);
        }
        log.info("Cleaning up of [{}] expired disconnected integrations is finished", count);
    }

    private boolean cleanUpIfExpired(Integration integration) {
        if (!expiryChecker.isExpired(integration)) {
            return false;
        }
        // The row is re-read before the destructive part, because detaching is no longer self-correcting: acting on a
        // page fetched before a loop of blocking admin calls could clear the subscriptions and event types of an
        // integration enabled since, leaving it running while silently receiving nothing until it is saved again.
        // Only expired candidates pay for this read, and they are rare.
        Integration current = integrationService.findIntegrationById(integration.getId());
        if (current == null || !expiryChecker.isExpired(current)) {
            log.debug("[{}][{}] No longer expired, skipping", integration.getId(), integration.getName());
            return false;
        }
        try {
            log.debug("[{}][{}] Cleaning up expired disconnected integration", current.getId(), current.getName());
            if (!stopProducingFor(current.getIdStr())) {
                // An earlier sweep already detached it, so nothing feeds either topic any more and there is nothing
                // left to reclaim. Without this the sweep would re-issue four admin calls per expired integration,
                // on every node, for as long as the integration stays disabled. A topic whose deletion failed back
                // then is left behind until the integration is deleted, which is a better trade than repeating the
                // whole sweep indefinitely - the failure is logged by the delete callback.
                log.debug("[{}][{}] Already detached from both streams, skipping", current.getId(), current.getName());
                return false;
            }
            deleteIntegrationTopics(current.getIdStr());
            return true;
        } catch (Exception e) {
            // Per integration: a failure here (e.g. a Kafka admin timeout on a consumer group delete) must not
            // skip every remaining expired integration until the next period.
            log.warn("[{}][{}] Failed to clean up expired disconnected integration",
                    current.getId(), current.getName(), e);
            return false;
        }
    }

    /**
     * Deletes both of the integration's topics and their consumer groups. Called by the sweep and, for a disabled
     * integration, on delete - see {@code DefaultPlatformIntegrationService.processIntegrationDelete} for why the
     * Integration Executor cannot do it in that case.
     */
    public void deleteIntegrationTopics(String integrationId) {
        deleteTopicQuietly(integrationId, "data", integrationTopicService::deleteTopic);
        deleteTopicQuietly(integrationId, "lifecycle events", integrationTopicService::deleteEventTopic);
    }

    /**
     * Each deletion is isolated from the other. Only the topic delete itself is callback-based: the consumer group is
     * deleted first and synchronously, and rethrows on anything but GroupIdNotFoundException. Without this a failure
     * on the first topic would skip the second - and neither the sweep, which has already detached the integration
     * and so short-circuits, nor the delete path, where the row is gone, would ever retry.
     */
    private void deleteTopicQuietly(String integrationId, String topicKind, BiConsumer<String, BasicCallback> deletion) {
        BasicCallback callback = deleteCallback(integrationId, topicKind);
        try {
            deletion.accept(integrationId, callback);
        } catch (Exception e) {
            // Routed through the same callback so a synchronous missing-topic error is logged like an asynchronous one.
            callback.onFailure(e);
        }
    }

    private BasicCallback deleteCallback(String integrationId, String topicKind) {
        return CallbackUtil.createCallback(
                () -> log.debug("[{}] Deleted the {} topic", integrationId, topicKind),
                t -> {
                    if (ExceptionUtils.hasCause(t, UnknownTopicOrPartitionException.class)) {
                        // Expected: an integration that never opted into lifecycle events has no events topic, and a
                        // sweep on another node may have won the race. Deleting unconditionally keeps a topic left
                        // over from a since-removed opt-in reclaimable, so this is not worth gating on the opt-in.
                        log.debug("[{}] The {} topic does not exist", integrationId, topicKind);
                        return;
                    }
                    log.warn("[{}] Failed to delete the {} topic", integrationId, topicKind, t);
                });
    }

    /**
     * Detaches the integration from both streams that feed it, so the TTL actually reclaims something: topic
     * subscriptions drive the data stream, and the lifecycle event type cache drives the events stream. Deleting the
     * topics without this is a no-op in practice - both producers recreate their topic on the next send.
     * <p>
     * Returns {@code true} when something was actually detached, i.e. the integration was still producing. Both are
     * restored when the integration is enabled again: saving it re-registers the subscriptions
     * (see {@code PlatformIntegrationService.updateSubscriptions}) and re-populates the cache on every node via the
     * {@code IntegrationLifecycleConfigProto} broadcast.
     */
    private boolean stopProducingFor(String integrationId) {
        boolean wasSubscribed = integrationSubscriptionUpdateService.hasSubscriptions(integrationId);
        if (wasSubscribed) {
            integrationSubscriptionUpdateService.clearSubscriptions(integrationId);
        }
        boolean eventTypesEvicted = lifecycleEventTypeCache.remove(integrationId);
        boolean detached = wasSubscribed || eventTypesEvicted;
        if (detached) {
            // Deliberately not gated on eventTypesEvicted: this node's cache being empty says nothing about the
            // other nodes'. The startup skip leaves it empty on a node that restarted after the expiry, while its
            // subscriptions still come back from the subscriptions topic - so the node that reclaims the topics is
            // not necessarily the node that had the event types cached. An idempotent no-op where nothing is cached.
            evictEventTypesClusterWide(integrationId);
        }
        return detached;
    }

    /**
     * The event type cache is node-local, while the topics are deleted for the whole cluster, so evicting only here
     * would leave every other node publishing lifecycle events into a topic that no longer exists - recreating it on
     * send and undoing the reclaim until that node's own sweep runs, up to
     * {@code integrations.cleanup.period} later.
     * <p>
     * Sent as an opt-in with no event types rather than as a delete: the integration row survives a sweep, and
     * {@code IntegrationLifecycleEventTypeCacheImpl.put} already maps an empty list to the same eviction. Reusing the
     * {@code deleted} flag would mislead the next handler added for this proto.
     */
    private void evictEventTypesClusterWide(String integrationId) {
        internodeNotificationsService.broadcast(
                InternodeNotificationProto.newBuilder()
                        .setIntegrationLifecycleConfigProto(IntegrationLifecycleConfigProto.newBuilder()
                                .setIntegrationId(integrationId)
                                .setDeleted(false)
                                .build())
                        .build());
    }
}
