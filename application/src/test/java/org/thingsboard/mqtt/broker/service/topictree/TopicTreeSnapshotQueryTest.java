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

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.client.session.SubscriptionOperation;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.dto.TopicTreeEntityDto;
import org.thingsboard.mqtt.broker.dto.TopicTreeSubscriptionDto;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TopicTreeSnapshotQueryTest {

    TopicTreeSnapshot s = new TopicTreeSnapshotBuilderTest().fixture();

    @Test
    void rootChildrenSortedBySubscriptionsDesc() {
        PageData<TopicTreeNode> p = s.children(s.getRoot(), new PageLink(10, 0));
        assertThat(p.getData().get(0).segment()).isEqualTo("sensors");
        assertThat(p.getTotalElements()).isEqualTo(s.getRoot().childrenCount());
    }

    @Test
    void childrenPaging() {
        PageData<TopicTreeNode> p = s.children(s.getRoot(), new PageLink(1, 1));
        assertThat(p.getData()).hasSize(1);
        assertThat(p.hasNext()).isTrue();
    }

    @Test
    void childrenSortBySegmentAsc() {
        PageData<TopicTreeNode> p = s.children(s.getRoot(), new PageLink(10, 0, null, new SortOrder("segment", SortOrder.Direction.ASC)));
        assertThat(p.getData().get(0).segment()).isEmpty();
    }

    @Test
    void searchIsCaseInsensitiveSubstringOverPath() {
        PageData<TopicTreeNode> p = s.search("TEMP", new PageLink(50, 0));
        assertThat(p.getData()).extracting(TopicTreeNode::path)
                .contains("sensors/+/temperature", "sensors/room1/temperature");
    }

    @Test
    void entitiesClientsAreDistinctAcrossSubtree() {
        PageData<TopicTreeEntityDto> p = s.entities(s.find("sensors"), TopicTreeEntityDto.Kind.CLIENTS, new PageLink(50, 0));
        assertThat(p.getData()).extracting(TopicTreeEntityDto::getId).containsExactlyInAnyOrder("c1", "c2", "c3");
    }

    @Test
    void subscriptionsListSubtreeWithSharePrefixAndIntegrations() {
        PageData<TopicTreeSubscriptionDto> p = s.subscriptions(s.find("sensors"), new PageLink(50, 0));
        assertThat(p.getTotalElements()).isEqualTo(s.find("sensors").subscriptionsTotal());
        assertThat(p.getData()).extracting(TopicTreeSubscriptionDto::getClientId, TopicTreeSubscriptionDto::getTopicFilter)
                .containsExactly(
                        tuple("c1", "sensors/+/temperature"),
                        tuple("c1", "sensors/room1/#"),
                        tuple("c2", "$share/g1/sensors/+/temperature"),
                        tuple("c3", "$share/g1/sensors/+/temperature"),
                        tuple("int-1", "sensors/#"));
        assertThat(p.getData().get(4).isIntegration()).isTrue();
        assertThat(p.getData().get(2).getShareName()).isEqualTo("g1");
        assertThat(s.sharedSubscriptions(s.find("sensors"))).isEqualTo(2);
        assertThat(s.sharedSubscriptions(s.find("sensors/room1/#"))).isZero();
    }

    @Test
    void entitiesIntegrationsAndSharedGroups() {
        assertThat(s.entities(s.find("sensors"), TopicTreeEntityDto.Kind.INTEGRATIONS, new PageLink(50, 0)).getData())
                .extracting(TopicTreeEntityDto::getId).containsExactly("int-1");
        assertThat(s.entities(s.find("sensors"), TopicTreeEntityDto.Kind.SHARED, new PageLink(50, 0)).getData())
                .extracting(TopicTreeEntityDto::getId).containsExactly("g1");
    }

    @Test
    void queryHasRetainedUsesSubtreeTotals() {
        assertThat(paths(query(Set.of(TopicTreeQuery.Has.RETAINED), Set.of(), false, null)))
                .contains("other", "sensors", "sensors/room1/temperature").doesNotContain("/leading", "a//b");
    }

    @Test
    void queryTopicPrefixMatchesWholeLevelsOnly() {
        TopicTreeQuery q = new TopicTreeQuery(null, "sensors/room1", true, null, Set.of(), Set.of(), Set.of(), false, null, null);
        assertThat(paths(s.query(q, n -> 0L, new PageLink(50, 0))))
                .isNotEmpty().allMatch(p -> p.equals("sensors/room1") || p.startsWith("sensors/room1/"));
    }

    @Test
    void queryWildcardOnly() {
        assertThat(paths(query(Set.of(), Set.of(), true, null)))
                .containsExactlyInAnyOrder("sensors/+", "sensors/#", "sensors/room1/#");
    }

    @Test
    void queryDisconnectedClientsFollowsTheOfflineSubscriber() {
        assertThat(paths(query(Set.of(), Set.of(TopicTreeQuery.ClientStatus.DISCONNECTED), false, null)))
                .containsExactlyInAnyOrder("sensors", "sensors/+", "sensors/+/temperature");
    }

    @Test
    void querySubscriptionCountComparison() {
        PageData<TopicTreeNode> p = query(Set.of(), Set.of(), false, 2);
        assertThat(p.getData()).isNotEmpty().allMatch(n -> n.subscriptionsTotal() > 2);
        assertThat(paths(p)).contains("sensors", "sensors/+/temperature");
    }

    @Test
    void queryHasDroppedAndSortByDroppedUseTheSuppliedCounts() {
        TopicTreeQuery q = new TopicTreeQuery(null, null, false, null, Set.of(TopicTreeQuery.Has.DROPPED), Set.of(), Set.of(), false, null, null);
        PageData<TopicTreeNode> p = s.query(q, n -> "other".equals(n.path()) ? 3 : 0, new PageLink(50, 0));
        assertThat(paths(p)).containsExactly("other");
        PageData<TopicTreeNode> sorted = s.children(s.getRoot(),
                new PageLink(50, 0, null, new SortOrder("dropped", SortOrder.Direction.DESC)), n -> "other".equals(n.path()) ? 3 : 0);
        assertThat(sorted.getData().get(0).path()).isEqualTo("other");
    }

    @Test
    void childrenSortByClientsDesc() {
        PageData<TopicTreeNode> p = s.children(s.getRoot(), new PageLink(50, 0, null, new SortOrder("clients", SortOrder.Direction.DESC)));
        assertThat(p.getData().get(0).path()).isEqualTo("sensors");
    }

    @Test
    void queryClientTypes() {
        Map<String, Set<TopicSubscription>> subs = Map.of(
                "app", Set.<TopicSubscription>of(TopicTreeSnapshotBuilderTest.sub("a/x", 0)),
                "dev", Set.<TopicSubscription>of(TopicTreeSnapshotBuilderTest.sub("b/y", 0)));
        Map<String, ClientSessionInfo> sessions = Map.of(
                "app", ClientSessionInfo.builder().clientId("app").type(ClientType.APPLICATION).build(),
                "dev", ClientSessionInfo.builder().clientId("dev").type(ClientType.DEVICE).build());
        TopicTreeSnapshot typed = TopicTreeSnapshotBuilder.build(subs, List.of(), sessions, 1L);
        TopicTreeQuery q = new TopicTreeQuery(null, null, false, null, Set.of(), Set.of(), Set.of(ClientType.APPLICATION), false, null, null);
        assertThat(paths(typed.query(q, n -> 0L, new PageLink(50, 0)))).containsExactlyInAnyOrder("a", "a/x");
    }

    private PageData<TopicTreeNode> query(Set<TopicTreeQuery.Has> has, Set<TopicTreeQuery.ClientStatus> statuses,
                                          boolean wildcardOnly, Integer greaterThan) {
        TopicTreeQuery q = new TopicTreeQuery(null, null, false, null, has, statuses, Set.of(), wildcardOnly, greaterThan,
                greaterThan == null ? null : SubscriptionOperation.GREATER);
        return s.query(q, n -> 0L, new PageLink(100, 0));
    }

    private static List<String> paths(PageData<TopicTreeNode> page) {
        return page.getData().stream().map(TopicTreeNode::path).toList();
    }

    @Test
    void pageBeyondIntRangeIsEmptyInsteadOfFailing() {
        PageData<TopicTreeNode> p = s.children(s.getRoot(), new PageLink(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertThat(p.getData()).isEmpty();
        assertThat(p.getTotalElements()).isEqualTo(s.getRoot().childrenCount());
    }

    @Test
    void searchMatchesAcrossLevelsAndInheritsToDescendants() {
        List<String> paths = s.search("RS/RO", new PageLink(100, 0)).getData().stream().map(TopicTreeNode::path).toList();
        assertThat(paths).contains("sensors/room1", "sensors/room1/#", "sensors/room2/temperature").doesNotContain("sensors");
    }
}
