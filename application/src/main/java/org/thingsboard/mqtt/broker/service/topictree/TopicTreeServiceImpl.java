/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2026 ThingsBoard, Inc. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 *
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes
 * information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
 * OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
 * THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
 * DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
 * OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
 */
package org.thingsboard.mqtt.broker.service.topictree;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.dto.DroppedMsgDto;
import org.thingsboard.mqtt.broker.dto.RetainedMsgDto;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.dropped.DroppedMsgPageService;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerService;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgService;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;

import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;

@Slf4j
@Service
public class TopicTreeServiceImpl implements TopicTreeService {

    private static final long DROPPED_WINDOW_MS = TimeUnit.HOURS.toMillis(24);

    private final ClientSubscriptionCache clientSubscriptionCache;
    private final RetainedMsgListenerService retainedMsgListenerService;
    private final RetainedMsgService retainedMsgService;
    private final ClientSessionCache clientSessionCache;
    private final DroppedMsgPageService droppedMsgPageService;
    private final boolean enabled;
    private final long ttlMs;
    private final long idleEvictMs;
    private final ScheduledExecutorService ownExecutor;
    private final AtomicBoolean rebuilding = new AtomicBoolean();
    private final AtomicBoolean droppedRebuilding = new AtomicBoolean();
    private final Object droppedLock = new Object();

    LongSupplier clock = System::currentTimeMillis;
    Executor executor;

    private volatile TopicTreeSnapshot snapshot;
    private volatile DroppedByPrefix dropped;
    private volatile long lastAccess;

    private record DroppedByPrefix(Map<String, Long> byPrefix, long builtAt) {
    }

    public TopicTreeServiceImpl(ClientSubscriptionCache clientSubscriptionCache,
                                RetainedMsgListenerService retainedMsgListenerService,
                                RetainedMsgService retainedMsgService,
                                ClientSessionCache clientSessionCache,
                                DroppedMsgPageService droppedMsgPageService,
                                @Value("${mqtt.topic-tree.enabled:true}") boolean enabled,
                                @Value("${mqtt.topic-tree.snapshot-ttl-ms:5000}") long ttlMs,
                                @Value("${mqtt.topic-tree.idle-evict-ms:600000}") long idleEvictMs) {
        this.clientSubscriptionCache = clientSubscriptionCache;
        this.retainedMsgListenerService = retainedMsgListenerService;
        this.retainedMsgService = retainedMsgService;
        this.clientSessionCache = clientSessionCache;
        this.droppedMsgPageService = droppedMsgPageService;
        this.enabled = enabled;
        this.ttlMs = ttlMs;
        this.idleEvictMs = idleEvictMs;
        this.ownExecutor = ThingsBoardExecutors.newSingleScheduledThreadPool("topic-tree-builder");
        this.executor = ownExecutor;
        if (enabled && idleEvictMs > 0) {
            long period = Math.max(1000, Math.min(idleEvictMs, 60_000));
            ownExecutor.scheduleWithFixedDelay(this::evictIfIdle, period, period, TimeUnit.MILLISECONDS);
        }
    }

    // A large broker's snapshot holds hundreds of MB; once nobody has looked at the page for a while, release it
    // and let the next request rebuild synchronously.
    void evictIfIdle() {
        if (snapshot != null && clock.getAsLong() - lastAccess >= idleEvictMs) {
            snapshot = null;
            dropped = null;
            log.debug("Evicted idle topic tree snapshot");
        }
    }

    @Override
    public TopicTreeSnapshot getSnapshot() {
        lastAccess = clock.getAsLong();
        TopicTreeSnapshot current = snapshot;
        if (!enabled) {
            return current != null ? current : emptySnapshot();
        }
        if (current == null) {
            synchronized (this) {
                if (snapshot == null) {
                    snapshot = build();
                }
                return snapshot;
            }
        }
        if (isStale(current.getBuiltAt()) && rebuilding.compareAndSet(false, true)) {
            try {
                executor.execute(this::rebuild);
            } catch (RuntimeException e) {
                rebuilding.set(false);
                log.warn("Failed to schedule topic tree snapshot rebuild, keeping the previous one", e);
            }
        }
        return current;
    }

    private boolean isStale(long builtAt) {
        return clock.getAsLong() - builtAt >= ttlMs;
    }

    private void rebuild() {
        try {
            snapshot = build();
        } catch (Throwable t) {
            rebuilding.set(false);
            if (t instanceof VirtualMachineError error) {
                throw error;
            }
            log.warn("Failed to rebuild topic tree snapshot, keeping the previous one", t);
            return;
        }
        rebuilding.set(false);
    }

