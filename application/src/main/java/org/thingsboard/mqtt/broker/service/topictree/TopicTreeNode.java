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
    public boolean isWildcard() { return "+".equals(segment) || "#".equals(segment); }
    public int subscriptionsExact() { return subscriptionsExact; }
    public int subscriptionsTotal() { return subscriptionsTotal; }
    public int integrationsExact() { return integrationsExact; }
    public int integrationsTotal() { return integrationsTotal; }
    public int sharedGroupsExact() { return sharedGroupsExact; }
    public int sharedGroupsTotal() { return sharedGroupsTotal; }
    public int retainedExact() { return retainedExact; }
    public int retainedTotal() { return retainedTotal; }

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
