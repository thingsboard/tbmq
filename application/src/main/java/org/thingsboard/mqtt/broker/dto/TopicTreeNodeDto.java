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
package org.thingsboard.mqtt.broker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeColumn;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeNode;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeSnapshot;

import java.util.Set;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TopicTreeNodeDto {

    private String path;
    private String segment;
    private boolean wildcard;
    private boolean hasChildren;
    private int childrenCount;
    private int subscriptionsExact;
    private int subscriptionsTotal;
    private int retainedExact;
    private int retainedTotal;
    private Integer clientsTotal;
    private Integer clientsOnline;
    private Integer clientsOffline;
    private Integer sharedGroupsExact;
    private Integer sharedGroupsTotal;
    private Integer sharedSubscriptionsTotal;
    private Integer integrationsExact;
    private Integer integrationsTotal;
    private Long dropped24h;
    private Object metrics;

    public static TopicTreeNodeDto of(TopicTreeNode n, TopicTreeSnapshot s, Set<TopicTreeColumn> cols) {
        TopicTreeNodeDtoBuilder b = TopicTreeNodeDto.builder()
                .path(n.path()).segment(n.segment()).wildcard(n.isWildcard())
                .hasChildren(n.childrenCount() > 0).childrenCount(n.childrenCount())
                .subscriptionsExact(n.subscriptionsExact()).subscriptionsTotal(n.subscriptionsTotal())
                .retainedExact(n.retainedExact()).retainedTotal(n.retainedTotal());
        if (cols.contains(TopicTreeColumn.CLIENTS) || cols.contains(TopicTreeColumn.ONLINE)) {
            TopicTreeSnapshot.ClientCounts cc = s.clientCounts(n);
            b.clientsTotal(cc.total());
            if (cols.contains(TopicTreeColumn.ONLINE)) {
                b.clientsOnline(cc.online()).clientsOffline(cc.offline());
            }
        }
        if (cols.contains(TopicTreeColumn.SHARED)) {
            b.sharedGroupsExact(n.sharedGroupsExact()).sharedGroupsTotal(n.sharedGroupsTotal());
        }
        if (cols.contains(TopicTreeColumn.INTEGRATIONS)) {
            b.integrationsExact(n.integrationsExact()).integrationsTotal(n.integrationsTotal());
        }
        return b.build();
    }
}
