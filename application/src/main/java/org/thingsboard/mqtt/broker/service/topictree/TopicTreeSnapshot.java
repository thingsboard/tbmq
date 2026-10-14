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

import lombok.Getter;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.client.session.SubscriptionOperation;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.dto.TopicTreeEntityDto;
import org.thingsboard.mqtt.broker.dto.TopicTreeNodeDto;
import org.thingsboard.mqtt.broker.dto.TopicTreeSubscriptionDto;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class TopicTreeSnapshot {

    private static final Comparator<TopicTreeNode> BY_SUBSCRIPTIONS_DESC =
            Comparator.comparingInt(TopicTreeNode::subscriptionsTotal).reversed().thenComparing(TopicTreeNode::segment);

    public record ClientCounts(int total, int online, int offline) {
    }

    // The criterion that drives a query scan; the others are checked only on its candidates.
    private enum Driver { PATHS, PREFIX, TEXT, TOPIC, ALL }

    @Getter
    private final TopicTreeNode root;
    // Preorder (index 0 is the root): the nodes, each node's parent index and its lower-cased segment. Search scans
    // these flat arrays instead of chasing tree pointers or keeping a full path string per node.
    private final TopicTreeNode[] preorder;
    private final int[] parents;
    private final String[] lowerSegments;
    private final Map<String, ClientSessionInfo> sessions;
    @Getter
    private final long builtAt;

    TopicTreeSnapshot(TopicTreeNode root, TopicTreeNode[] preorder, int[] parents, Map<String, ClientSessionInfo> sessions, long builtAt) {
        this.root = root;
        this.preorder = preorder;
        this.parents = parents;
        this.sessions = sessions;
        this.builtAt = builtAt;
        this.lowerSegments = new String[preorder.length];
        for (int i = 1; i < preorder.length; i++) {
            lowerSegments[i] = preorder[i].segment().toLowerCase(Locale.ROOT);
        }
    }

    public int nodesCount() {
        return preorder.length;
    }

    public TopicTreeNode find(String path) {
        if (path == null) {
            return root;
        }
        TopicTreeNode node = root;
        int start = 0;
        while (node != null) {
            int slash = path.indexOf('/', start);
            if (slash < 0) {
                return node.existingChild(path.substring(start));
            }
            node = node.existingChild(path.substring(start, slash));
            start = slash + 1;
        }
        return null;
    }

    public TopicTreeNodeDto toDto(TopicTreeNode n, Set<TopicTreeColumn> cols) {
        TopicTreeNodeDto.TopicTreeNodeDtoBuilder b = TopicTreeNodeDto.builder()
                .path(n.path()).segment(n.segment()).wildcard(n.isWildcard())
                .hasChildren(n.childrenCount() > 0).childrenCount(n.childrenCount())
                .subscriptionsExact(n.subscriptionsExact()).subscriptionsTotal(n.subscriptionsTotal())
                .retainedExact(n.retainedExact()).retainedTotal(n.retainedTotal());
        if (cols.contains(TopicTreeColumn.CLIENTS) || cols.contains(TopicTreeColumn.ONLINE)) {
            ClientCounts cc = clientCounts(n);
            b.clientsTotal(cc.total());
            if (cols.contains(TopicTreeColumn.ONLINE)) {
                b.clientsOnline(cc.online()).clientsOffline(cc.offline());
            }
        }
        if (cols.contains(TopicTreeColumn.SHARED)) {
            b.sharedGroupsExact(n.sharedGroupsExact()).sharedGroupsTotal(n.sharedGroupsTotal())
                    .sharedSubscriptionsTotal(n.sharedSubscriptionsTotal());
        }
        if (cols.contains(TopicTreeColumn.INTEGRATIONS)) {
            b.integrationsExact(n.integrationsExact()).integrationsTotal(n.integrationsTotal());
        }
        return b.build();
    }

    public ClientCounts clientCounts(TopicTreeNode node) {
        ClientCounts cached = node.clientCounts;
        if (cached != null) {
            return cached;
        }
        Set<String> clients = new HashSet<>(Math.max(16, (int) (node.subscriptionsTotal / 0.75f) + 1));
        forEachInSubtree(node, n -> {
            for (SubRef r : n.refs()) {
                if (!r.integration()) {
                    clients.add(r.clientId());
                }
            }
        });
        int online = 0;
        for (String c : clients) {
            ClientSessionInfo s = sessions.get(c);
            if (s != null && s.isConnected()) {
                online++;
            }
        }
        ClientCounts result = new ClientCounts(clients.size(), online, clients.size() - online);
        node.clientCounts = result;
        return result;
    }

    public PageData<TopicTreeNode> children(TopicTreeNode parent, PageLink link) {
        TopicTreeNode[] sorted = parent.sortedChildren;
        if (sorted == null) {
            sorted = parent.children().toArray(new TopicTreeNode[0]);
            Arrays.sort(sorted, BY_SUBSCRIPTIONS_DESC);
            parent.sortedChildren = sorted;
        }
        List<TopicTreeNode> list = Arrays.asList(sorted);
        Comparator<TopicTreeNode> cmp = comparator(link.getSortOrder(), false);
        if (cmp != null && !isCachedOrder(link.getSortOrder())) {
            list = new ArrayList<>(list);
            list.sort(cmp);
        }
        return page(list, link);
    }

    // The cached array is already in subscriptions DESC order, which is also what the UI asks for by default.
    private static boolean isCachedOrder(SortOrder sortOrder) {
        return TopicTreeSortProperty.parse(sortOrder.getProperty()) == TopicTreeSortProperty.SUBSCRIPTIONS
                && sortOrder.getDirection() == SortOrder.Direction.DESC;
    }

    public PageData<TopicTreeNode> query(TopicTreeQuery q, PageLink link) {
        List<TopicTreeNode> hits = new ArrayList<>();
        Driver driver = driver(q);
        Predicate<TopicTreeNode> matches = predicate(q, driver);
        forEachCandidate(q, driver, n -> {
            if (matches.test(n)) {
                hits.add(n);
            }
        });
        Comparator<TopicTreeNode> cmp = comparator(link.getSortOrder(), true);
        hits.sort(cmp != null ? cmp : BY_SUBSCRIPTIONS_DESC);
        return page(hits, link);
    }

    private static Driver driver(TopicTreeQuery q) {
        if (q.matchingPaths() != null) {
            return Driver.PATHS;
        }
        if (q.topic() != null && q.topicMatch() == TopicTreeQuery.TopicMatch.PREFIX) {
            return Driver.PREFIX;
        }
        if (q.text() != null) {
            return Driver.TEXT;
        }
        return q.topic() != null ? Driver.TOPIC : Driver.ALL;
    }

    // Resolved paths and prefixes are looked up directly, substrings use the segment scan; only a query without
    // any of them visits every node.
    private void forEachCandidate(TopicTreeQuery q, Driver driver, Consumer<TopicTreeNode> action) {
        switch (driver) {
            case PATHS -> {
                for (String path : q.matchingPaths()) {
                    TopicTreeNode n = find(path);
                    if (n != null && n != root) {
                        action.accept(n);
                    }
                }
            }
            case PREFIX -> {
                TopicTreeNode start = find(q.topic());
                if (start != null && start != root) {
                    forEachInSubtree(start, action);
                }
            }
            case TEXT -> forEachMatching(q.text().toLowerCase(Locale.ROOT), action);
            case TOPIC -> forEachMatching(q.topic().toLowerCase(Locale.ROOT), action);
            case ALL -> {
                for (int i = 1; i < preorder.length; i++) {
                    action.accept(preorder[i]);
                }
            }
        }
    }

    private Predicate<TopicTreeNode> predicate(TopicTreeQuery q, Driver driver) {
        Predicate<TopicTreeNode> p = n -> true;
        if (q.wildcardOnly()) {
            p = p.and(TopicTreeNode::isWildcard);
        }
        if (q.text() != null && driver != Driver.TEXT) {
            p = p.and(pathContains(q.text()));
        }
        if (q.topic() != null && driver != Driver.PREFIX && driver != Driver.TOPIC) {
            String topic = q.topic();
            p = p.and(q.topicMatch() == TopicTreeQuery.TopicMatch.PREFIX ? n -> isPrefixOf(topic, n.path()) : pathContains(topic));
        }
        for (TopicTreeQuery.Has has : q.has()) {
            p = p.and(switch (has) {
                case SUBSCRIPTIONS -> n -> n.subscriptionsTotal() > 0;
                case RETAINED -> n -> n.retainedTotal() > 0;
                case SHARED -> n -> n.sharedGroupsTotal() > 0;
                case INTEGRATIONS -> n -> n.integrationsTotal() > 0;
            });
        }
        if (q.subscriptions() != null) {
            int value = q.subscriptions();
            SubscriptionOperation op = q.subscriptionOperation();
            p = p.and(n -> compare(n.subscriptionsTotal(), op, value));
        }
        return p;
    }

    private static Predicate<TopicTreeNode> pathContains(String text) {
        String needle = text.toLowerCase(Locale.ROOT);
        return n -> n.path().toLowerCase(Locale.ROOT).contains(needle);
    }

    private static boolean isPrefixOf(String prefix, String path) {
        return path.equals(prefix) || (path.startsWith(prefix) && path.charAt(prefix.length()) == '/');
    }

    private static boolean compare(int actual, SubscriptionOperation op, int value) {
        return switch (op) {
            case EQUAL -> actual == value;
            case NOT_EQUAL -> actual != value;
            case GREATER -> actual > value;
            case LESS -> actual < value;
            case GREATER_OR_EQUAL -> actual >= value;
            case LESS_OR_EQUAL -> actual <= value;
        };
    }

    public PageData<TopicTreeEntityDto> entities(TopicTreeNode node, TopicTreeEntityKind kind, PageLink link) {
        Map<String, int[]> counts = new HashMap<>();
        forEachInSubtree(node, n -> {
            for (SubRef r : n.refs()) {
                String key = switch (kind) {
                    case CLIENTS -> r.integration() ? null : r.clientId();
                    case INTEGRATIONS -> r.integration() ? r.clientId() : null;
                    case SHARED -> r.shareName();
                };
                if (key != null) {
                    counts.computeIfAbsent(key, k -> new int[1])[0]++;
                }
            }
        });
        List<TopicTreeEntityDto> rows = new ArrayList<>(counts.size());
        counts.forEach((id, c) -> {
            ClientSessionInfo si = kind == TopicTreeEntityKind.CLIENTS ? sessions.get(id) : null;
            rows.add(new TopicTreeEntityDto(id, null, c[0],
                    si == null ? null : si.isConnected(),
                    si == null || si.getType() == null ? null : si.getType().name()));
        });
        rows.sort(Comparator.comparingInt(TopicTreeEntityDto::getSubscriptions).reversed().thenComparing(TopicTreeEntityDto::getId));
        return page(rows, link);
    }

    // Rows come in tree order: a topic's own subscriptions, then those of its subtopics. The total is known from the
    // counts and whole subtrees before the page are skipped by count, so only the requested page is materialised.
    // Shared subscriptions are stored under their base filter, so the original "$share/<group>/<filter>" is rebuilt.
    public PageData<TopicTreeSubscriptionDto> subscriptions(TopicTreeNode node, PageLink link) {
        int total = node.subscriptionsTotal();
        int size = link.getPageSize();
        long skip = Math.min((long) link.getPage() * size, total);
        List<TopicTreeSubscriptionDto> rows = new ArrayList<>((int) Math.min(size, total - skip));
        Deque<TopicTreeNode> stack = new ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty() && rows.size() < size) {
            TopicTreeNode n = stack.pop();
            if (skip >= n.subscriptionsTotal()) {
                skip -= n.subscriptionsTotal();
                continue;
            }
            List<SubRef> refs = n.refs();
            int start = (int) Math.min(skip, refs.size());
            skip -= start;
            if (start < refs.size()) {
                String path = n.path();
                for (int i = start; i < refs.size() && rows.size() < size; i++) {
                    SubRef r = refs.get(i);
                    String filter = r.shareName() == null ? path : BrokerConstants.SHARED_SUBSCRIPTION_PREFIX + r.shareName() + "/" + path;
                    rows.add(new TopicTreeSubscriptionDto(r.clientId(), filter, r.qos(), r.integration(), r.shareName(), null));
                }
            }
            n.children().forEach(stack::push);
        }
        return PageData.of(rows, total, link);
    }

    private static void forEachInSubtree(TopicTreeNode node, Consumer<TopicTreeNode> action) {
        Deque<TopicTreeNode> stack = new ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty()) {
            TopicTreeNode n = stack.pop();
            action.accept(n);
            n.children().forEach(stack::push);
        }
    }

    // A node matches when its parent did, or when the needle ends inside its own segment. In preorder the shared
    // buffer always starts with the parent's path, so each path is built from its parent's by one append.
    private void forEachMatching(String needle, Consumer<TopicTreeNode> action) {
        int n = preorder.length;
        int needleLength = needle.length();
        // Without a '/' the needle cannot span two levels, so only the node's own segment needs checking.
        boolean crossesLevels = needle.indexOf('/') >= 0;
        int[] lengths = new int[n];
        boolean[] matched = new boolean[n];
        StringBuilder path = new StringBuilder(256);
        for (int i = 1; i < n; i++) {
            int parent = parents[i];
            if (matched[parent]) {
                matched[i] = true;
                action.accept(preorder[i]);
                continue;
            }
            if (!crossesLevels) {
                if (lowerSegments[i].contains(needle)) {
                    matched[i] = true;
                    action.accept(preorder[i]);
                }
                continue;
            }
            int parentLength = lengths[parent];
            path.setLength(parentLength);
            if (parent != 0) {
                path.append('/');
            }
            path.append(lowerSegments[i]);
            lengths[i] = path.length();
            if (path.indexOf(needle, Math.max(0, parentLength - needleLength + 1)) >= 0) {
                matched[i] = true;
                action.accept(preorder[i]);
            }
        }
    }

    // Siblings share their parent's path, so the tree sorts by segment; a flat listing needs the full path.
    private Comparator<TopicTreeNode> comparator(SortOrder sortOrder, boolean flat) {
        if (sortOrder == null || sortOrder.getProperty() == null) {
            return null;
        }
        Comparator<TopicTreeNode> c = switch (TopicTreeSortProperty.parse(sortOrder.getProperty())) {
            case TOPIC -> flat ? Comparator.comparing(TopicTreeNode::path) : Comparator.comparing(TopicTreeNode::segment);
            case SUBSCRIPTIONS -> Comparator.comparingInt(TopicTreeNode::subscriptionsTotal);
            case RETAINED -> Comparator.comparingInt(TopicTreeNode::retainedTotal);
            case SHARED -> Comparator.comparingInt(TopicTreeNode::sharedGroupsTotal);
            case INTEGRATIONS -> Comparator.comparingInt(TopicTreeNode::integrationsTotal);
        };
        return sortOrder.getDirection() == SortOrder.Direction.DESC ? c.reversed() : c;
    }

    static <T> PageData<T> page(List<T> all, PageLink link) {
        int from = (int) Math.min((long) link.getPage() * link.getPageSize(), all.size());
        int to = (int) Math.min((long) from + link.getPageSize(), all.size());
        return PageData.of(new ArrayList<>(all.subList(from, to)), all.size(), link);
    }
}
