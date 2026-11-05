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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.thingsboard.mqtt.broker.common.data.client.session.SubscriptionOperation;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.dto.TopicTreeEntityDto;
import org.thingsboard.mqtt.broker.dto.TopicTreeNodeDto;
import org.thingsboard.mqtt.broker.dto.TopicTreeSubscriptionDto;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeQuery.Has;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeQuery.TopicMatch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class TopicTreeSnapshotQueryTest {

    TopicTreeSnapshot s = TopicTreeFixtures.fixture();

    static PageLink sorted(String property, SortOrder.Direction direction) {
        return new PageLink(100, 0, null, new SortOrder(property, direction));
    }

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
    void childrenSortByTopicUsesSegment() {
        PageData<TopicTreeNode> p = s.children(s.getRoot(), sorted("topic", SortOrder.Direction.ASC));
        assertThat(p.getData().get(0).segment()).isEmpty();
    }

    @Test
    void childrenExplicitSubscriptionsDescEqualsDefaultOrder() {
        List<TopicTreeNode> unsorted = s.children(s.getRoot(), new PageLink(10, 0)).getData();
        List<TopicTreeNode> explicit = s.children(s.getRoot(), sorted("subscriptions", SortOrder.Direction.DESC)).getData();
        assertThat(explicit).containsExactlyElementsOf(unsorted);
        assertThat(s.children(s.getRoot(), sorted("subscriptions", SortOrder.Direction.ASC)).getData())
                .extracting(TopicTreeNode::subscriptionsTotal).isSorted();
    }

    @Test
    void childrenUnknownSortPropertyIsRejected() {
        assertThatThrownBy(() -> s.children(s.getRoot(), sorted("clients", SortOrder.Direction.ASC)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void textIsCaseInsensitiveSubstringOverPath() {
        assertThat(paths(s.query(TopicTreeQuery.text("TEMP"), new PageLink(50, 0))))
                .contains("sensors/+/temperature", "sensors/room1/temperature");
    }

    @Test
    void textMatchesAcrossLevelsAndInheritsToDescendants() {
        List<String> paths = paths(s.query(TopicTreeQuery.text("RS/RO"), new PageLink(100, 0)));
        assertThat(paths).contains("sensors/room1", "sensors/room1/#", "sensors/room2/temperature").doesNotContain("sensors");
    }

    @Test
    void topicContainsBehavesLikeText() {
        TopicTreeQuery q = TopicTreeQuery.builder().topic("RS/RO").topicMatch(TopicMatch.CONTAINS).build();
        assertThat(paths(s.query(q, new PageLink(100, 0))))
                .containsExactlyInAnyOrderElementsOf(paths(s.query(TopicTreeQuery.text("RS/RO"), new PageLink(100, 0))));
    }

    @Test
    void topicPrefixMatchesWholeLevelsOnlyAndIsCaseSensitive() {
        TopicTreeQuery q = TopicTreeQuery.builder().topic("sensors/room1").topicMatch(TopicMatch.PREFIX).build();
        assertThat(paths(s.query(q, new PageLink(50, 0))))
                .containsExactlyInAnyOrder("sensors/room1", "sensors/room1/#", "sensors/room1/temperature");
        TopicTreeQuery upper = TopicTreeQuery.builder().topic("SENSORS/room1").topicMatch(TopicMatch.PREFIX).build();
        assertThat(s.query(upper, new PageLink(50, 0)).getData()).isEmpty();
        TopicTreeQuery missing = TopicTreeQuery.builder().topic("nope").topicMatch(TopicMatch.PREFIX).build();
        assertThat(s.query(missing, new PageLink(50, 0)).getTotalElements()).isZero();
    }

    @Test
    void textAndPrefixCombine() {
        TopicTreeQuery q = TopicTreeQuery.builder().text("temp").topic("sensors/room1").topicMatch(TopicMatch.PREFIX).build();
        assertThat(paths(s.query(q, new PageLink(50, 0)))).containsExactly("sensors/room1/temperature");
        TopicTreeQuery both = TopicTreeQuery.builder().text("temp").topic("ROOM").topicMatch(TopicMatch.CONTAINS).build();
        assertThat(paths(s.query(both, new PageLink(50, 0))))
                .containsExactlyInAnyOrder("sensors/room1/temperature", "sensors/room2/temperature");
    }

    @Test
    void matchingPathsSelectsExactlyThoseFiltersAndCombinesWithOtherCriteria() {
        TopicTreeQuery q = TopicTreeQuery.builder().matchingPaths(Set.of("sensors/+/temperature", "sensors/#", "no/such")).build();
        assertThat(paths(s.query(q, new PageLink(50, 0)))).containsExactlyInAnyOrder("sensors/+/temperature", "sensors/#");
        TopicTreeQuery withText = TopicTreeQuery.builder().matchingPaths(Set.of("sensors/+/temperature", "sensors/#")).text("temp").build();
        assertThat(paths(s.query(withText, new PageLink(50, 0)))).containsExactly("sensors/+/temperature");
        TopicTreeQuery withPrefix = TopicTreeQuery.builder().matchingPaths(Set.of("sensors/+/temperature", "sensors/#"))
                .topic("sensors/#").topicMatch(TopicMatch.PREFIX).build();
        assertThat(paths(s.query(withPrefix, new PageLink(50, 0)))).containsExactly("sensors/#");
    }

    @Test
    void hasRetainedUsesSubtreeTotals() {
        assertThat(paths(query(Set.of(Has.RETAINED), false, null, null)))
                .contains("other", "sensors", "sensors/room1/temperature").doesNotContain("/leading", "a//b");
    }

    @Test
    void hasSubscriptionsSharedAndIntegrations() {
        assertThat(paths(query(Set.of(Has.SUBSCRIPTIONS), false, null, null)))
                .contains("sensors", "sensors/+/temperature", "/leading", "a//b").doesNotContain("other", "sensors/room1/temperature");
        assertThat(paths(query(Set.of(Has.SHARED), false, null, null))).containsExactlyInAnyOrder("sensors", "sensors/+", "sensors/+/temperature");
        assertThat(paths(query(Set.of(Has.INTEGRATIONS), false, null, null))).containsExactlyInAnyOrder("sensors", "sensors/#");
        assertThat(paths(query(Set.of(Has.SUBSCRIPTIONS, Has.RETAINED), false, null, null))).containsExactlyInAnyOrder("sensors", "sensors/room1");
    }

    @Test
    void wildcardOnly() {
        assertThat(paths(query(Set.of(), true, null, null)))
                .containsExactlyInAnyOrder("sensors/+", "sensors/#", "sensors/room1/#");
    }

    @ParameterizedTest
    @CsvSource({
            "EQUAL, 3, 'sensors/+,sensors/+/temperature'",
            "NOT_EQUAL, 0, 'sensors,sensors/+,sensors/+/temperature,sensors/room1,sensors/room1/#,sensors/#,/leading,,a,a/,a//b'",
            "GREATER, 2, 'sensors,sensors/+,sensors/+/temperature'",
            "LESS, 1, 'other,sensors/room1/temperature,sensors/room2,sensors/room2/temperature'",
            "GREATER_OR_EQUAL, 3, 'sensors,sensors/+,sensors/+/temperature'",
            "LESS_OR_EQUAL, 1, 'other,sensors/room1,sensors/room1/#,sensors/room1/temperature,sensors/room2,sensors/room2/temperature,sensors/#,/leading,,a,a/,a//b'"
    })
    void subscriptionCountComparison(SubscriptionOperation op, int value, String expected) {
        assertThat(paths(query(Set.of(), false, value, op))).containsExactlyInAnyOrder(expected.split(",", -1));
    }

    @Test
    void subscriptionOperationDefaultsToEqual() {
        TopicTreeQuery q = TopicTreeQuery.builder().subscriptions(3).build();
        assertThat(q.subscriptionOperation()).isEqualTo(SubscriptionOperation.EQUAL);
        assertThat(paths(s.query(q, new PageLink(50, 0)))).containsExactlyInAnyOrder("sensors/+", "sensors/+/temperature");
    }

    @ParameterizedTest
    @EnumSource(value = TopicTreeSortProperty.class, names = {"TOPIC", "SUBSCRIPTIONS", "RETAINED", "SHARED", "INTEGRATIONS"})
    void flatSortsAscAndDesc(TopicTreeSortProperty property) {
        List<TopicTreeNode> asc = s.query(allNodes(), sorted(property.name().toLowerCase(), SortOrder.Direction.ASC)).getData();
        List<TopicTreeNode> desc = s.query(allNodes(), sorted(property.name().toLowerCase(), SortOrder.Direction.DESC)).getData();
        assertThat(asc).hasSize(s.nodesCount() - 1);
        assertThat(asc).isSortedAccordingTo(comparator(property));
        assertThat(desc).isSortedAccordingTo(comparator(property).reversed());
    }

    @Test
    void flatTopicSortUsesFullPathAndUnknownPropertyIsRejected() {
        List<String> byPath = paths(s.query(allNodes(), sorted("topic", SortOrder.Direction.ASC)));
        assertThat(byPath).isSorted();
        assertThat(byPath.get(0)).isEmpty();
        assertThatThrownBy(() -> s.query(allNodes(), sorted("segment", SortOrder.Direction.ASC)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void entitiesClientsAreDistinctAcrossSubtreeWithSessionState() {
        PageData<TopicTreeEntityDto> p = s.entities(s.find("sensors"), TopicTreeEntityKind.CLIENTS, new PageLink(50, 0));
        assertThat(p.getData()).extracting(TopicTreeEntityDto::getId, TopicTreeEntityDto::getSubscriptions,
                        TopicTreeEntityDto::getConnected, TopicTreeEntityDto::getClientType)
                .containsExactly(tuple("c1", 2, true, "DEVICE"), tuple("c2", 1, false, "APPLICATION"), tuple("c3", 1, true, null));
    }

    @Test
    void entitiesIntegrationsAndSharedGroups() {
        assertThat(s.entities(s.find("sensors"), TopicTreeEntityKind.INTEGRATIONS, new PageLink(50, 0)).getData())
                .extracting(TopicTreeEntityDto::getId, TopicTreeEntityDto::getConnected).containsExactly(tuple("int-1", null));
        assertThat(s.entities(s.find("sensors"), TopicTreeEntityKind.SHARED, new PageLink(50, 0)).getData())
                .extracting(TopicTreeEntityDto::getId, TopicTreeEntityDto::getSubscriptions).containsExactly(tuple("g1", 2));
    }

    @Test
    void subscriptionsListSubtreeWithSharePrefixAndIntegrations() {
        TopicTreeNode sensors = s.find("sensors");
        PageData<TopicTreeSubscriptionDto> p = s.subscriptions(sensors, new PageLink(50, 0));
        assertThat(p.getTotalElements()).isEqualTo(sensors.subscriptionsTotal());
        assertThat(p.hasNext()).isFalse();
        assertThat(p.getData()).extracting(TopicTreeSubscriptionDto::getClientId, TopicTreeSubscriptionDto::getTopicFilter)
                .containsExactlyInAnyOrder(
                        tuple("c1", "sensors/+/temperature"),
                        tuple("c1", "sensors/room1/#"),
                        tuple("c2", "$share/g1/sensors/+/temperature"),
                        tuple("c3", "$share/g1/sensors/+/temperature"),
                        tuple("int-1", "sensors/#"));
        assertThat(p.getData()).filteredOn(TopicTreeSubscriptionDto::isIntegration).extracting(TopicTreeSubscriptionDto::getClientId).containsExactly("int-1");
        assertThat(p.getData()).filteredOn(d -> "c2".equals(d.getClientId())).extracting(TopicTreeSubscriptionDto::getShareName).containsExactly("g1");
    }

    @Test
    void subscriptionsPagesInTreeOrderWithoutGapsOrOverlap() {
        TopicTreeNode sensors = s.find("sensors");
        List<TopicTreeSubscriptionDto> all = s.subscriptions(sensors, new PageLink(50, 0)).getData();
        List<TopicTreeSubscriptionDto> paged = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            PageData<TopicTreeSubscriptionDto> p = s.subscriptions(sensors, new PageLink(2, page));
            assertThat(p.getTotalElements()).isEqualTo(5);
            assertThat(p.getTotalPages()).isEqualTo(3);
            assertThat(p.hasNext()).isEqualTo(page < 2);
            paged.addAll(p.getData());
        }
        assertThat(paged).containsExactlyElementsOf(all);
        assertThat(s.subscriptions(sensors, new PageLink(2, 3)).getData()).isEmpty();
        assertThat(s.subscriptions(s.find("sensors/room1/#"), new PageLink(10, 0)).getData())
                .extracting(TopicTreeSubscriptionDto::getTopicFilter).containsExactly("sensors/room1/#");
    }

    @Test
    void toDtoIncludesOnlyRequestedColumns() {
        TopicTreeNode sensors = s.find("sensors");
        TopicTreeNodeDto bare = s.toDto(sensors, Set.of());
        assertThat(bare.getClientsTotal()).isNull();
        assertThat(bare.getSharedGroupsTotal()).isNull();
        TopicTreeNodeDto full = s.toDto(s.find("other"), Set.of(TopicTreeColumn.values()));
        assertThat(full.getClientsTotal()).isZero();
        assertThat(full.getClientsOnline()).isZero();
        TopicTreeNodeDto shared = s.toDto(sensors, Set.of(TopicTreeColumn.SHARED, TopicTreeColumn.ONLINE));
        assertThat(shared.getSharedSubscriptionsTotal()).isEqualTo(2);
        assertThat(shared.getClientsOnline()).isEqualTo(2);
    }

    @Test
    void pageBeyondIntRangeIsEmptyInsteadOfFailing() {
        PageData<TopicTreeNode> p = s.children(s.getRoot(), new PageLink(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertThat(p.getData()).isEmpty();
        assertThat(p.getTotalElements()).isEqualTo(s.getRoot().childrenCount());
    }

    private PageData<TopicTreeNode> query(Set<Has> has, boolean wildcardOnly, Integer subscriptions, SubscriptionOperation op) {
        TopicTreeQuery q = TopicTreeQuery.builder().has(has).wildcardOnly(wildcardOnly).subscriptions(subscriptions)
                .subscriptionOperation(op).build();
        return s.query(q, new PageLink(100, 0));
    }

    private static TopicTreeQuery allNodes() {
        return TopicTreeQuery.builder().subscriptions(-1).subscriptionOperation(SubscriptionOperation.GREATER).build();
    }

    private static Comparator<TopicTreeNode> comparator(TopicTreeSortProperty property) {
        return switch (property) {
            case TOPIC -> Comparator.comparing(TopicTreeNode::path);
            case SUBSCRIPTIONS -> Comparator.comparingInt(TopicTreeNode::subscriptionsTotal);
            case RETAINED -> Comparator.comparingInt(TopicTreeNode::retainedTotal);
            case SHARED -> Comparator.comparingInt(TopicTreeNode::sharedGroupsTotal);
            case INTEGRATIONS -> Comparator.comparingInt(TopicTreeNode::integrationsTotal);
        };
    }

    private static List<String> paths(PageData<TopicTreeNode> page) {
        return page.getData().stream().map(TopicTreeNode::path).toList();
    }
}
