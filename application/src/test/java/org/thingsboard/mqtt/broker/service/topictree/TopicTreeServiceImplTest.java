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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.subscription.ClientTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.dto.RetainedMsgDto;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerService;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgService;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;

import java.nio.charset.StandardCharsets;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TopicTreeServiceImplTest {

    ClientSubscriptionCache subs = mock(ClientSubscriptionCache.class);
    RetainedMsgListenerService retained = mock(RetainedMsgListenerService.class);
    RetainedMsgService retainedMsgService = mock(RetainedMsgService.class);
    ClientSessionCache sessions = mock(ClientSessionCache.class);
    AtomicLong now = new AtomicLong(1000);
    List<Runnable> queued = new ArrayList<>();
    TopicTreeServiceImpl service;

    @BeforeEach
    void setUp() {
        when(subs.getAllClientSubscriptions()).thenReturn(Map.of());
        when(retained.getRetainedMessages()).thenReturn(List.of());
        when(sessions.getAllClientSessions()).thenReturn(Map.of());
        service = newService(true);
    }

    private TopicTreeServiceImpl newService(boolean enabled) {
        TopicTreeServiceImpl s = new TopicTreeServiceImpl(subs, retained, retainedMsgService, sessions,
                enabled, 5000, 600_000);
        s.clock = now::get;
        s.executor = queued::add;
        return s;
    }

    @Test
    void firstCallBuildsSynchronously() {
        TopicTreeSnapshot s = service.getSnapshot();
        assertThat(s.getBuiltAt()).isEqualTo(1000);
        verify(subs, times(1)).getAllClientSubscriptions();
    }

    @Test
    void freshSnapshotIsReused() {
        TopicTreeSnapshot a = service.getSnapshot();
        now.addAndGet(4999);
        assertThat(service.getSnapshot()).isSameAs(a);
        assertThat(queued).isEmpty();
    }

    @Test
    void staleSnapshotIsServedAndOneRebuildIsQueued() {
        TopicTreeSnapshot a = service.getSnapshot();
        now.addAndGet(6000);
        assertThat(service.getSnapshot()).isSameAs(a);
        assertThat(service.getSnapshot()).isSameAs(a);
        assertThat(queued).hasSize(1);
        queued.get(0).run();
        assertThat(service.getSnapshot()).isNotSameAs(a);
        assertThat(service.getSnapshot().getBuiltAt()).isEqualTo(7000);
    }

    @Test
    void failedRebuildKeepsPreviousSnapshot() {
        TopicTreeSnapshot a = service.getSnapshot();
        now.addAndGet(6000);
        when(subs.getAllClientSubscriptions()).thenThrow(new RuntimeException("boom"));
        service.getSnapshot();
        queued.get(0).run();
        assertThat(service.getSnapshot()).isSameAs(a);
    }

    @Test
    void concurrentModificationOnFirstIterationIsRetried() {
        AtomicInteger iterations = new AtomicInteger();
        Set<TopicSubscription> flaky = new AbstractSet<>() {
            private final List<TopicSubscription> items = List.of(new ClientTopicSubscription("a/b", 1));

            @Override
            public Iterator<TopicSubscription> iterator() {
                if (iterations.incrementAndGet() == 1) {
                    throw new ConcurrentModificationException();
                }
                return items.iterator();
            }

            @Override
            public int size() {
                return items.size();
            }
        };
        when(subs.getAllClientSubscriptions()).thenReturn(Map.of("c1", flaky));

        TopicTreeSnapshot s = service.getSnapshot();

        assertThat(iterations.get()).isEqualTo(2);
        assertThat(s.find("a/b").subscriptionsExact()).isEqualTo(1);
    }

    @Test
    void rejectedExecutionResetsRebuildingFlag() {
        TopicTreeSnapshot a = service.getSnapshot();
        now.addAndGet(6000);
        service.executor = r -> {
            throw new RejectedExecutionException();
        };
        assertThat(service.getSnapshot()).isSameAs(a);
        service.executor = queued::add;
        service.getSnapshot();
        assertThat(queued).hasSize(1);
    }

    @Test
    void disabledServiceDoesNotBuild() {
        service = newService(false);
        assertThat(service.getSnapshot().nodesCount()).isEqualTo(1);
        verify(subs, times(0)).getAllClientSubscriptions();
    }

    @Test
    void idleSnapshotIsEvictedAndRebuiltOnNextRequest() {
        TopicTreeSnapshot first = service.getSnapshot();
        now.addAndGet(599_999);
        service.evictIfIdle();
        assertThat(service.getSnapshot()).isSameAs(first);
        now.addAndGet(600_000);
        service.evictIfIdle();
        TopicTreeSnapshot rebuilt = service.getSnapshot();
        assertThat(rebuilt).isNotSameAs(first);
        assertThat(rebuilt.getBuiltAt()).isEqualTo(now.get());
    }

    @Test
    void retainedMessagesComeFromTheTrieSubtreeSortedAndPaged() {
        byte[] payload = "1".getBytes(StandardCharsets.UTF_8);
        when(retainedMsgService.getRetainedMessages("a/#")).thenReturn(List.of(
                new RetainedMsg("a/c", payload, 0), new RetainedMsg("a", payload, 1), new RetainedMsg("a/b", payload, 0)));

        PageData<RetainedMsgDto> first = service.getRetainedMessages("a", new PageLink(2, 0));
        assertThat(first.getData()).extracting(RetainedMsgDto::getTopic).containsExactly("a", "a/b");
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.hasNext()).isTrue();
        assertThat(service.getRetainedMessages("a", new PageLink(2, 1)).getData()).extracting(RetainedMsgDto::getTopic).containsExactly("a/c");
    }

    @Test
    void retainedMessagesUnderWildcardPathAreEmptyWithoutAskingTheTrie() {
        assertThat(service.getRetainedMessages("a/+", new PageLink(10, 0)).getTotalElements()).isZero();
        assertThat(service.getRetainedMessages("a/#", new PageLink(10, 0)).getData()).isEmpty();
        verify(retainedMsgService, never()).getRetainedMessages(anyString());
    }
}
