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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TopicTreeSnapshot {

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
}
