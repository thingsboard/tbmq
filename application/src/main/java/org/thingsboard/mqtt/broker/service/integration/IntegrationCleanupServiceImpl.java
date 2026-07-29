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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.integration.IntegrationSubscriptionUpdateService;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.dao.integration.IntegrationService;
import org.thingsboard.mqtt.broker.service.queue.IntegrationTopicService;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class IntegrationCleanupServiceImpl {

    /**
     * Integrations are fetched one at a time on purpose. The sweep is destructive - it detaches the integration from
     * both streams - and each iteration issues blocking admin calls, so a snapshot taken before the loop would let a
     * stale {@code enabled=false} reading detach an integration that has since been enabled, leaving it running with
     * no subscriptions and no lifecycle events until it is saved again. Re-reading per integration also keeps the
     * sweep's footprint constant regardless of how many integrations exist.
     */
    private static final int CLEANUP_PAGE_SIZE = 1;

    private final IntegrationService integrationService;
    private final IntegrationTopicService integrationTopicService;
    private final IntegrationSubscriptionUpdateService integrationSubscriptionUpdateService;
    private final IntegrationLifecycleEventTypeCache lifecycleEventTypeCache;

    @Value("#{${integrations.cleanup.ttl:604800} * 1000}")
    private long ttlMs;

    @Scheduled(fixedRateString = "${integrations.cleanup.period}", timeUnit = TimeUnit.SECONDS)
    public void cleanUp() {
        if (ttlMs <= 0) {
            log.debug("Integrations cleanup is disabled: {}ms", ttlMs);
            return;
        }
        log.info("Starting cleaning up expired disconnected integrations");

        int count = 0;
        try {
            PageLink pageLink = new PageLink(CLEANUP_PAGE_SIZE);
            PageData<Integration> pageData;
            do {
                pageData = integrationService.findIntegrations(pageLink);
                for (Integration integration : pageData.getData()) {
                    if (cleanUp(integration)) {
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

    private boolean cleanUp(Integration integration) {
        if (!needsToBeRemoved(System.currentTimeMillis(), integration)) {
            return false;
        }
        try {
            log.debug("[{}][{}] Cleaning up expired disconnected integration", integration.getId(), integration.getName());
            if (!stopProducingFor(integration.getIdStr())) {
                // An earlier sweep already detached it, so nothing feeds either topic any more and there is nothing
                // left to reclaim. Without this the sweep would re-issue four admin calls per expired integration,
                // on every node, for as long as the integration stays disabled. A topic whose deletion failed back
                // then is left behind until the integration is deleted, which is a better trade than repeating the
                // whole sweep indefinitely - the failure is logged by the delete callback.
                log.debug("[{}][{}] Already detached from both streams, skipping", integration.getId(), integration.getName());
                return false;
            }
            deleteIntegrationTopics(integration.getIdStr());
            return true;
        } catch (Exception e) {
            // Per integration: a failure here (e.g. a Kafka admin timeout on a consumer group delete) must not
            // skip every remaining expired integration until the next period.
            log.warn("[{}][{}] Failed to clean up expired disconnected integration",
                    integration.getId(), integration.getName(), e);
            return false;
        }
    }

    /**
     * Deletes both of the integration's topics and their consumer groups. Called by the sweep and, for a disabled
     * integration, on delete - see {@code DefaultPlatformIntegrationService.processIntegrationDelete} for why the
     * Integration Executor cannot do it in that case.
     */
    public void deleteIntegrationTopics(String integrationId) {
        integrationTopicService.deleteTopic(integrationId, deleteCallback(integrationId, "data"));
        integrationTopicService.deleteEventTopic(integrationId, deleteCallback(integrationId, "lifecycle events"));
    }

    private BasicCallback deleteCallback(String integrationId, String topicKind) {
        return CallbackUtil.createCallback(
                () -> log.debug("[{}] Deleted the {} topic", integrationId, topicKind),
                t -> log.warn("[{}] Failed to delete the {} topic", integrationId, topicKind, t));
    }

    /**
     * Detaches the integration from both streams that feed it, so the TTL actually reclaims something: topic
     * subscriptions drive the data stream, and the lifecycle event type cache drives the events stream. Deleting
     * the topics without this is a no-op in practice - the next matching publish recreates the data topic (its
     * producer creates topics on send) and the next lifecycle event keeps targeting the events topic.
     * <p>
     * Returns {@code true} when something was actually detached, i.e. the integration was still producing. Both are
     * restored when the integration is enabled again: saving it re-registers the subscriptions
     * (see {@code PlatformIntegrationService.updateSubscriptions}) and re-populates the cache on every node via the
     * {@code IntegrationLifecycleConfigProto} broadcast.
     */
    private boolean stopProducingFor(String integrationId) {
        boolean subscriptionsCleared = integrationSubscriptionUpdateService.processSubscriptionsUpdate(integrationId, Collections.emptySet());
        boolean eventTypesEvicted = lifecycleEventTypeCache.remove(integrationId);
        return subscriptionsCleared || eventTypesEvicted;
    }

    private boolean needsToBeRemoved(long currentTs, Integration integration) {
        return !integration.isEnabled() && isExpired(integration, currentTs);
    }

    private boolean isExpired(Integration integration, long currentTs) {
        return integration.getDisconnectedTime() + ttlMs < currentTs;
    }
}
