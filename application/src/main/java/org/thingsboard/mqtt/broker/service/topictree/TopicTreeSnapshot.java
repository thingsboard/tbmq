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
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.dto.TopicTreeEntityDto;
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

public final class TopicTreeSnapshot {

    private static final Comparator<TopicTreeNode> BY_SUBSCRIPTIONS_DESC =
            Comparator.comparingInt(TopicTreeNode::subscriptionsTotal).reversed().thenComparing(TopicTreeNode::segment);

    public record ClientCounts(int total, int online, int offline) {
    }

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

    public ClientCounts clientCounts(TopicTreeNode node) {
        ClientCounts cached = node.clientCounts;
        if (cached != null) {
            return cached;
        }
        Set<String> clients = new HashSet<>(Math.max(16, (int) (node.subscriptionsTotal / 0.75f) + 1));
        Deque<TopicTreeNode> stack = new ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty()) {
            TopicTreeNode n = stack.pop();
            for (SubRef r : n.refs()) {
                if (!r.integration()) {
                    clients.add(r.clientId());
                }
            }
            n.children().forEach(stack::push);
        }
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
        Comparator<TopicTreeNode> cmp = comparator(link.getSortOrder());
        List<TopicTreeNode> list = Arrays.asList(sorted);
        if (cmp != null) {
            list = new ArrayList<>(list);
            list.sort(cmp);
        }
        return page(list, link);
    }

    public PageData<TopicTreeNode> search(String text, PageLink link) {
        String needle = text.toLowerCase(Locale.ROOT);
        List<TopicTreeNode> hits = new ArrayList<>();
        forEachMatching(needle, hits::add);
        Comparator<TopicTreeNode> cmp = comparator(link.getSortOrder());
        hits.sort(cmp != null ? cmp : BY_SUBSCRIPTIONS_DESC);
        return page(hits, link);
    }

    public PageData<TopicTreeEntityDto> entities(TopicTreeNode node, TopicTreeEntityDto.Kind kind, PageLink link) {
        Map<String, int[]> counts = new HashMap<>();
        Deque<TopicTreeNode> stack = new ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty()) {
            TopicTreeNode n = stack.pop();
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
            n.children().forEach(stack::push);
        }
        List<TopicTreeEntityDto> rows = new ArrayList<>(counts.size());
        counts.forEach((id, c) -> {
            ClientSessionInfo si = kind == TopicTreeEntityDto.Kind.CLIENTS ? sessions.get(id) : null;
            rows.add(new TopicTreeEntityDto(id, null, c[0],
                    si == null ? null : si.isConnected(),
                    si == null || si.getType() == null ? null : si.getType().name()));
        });
        rows.sort(Comparator.comparingInt(TopicTreeEntityDto::getSubscriptions).reversed().thenComparing(TopicTreeEntityDto::getId));
        return page(rows, link);
    }

    public int sharedSubscriptions(TopicTreeNode node) {
        int count = 0;
        Deque<TopicTreeNode> stack = new ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty()) {
            TopicTreeNode n = stack.pop();
            for (SubRef r : n.refs()) {
                if (r.shareName() != null) {
                    count++;
                }
            }
            n.children().forEach(stack::push);
        }
        return count;
    }

    // Shared subscriptions are stored under their base filter, so the original "$share/<group>/<filter>" is rebuilt here.
    public PageData<TopicTreeSubscriptionDto> subscriptions(TopicTreeNode node, PageLink link) {
        List<TopicTreeSubscriptionDto> rows = new ArrayList<>();
        Deque<TopicTreeNode> stack = new ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty()) {
            TopicTreeNode n = stack.pop();
            List<SubRef> refs = n.refs();
            if (!refs.isEmpty()) {
                String path = n.path();
                for (SubRef r : refs) {
                    String filter = r.shareName() == null ? path : "$share/" + r.shareName() + "/" + path;
                    rows.add(new TopicTreeSubscriptionDto(r.clientId(), filter, r.qos(), r.integration(), r.shareName(), null));
                }
            }
            n.children().forEach(stack::push);
        }
        rows.sort(Comparator.comparing(TopicTreeSubscriptionDto::getClientId).thenComparing(TopicTreeSubscriptionDto::getTopicFilter));
        return page(rows, link);
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

    private static Comparator<TopicTreeNode> comparator(SortOrder sortOrder) {
        if (sortOrder == null || sortOrder.getProperty() == null) {
            return null;
        }
        Comparator<TopicTreeNode> c = switch (sortOrder.getProperty()) {
            case "segment" -> Comparator.comparing(TopicTreeNode::segment);
            case "subscriptions" -> Comparator.comparingInt(TopicTreeNode::subscriptionsTotal);
            case "retained" -> Comparator.comparingInt(TopicTreeNode::retainedTotal);
            default -> null;
        };
        if (c == null) {
            return null;
        }
        return sortOrder.getDirection() == SortOrder.Direction.DESC ? c.reversed() : c;
    }

    private static <T> PageData<T> page(List<T> all, PageLink link) {
        int from = (int) Math.min((long) link.getPage() * link.getPageSize(), all.size());
        int to = (int) Math.min((long) from + link.getPageSize(), all.size());
        int totalPages = link.getPageSize() == 0 ? 0 : (all.size() + link.getPageSize() - 1) / link.getPageSize();
        return new PageData<>(new ArrayList<>(all.subList(from, to)), totalPages, all.size(), to < all.size());
    }
}
