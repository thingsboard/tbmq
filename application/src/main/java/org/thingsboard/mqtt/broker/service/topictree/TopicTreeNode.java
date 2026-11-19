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

import org.thingsboard.mqtt.broker.common.data.BrokerConstants;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TopicTreeNode {

    // Most nodes have a handful of children, where a HashMap costs several times the array; large fan-outs switch to a map.
    private static final int ARRAY_CHILDREN_LIMIT = 8;
    private static final TopicTreeNode[] NO_CHILDREN = new TopicTreeNode[0];
    private static final SubRef[] NO_REFS = new SubRef[0];

    private final String segment;
    private final TopicTreeNode parent;
    private final int depth;
    private TopicTreeNode[] childArray = NO_CHILDREN;
    private Map<String, TopicTreeNode> childMap;
    private SubRef[] refs = NO_REFS;
    private int refsCount;

    int subscriptionsExact;
    int subscriptionsTotal;
    int integrationsExact;
    int integrationsTotal;
    int sharedGroupsExact;
    int sharedGroupsTotal;
    int retainedExact;
    int retainedTotal;
    int sharedSubscriptionsTotal;

    volatile TopicTreeSnapshot.ClientCounts clientCounts;
    volatile TopicTreeNode[] sortedChildren;

    TopicTreeNode(String segment, TopicTreeNode parent) {
        this.segment = segment;
        this.parent = parent;
        this.depth = parent == null ? 0 : parent.depth + 1;
    }

    TopicTreeNode child(String segment) {
        TopicTreeNode existing = existingChild(segment);
        if (existing != null) {
            return existing;
        }
        TopicTreeNode created = new TopicTreeNode(segment, this);
        if (childMap != null) {
            childMap.put(segment, created);
        } else if (childArray.length < ARRAY_CHILDREN_LIMIT) {
            childArray = Arrays.copyOf(childArray, childArray.length + 1);
            childArray[childArray.length - 1] = created;
        } else {
            childMap = new HashMap<>(ARRAY_CHILDREN_LIMIT * 4);
            for (TopicTreeNode c : childArray) {
                childMap.put(c.segment, c);
            }
            childMap.put(segment, created);
            childArray = null;
        }
        return created;
    }

    TopicTreeNode existingChild(String segment) {
        if (childMap != null) {
            return childMap.get(segment);
        }
        for (TopicTreeNode c : childArray) {
            if (c.segment.equals(segment)) {
                return c;
            }
        }
        return null;
    }

    void addRef(SubRef ref) {
        if (refsCount == refs.length) {
            refs = Arrays.copyOf(refs, Math.max(2, refsCount * 2));
        }
        refs[refsCount++] = ref;
    }

    void trimRefs() {
        if (refsCount < refs.length) {
            refs = Arrays.copyOf(refs, refsCount);
        }
    }

    public String segment() { return segment; }
    public int depth() { return depth; }
    public Collection<TopicTreeNode> children() { return childMap != null ? childMap.values() : Arrays.asList(childArray); }
    public int childrenCount() { return childMap != null ? childMap.size() : childArray.length; }
    public List<SubRef> refs() { return Arrays.asList(refs).subList(0, refsCount); }
    public boolean isWildcard() { return BrokerConstants.SINGLE_LEVEL_WILDCARD.equals(segment) || BrokerConstants.MULTI_LEVEL_WILDCARD.equals(segment); }
    public int subscriptionsExact() { return subscriptionsExact; }
    public int subscriptionsTotal() { return subscriptionsTotal; }
    public int integrationsExact() { return integrationsExact; }
    public int integrationsTotal() { return integrationsTotal; }
    public int sharedGroupsExact() { return sharedGroupsExact; }
    public int sharedGroupsTotal() { return sharedGroupsTotal; }
    public int retainedExact() { return retainedExact; }
    public int retainedTotal() { return retainedTotal; }
    public int sharedSubscriptionsTotal() { return sharedSubscriptionsTotal; }

    // Derived rather than stored: a full path per node dominated the snapshot heap.
    public String path() {
        if (parent == null) {
            return null;
        }
        TopicTreeNode[] chain = new TopicTreeNode[depth];
        int length = depth - 1;
        for (TopicTreeNode n = this; n.parent != null; n = n.parent) {
            chain[n.depth - 1] = n;
            length += n.segment.length();
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < chain.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(chain[i].segment);
        }
        return sb.toString();
    }
}
