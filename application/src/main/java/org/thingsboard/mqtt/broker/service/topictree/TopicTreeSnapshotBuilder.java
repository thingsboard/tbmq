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
            n.sharedSubscriptionsTotal += countShared(n.refs());
            for (TopicTreeNode c : n.children()) {
                n.subscriptionsTotal += c.subscriptionsTotal;
                n.integrationsTotal += c.integrationsTotal;
                n.sharedGroupsTotal += c.sharedGroupsTotal;
                n.retainedTotal += c.retainedTotal;
                n.sharedSubscriptionsTotal += c.sharedSubscriptionsTotal;
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

    private static int countShared(List<SubRef> refs) {
        int count = 0;
        for (SubRef r : refs) {
            if (r.shareName() != null) {
                count++;
            }
        }
        return count;
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