    private TopicTreeSnapshot emptySnapshot() {
        return TopicTreeSnapshotBuilder.build(Map.of(), List.of(), Map.of(), clock.getAsLong());
    }

    private TopicTreeSnapshot build() {
        try {
            return doBuild();
        } catch (ConcurrentModificationException e) {
            log.debug("Concurrent subscription change while building topic tree snapshot, retrying once");
            return doBuild();
        }
    }

    private TopicTreeSnapshot doBuild() {
        long start = System.nanoTime();
        List<String> retained = retainedMsgListenerService.getRetainedMessages().stream().map(RetainedMsg::getTopic).toList();
        TopicTreeSnapshot result = TopicTreeSnapshotBuilder.build(
                clientSubscriptionCache.getAllClientSubscriptions(), retained,
                clientSessionCache.getAllClientSessions(), clock.getAsLong());
        log.debug("Built topic tree snapshot with {} nodes in {} ms", result.nodesCount(), (System.nanoTime() - start) / 1_000_000);
        return result;
    }

    // Same refresh-ahead scheme as the snapshot: the first request loads synchronously, later ones are served the
    // cached rollup while a stale one is reloaded in the background.
    @Override
    public ToLongFunction<TopicTreeNode> droppedCounts() {
        DroppedByPrefix current = dropped;
        if (current == null) {
            synchronized (droppedLock) {
                if (dropped == null) {
                    dropped = loadDropped();
                }
                current = dropped;
            }
        } else if (isStale(current.builtAt()) && droppedRebuilding.compareAndSet(false, true)) {
            try {
                executor.execute(this::reloadDropped);
            } catch (RuntimeException e) {
                droppedRebuilding.set(false);
                log.warn("Failed to schedule dropped counts reload, keeping the previous ones", e);
            }
        }
        Map<String, Long> byPrefix = current.byPrefix();
        // A node's path is derived on each call, so the lookup is memoised for the request: a sort by dropped
        // count would otherwise rebuild it on every comparison.
        Map<TopicTreeNode, Long> memo = new IdentityHashMap<>();
        return n -> memo.computeIfAbsent(n, node -> {
            String path = node.path();
            return path == null ? 0L : byPrefix.getOrDefault(path, 0L);
        });
    }

    private void reloadDropped() {
        try {
            dropped = loadDropped();
        } catch (RuntimeException e) {
            log.warn("Failed to reload dropped counts, keeping the previous ones", e);
        } finally {
            droppedRebuilding.set(false);
        }
    }

    // Drops are stored per concrete topic, so each one is credited to every topic tree level above it.
    private DroppedByPrefix loadDropped() {
        long now = clock.getAsLong();
        Map<String, Long> byPrefix = new HashMap<>();
        droppedMsgPageService.sumDroppedByTopicSince(now - DROPPED_WINDOW_MS).forEach((topic, count) -> {
            for (int i = topic.indexOf('/'); i >= 0; i = topic.indexOf('/', i + 1)) {
                byPrefix.merge(topic.substring(0, i), count, Long::sum);
            }
            byPrefix.merge(topic, count, Long::sum);
        });
        return new DroppedByPrefix(byPrefix, now);
    }

    // Read from the retained-message trie rather than the snapshot, which keeps only topics. Retained messages
    // live on concrete topics, so a path with a wildcard segment has none below it; asking the trie for it would
    // expand the wildcards instead.
    @Override
    public PageData<RetainedMsgDto> getRetainedMessages(String path, PageLink pageLink) {
        if (path.contains(BrokerConstants.MULTI_LEVEL_WILDCARD) || path.contains(BrokerConstants.SINGLE_LEVEL_WILDCARD)) {
            return PageData.emptyPageData();
        }
        List<RetainedMsg> subtree = retainedMsgService.getRetainedMessages(path + "/" + BrokerConstants.MULTI_LEVEL_WILDCARD).stream()
                .sorted(Comparator.comparing(RetainedMsg::getTopic))
                .toList();
        return TopicTreeSnapshot.page(subtree, pageLink).mapData(RetainedMsgDto::newInstance);
    }

    @Override
    public PageData<DroppedMsgDto> getDroppedMessages(String path, PageLink pageLink) {
        return droppedMsgPageService.getTopicSubtreeSince(path, clock.getAsLong() - DROPPED_WINDOW_MS, pageLink);
    }

    @Override
    public boolean isEnabled() { return enabled; }



    @PreDestroy
    public void destroy() {
        ownExecutor.shutdownNow();
    }
}
