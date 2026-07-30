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
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
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

    /**
     * Deliberately not zero, which is what a fixed-rate task without one gets: Spring registers {@code @Scheduled}
     * tasks on ContextRefreshedEvent and submits the first run right there, before the ApplicationReadyEvent that
     * drives BrokerInitializer - so the first sweep after every restart would read node-local state that is not
     * loaded yet. The readiness check in {@link #cleanUp()} is what makes that safe; this delay is what keeps a node
     * restarting more often than {@code integrations.cleanup.period} reclaiming at all, since the check alone would
     * skip every first sweep and push the reclaim a full period past each start.
     * <p>
     * Generous rather than tight, because nothing needs the sweep to be prompt - the ttl it enforces defaults to a
     * week - so the only thing worth sizing for is clearing a slow startup on a large node.
     */
    static final long INITIAL_DELAY_SEC = 600;

    private final IntegrationService integrationService;
    private final IntegrationTopicService integrationTopicService;
    private final IntegrationSubscriptionUpdateService integrationSubscriptionUpdateService;
    private final IntegrationLifecycleEventTypeCache lifecycleEventTypeCache;
    private final InternodeNotificationsService internodeNotificationsService;
    private final IntegrationExpiryChecker expiryChecker;
    // Read only for its initialization flag - the subscriptions themselves go through
    // integrationSubscriptionUpdateService. Same use as in HistoricalStatsTotalConsumer.checkAllStatsServicesReady.
    private final ClientSubscriptionService clientSubscriptionService;

    @Scheduled(initialDelay = INITIAL_DELAY_SEC, fixedRateString = "${integrations.cleanup.period}", timeUnit = TimeUnit.SECONDS)
    public void cleanUp() {
        if (!expiryChecker.isCleanupEnabled()) {
            log.debug("Integrations cleanup is disabled");
            return;
        }
        if (!clientSubscriptionService.isInitialized()) {
            // An unloaded subscriptions map answers "no subscriptions" for every integration rather than failing
            // (ClientSubscriptionServiceImpl.getClientSubscriptions), and stopProducingFor cannot tell that apart
            // from an earlier sweep having detached the integration: it would report every expired integration as
            // already detached and reclaim nothing, while a concurrent ClientSubscriptionServiceImpl.init could
            // still have its clear land between that map being populated and the subscription trie being rebuilt.
            // Sweeping nothing is the honest outcome; INITIAL_DELAY_SEC is what normally keeps this unreached.
            //
            // The lifecycle event type cache needs no such check: an empty entry there for an expired integration
            // is the steady state rather than a load-in-progress artifact, since
            // BrokerInitializer.initIntegrationLifecycleEventCache deliberately never re-attaches one.
            log.info("Skipping the integrations cleanup: subscriptions are not initialized yet");
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
        try {
            // The row is re-read before the destructive part, because detaching is no longer self-correcting: acting on
            // a page fetched before a loop of blocking admin calls could clear the subscriptions and event types of an
            // integration enabled since, leaving it running while silently receiving nothing until it is saved again.
            // Only expired candidates pay for this read, and they are rare. Inside the try because it is a database
            // call like any other, and a failure here must not abandon the rest of the sweep.
            Integration current = integrationService.findIntegrationById(integration.getId());
            if (current == null || !expiryChecker.isExpired(current)) {
                log.debug("[{}][{}] No longer expired, skipping", integration.getId(), integration.getName());
                return false;
            }
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
                    if (ExceptionUtils.throwableOfType(t, UnknownTopicOrPartitionException.class) != null) {
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
            // not necessarily the node that had the event types cached. IntegrationLifecycleEventTypeCacheImpl.put
            // always rebuilds the reverse index for this call, even where nothing was cached locally - unlike
            // remove(), it does not skip on a miss - but every node's cache still converges to the same "nothing"
            // regardless. This does race a concurrent re-enable: if this empty broadcast lands after a save's
            // populated one on some node, that node stops publishing events for an enabled integration until the
            // next save or restart. The findIntegrationById re-read above narrows the window to microseconds; it is
            // inherent to last-writer-wins, not something to fix here.
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
