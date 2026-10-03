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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TopicTreeNode {

    private final String segment;
    private final String path;
    private final int depth;
    private Map<String, TopicTreeNode> children = Collections.emptyMap();
    private List<SubRef> refs = Collections.emptyList();

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

    TopicTreeNode(String segment, String path, int depth) {
        this.segment = segment;
        this.path = path;
        this.depth = depth;
    }

    TopicTreeNode child(String segment) {
        TopicTreeNode existing = children.get(segment);
        if (existing != null) {
            return existing;
        }
        if (children.isEmpty()) {
            children = new HashMap<>(4);
        }
        TopicTreeNode created = new TopicTreeNode(segment, path == null ? segment : path + "/" + segment, depth + 1);
        children.put(segment, created);
        return created;
    }

    TopicTreeNode existingChild(String segment) {
        return children.get(segment);
    }

    void addRef(SubRef ref) {
        if (refs.isEmpty()) {
            refs = new ArrayList<>(2);
        }
        refs.add(ref);
    }

    public String segment() { return segment; }
    public String path() { return path; }
    public int depth() { return depth; }
    public Collection<TopicTreeNode> children() { return children.values(); }
    public int childrenCount() { return children.size(); }
    public List<SubRef> refs() { return refs; }
    public boolean isWildcard() { return "+".equals(segment) || "#".equals(segment); }
    public int subscriptionsExact() { return subscriptionsExact; }
    public int subscriptionsTotal() { return subscriptionsTotal; }
    public int integrationsExact() { return integrationsExact; }
    public int integrationsTotal() { return integrationsTotal; }
    public int sharedGroupsExact() { return sharedGroupsExact; }
    public int sharedGroupsTotal() { return sharedGroupsTotal; }
    public int retainedExact() { return retainedExact; }
    public int retainedTotal() { return retainedTotal; }
}
