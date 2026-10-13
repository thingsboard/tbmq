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
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.dto.RetainedMsgDto;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerService;
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
    private final ClientSessionCache clientSessionCache;
    private final boolean enabled;
    private final long ttlMs;
    private final int maxPageSize;
    private final int filterMinLength;
    private final long idleEvictMs;
    private final ScheduledExecutorService ownExecutor;
    private final AtomicBoolean rebuilding = new AtomicBoolean();

    LongSupplier clock = System::currentTimeMillis;
    Executor executor;

    private volatile TopicTreeSnapshot snapshot;
    private volatile long lastAccess;

    public TopicTreeServiceImpl(ClientSubscriptionCache clientSubscriptionCache,
                                RetainedMsgListenerService retainedMsgListenerService,
                                ClientSessionCache clientSessionCache,
                                @Value("${mqtt.topic-tree.enabled:true}") boolean enabled,
                                @Value("${mqtt.topic-tree.snapshot-ttl-ms:5000}") long ttlMs,
                                @Value("${mqtt.topic-tree.max-page-size:500}") int maxPageSize,
                                @Value("${mqtt.topic-tree.filter-min-length:2}") int filterMinLength,
                                @Value("${mqtt.topic-tree.idle-evict-ms:600000}") long idleEvictMs) {
        this.clientSubscriptionCache = clientSubscriptionCache;
        this.retainedMsgListenerService = retainedMsgListenerService;
        this.clientSessionCache = clientSessionCache;
        this.enabled = enabled;
        this.ttlMs = ttlMs;
        this.maxPageSize = maxPageSize;
        this.filterMinLength = filterMinLength;
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
        if (clock.getAsLong() - current.getBuiltAt() >= ttlMs && rebuilding.compareAndSet(false, true)) {
            try {
                executor.execute(this::rebuild);
            } catch (RuntimeException e) {
                rebuilding.set(false);
                log.warn("Failed to schedule topic tree snapshot rebuild, keeping the previous one", e);
            }
        }
        return current;
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

    // Read from the live retained-message map rather than the snapshot, which keeps only topics.
    @Override
    public PageData<RetainedMsgDto> getRetainedMessages(String path, PageLink pageLink) {
        String below = path == null ? null : path + "/";
        List<RetainedMsg> matching = retainedMsgListenerService.getRetainedMessages().stream()
                .filter(m -> path == null || m.getTopic().equals(path) || m.getTopic().startsWith(below))
                .sorted(Comparator.comparing(RetainedMsg::getTopic))
                .toList();
        int pageSize = pageLink.getPageSize();
        int from = (int) Math.min((long) pageLink.getPage() * pageSize, matching.size());
        int to = (int) Math.min((long) from + pageSize, matching.size());
        List<RetainedMsgDto> data = matching.subList(from, to).stream().map(RetainedMsgDto::newInstance).toList();
        return new PageData<>(data, (matching.size() + pageSize - 1) / pageSize, matching.size(), to < matching.size());
    }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public int getMaxPageSize() { return maxPageSize; }

    @Override
    public int getFilterMinLength() { return filterMinLength; }

    @PreDestroy
    public void destroy() {
        ownExecutor.shutdownNow();
    }
}
