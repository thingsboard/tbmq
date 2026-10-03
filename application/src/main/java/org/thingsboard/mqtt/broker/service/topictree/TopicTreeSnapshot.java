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

public final class TopicTreeSnapshot {

    private static final Comparator<TopicTreeNode> BY_SUBSCRIPTIONS_DESC =
            Comparator.comparingInt(TopicTreeNode::subscriptionsTotal).reversed().thenComparing(TopicTreeNode::segment);

    public record ClientCounts(int total, int online, int offline) {
    }

    @Getter
    private final TopicTreeNode root;
    private final List<TopicTreeNode> allNodes;
    private final Map<String, ClientSessionInfo> sessions;
    @Getter
    private final long builtAt;

    TopicTreeSnapshot(TopicTreeNode root, List<TopicTreeNode> allNodes, Map<String, ClientSessionInfo> sessions, long builtAt) {
        this.root = root;
        this.allNodes = allNodes;
        this.sessions = sessions;
        this.builtAt = builtAt;
    }

    public int nodesCount() {
        return allNodes.size();
    }

    List<TopicTreeNode> allNodes() {
        return allNodes;
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
        Set<String> clients = new HashSet<>();
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
        for (TopicTreeNode n : allNodes) {
            if (n.path() != null && n.path().toLowerCase(Locale.ROOT).contains(needle)) {
                hits.add(n);
            }
        }
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
            rows.add(new TopicTreeEntityDto(id, c[0],
                    si == null ? null : si.isConnected(),
                    si == null || si.getType() == null ? null : si.getType().name()));
        });
        rows.sort(Comparator.comparingInt(TopicTreeEntityDto::getSubscriptions).reversed().thenComparing(TopicTreeEntityDto::getId));
        return page(rows, link);
    }

    public List<TopicTreeNode> top(String filter, Integer depth, boolean byClients, int limit) {
        String needle = filter == null ? null : filter.toLowerCase(Locale.ROOT);
        List<TopicTreeNode> hits = new ArrayList<>();
        for (TopicTreeNode n : allNodes) {
            if (n.path() == null) {
                continue;
            }
            if (depth != null && n.depth() != depth) {
                continue;
            }
            if (needle != null && !n.path().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            hits.add(n);
        }
        hits.sort(BY_SUBSCRIPTIONS_DESC);
        if (byClients) {
            int prefilter = Math.min(hits.size(), limit * 4);
            List<TopicTreeNode> head = new ArrayList<>(hits.subList(0, prefilter));
            head.sort(Comparator.comparingInt((TopicTreeNode n) -> clientCounts(n).total()).reversed());
            hits = head;
        }
        return hits.size() > limit ? new ArrayList<>(hits.subList(0, limit)) : hits;
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
        int from = Math.min(link.getPage() * link.getPageSize(), all.size());
        int to = Math.min(from + link.getPageSize(), all.size());
        int totalPages = link.getPageSize() == 0 ? 0 : (all.size() + link.getPageSize() - 1) / link.getPageSize();
        return new PageData<>(new ArrayList<>(all.subList(from, to)), totalPages, all.size(), to < all.size());
    }
}
