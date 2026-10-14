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
package org.thingsboard.mqtt.broker.service.topictree;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.dto.RetainedMsgDto;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerService;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgService;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;

import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

@Slf4j
@Service
public class TopicTreeServiceImpl implements TopicTreeService {

    private final ClientSubscriptionCache clientSubscriptionCache;
    private final RetainedMsgListenerService retainedMsgListenerService;
    private final RetainedMsgService retainedMsgService;
    private final ClientSessionCache clientSessionCache;
    private final boolean enabled;
    private final long ttlMs;
    private final long idleEvictMs;
    private final ScheduledExecutorService ownExecutor;
    private final AtomicBoolean rebuilding = new AtomicBoolean();

    LongSupplier clock = System::currentTimeMillis;
    Executor executor;

    private volatile TopicTreeSnapshot snapshot;
    private volatile long lastAccess;

    public TopicTreeServiceImpl(ClientSubscriptionCache clientSubscriptionCache,
                                RetainedMsgListenerService retainedMsgListenerService,
                                RetainedMsgService retainedMsgService,
                                ClientSessionCache clientSessionCache,
                                @Value("${mqtt.topic-tree.enabled:true}") boolean enabled,
                                @Value("${mqtt.topic-tree.snapshot-ttl-ms:5000}") long ttlMs,
                                @Value("${mqtt.topic-tree.idle-evict-ms:600000}") long idleEvictMs) {
        this.clientSubscriptionCache = clientSubscriptionCache;
        this.retainedMsgListenerService = retainedMsgListenerService;
        this.retainedMsgService = retainedMsgService;
        this.clientSessionCache = clientSessionCache;
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
    public boolean isEnabled() { return enabled; }

    @PreDestroy
    public void destroy() {
        ownExecutor.shutdownNow();
    }
}
