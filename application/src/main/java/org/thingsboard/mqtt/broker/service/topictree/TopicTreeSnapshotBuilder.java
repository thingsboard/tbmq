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

import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.subscription.IntegrationTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TopicTreeSnapshotBuilder {

    private TopicTreeSnapshotBuilder() {
    }

    public static TopicTreeSnapshot build(Map<String, Set<TopicSubscription>> subscriptions,
                                          Collection<String> retainedTopics,
                                          Map<String, ClientSessionInfo> sessions,
                                          long builtAt) {
        TopicTreeNode root = new TopicTreeNode(null, null);
        for (Map.Entry<String, Set<TopicSubscription>> e : subscriptions.entrySet()) {
            String clientId = e.getKey();
            for (TopicSubscription ts : new ArrayList<>(e.getValue())) {
                TopicTreeNode node = walk(root, ts.getTopicFilter());
                boolean integration = ts instanceof IntegrationTopicSubscription;
                node.addRef(new SubRef(clientId, ts.getQos(), integration, ts.getShareName()));
                node.subscriptionsExact++;
                if (integration) {
                    node.integrationsExact++;
                }
            }
        }
        for (String topic : retainedTopics) {
            walk(root, topic).retainedExact++;
        }
        List<TopicTreeNode> all = new ArrayList<>();
        int[] parents = new int[1024];
        Deque<TopicTreeNode> stack = new ArrayDeque<>();
        int[] parentStack = new int[1024];
        stack.push(root);
        parentStack[0] = -1;
        while (!stack.isEmpty()) {
            TopicTreeNode n = stack.pop();
            int index = all.size();
            if (index == parents.length) {
                parents = Arrays.copyOf(parents, index * 2);
            }
            parents[index] = parentStack[stack.size()];
            all.add(n);
            n.trimRefs();
            n.sharedGroupsExact = countGroups(n.refs());
            for (TopicTreeNode c : n.children()) {
                if (stack.size() == parentStack.length) {
                    parentStack = Arrays.copyOf(parentStack, stack.size() * 2);
                }
                parentStack[stack.size()] = index;
                stack.push(c);
            }
        }
        for (int i = all.size() - 1; i >= 0; i--) {
            TopicTreeNode n = all.get(i);
            n.subscriptionsTotal += n.subscriptionsExact;
            n.integrationsTotal += n.integrationsExact;
            n.sharedGroupsTotal += n.sharedGroupsExact;
            n.retainedTotal += n.retainedExact;
            for (TopicTreeNode c : n.children()) {
                n.subscriptionsTotal += c.subscriptionsTotal;
                n.integrationsTotal += c.integrationsTotal;
                n.sharedGroupsTotal += c.sharedGroupsTotal;
                n.retainedTotal += c.retainedTotal;
            }
        }
        return new TopicTreeSnapshot(root, all.toArray(new TopicTreeNode[0]), Arrays.copyOf(parents, all.size()), sessions, builtAt);
    }

    private static TopicTreeNode walk(TopicTreeNode root, String filter) {
        TopicTreeNode node = root;
        int start = 0;
        while (true) {
            int slash = filter.indexOf('/', start);
            if (slash < 0) {
                return node.child(filter.substring(start));
            }
            node = node.child(filter.substring(start, slash));
            start = slash + 1;
        }
    }

    private static int countGroups(List<SubRef> refs) {
        if (refs.isEmpty()) {
            return 0;
        }
        Set<String> groups = null;
        for (SubRef r : refs) {
            if (r.shareName() != null) {
                if (groups == null) {
                    groups = new HashSet<>();
                }
                groups.add(r.shareName());
            }
        }
        return groups == null ? 0 : groups.size();
    }
}
