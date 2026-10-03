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

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.dto.TopicTreeEntityDto;

import static org.assertj.core.api.Assertions.assertThat;

class TopicTreeSnapshotQueryTest {

    TopicTreeSnapshot s = new TopicTreeSnapshotBuilderTest().fixture();

    @Test
    void rootChildrenSortedBySubscriptionsDesc() {
        PageData<TopicTreeNode> p = s.children(s.getRoot(), new PageLink(10, 0));
        assertThat(p.getData().get(0).segment()).isEqualTo("sensors");
        assertThat(p.getTotalElements()).isEqualTo(s.getRoot().childrenCount());
    }

    @Test
    void childrenPaging() {
        PageData<TopicTreeNode> p = s.children(s.getRoot(), new PageLink(1, 1));
        assertThat(p.getData()).hasSize(1);
        assertThat(p.hasNext()).isTrue();
    }

    @Test
    void childrenSortBySegmentAsc() {
        PageData<TopicTreeNode> p = s.children(s.getRoot(), new PageLink(10, 0, null, new SortOrder("segment", SortOrder.Direction.ASC)));
        assertThat(p.getData().get(0).segment()).isEmpty();
    }

    @Test
    void searchIsCaseInsensitiveSubstringOverPath() {
        PageData<TopicTreeNode> p = s.search("TEMP", new PageLink(50, 0));
        assertThat(p.getData()).extracting(TopicTreeNode::path)
                .contains("sensors/+/temperature", "sensors/room1/temperature");
    }

    @Test
    void entitiesClientsAreDistinctAcrossSubtree() {
        PageData<TopicTreeEntityDto> p = s.entities(s.find("sensors"), TopicTreeEntityDto.Kind.CLIENTS, new PageLink(50, 0));
        assertThat(p.getData()).extracting(TopicTreeEntityDto::getId).containsExactlyInAnyOrder("c1", "c2", "c3");
    }

    @Test
    void entitiesIntegrationsAndSharedGroups() {
        assertThat(s.entities(s.find("sensors"), TopicTreeEntityDto.Kind.INTEGRATIONS, new PageLink(50, 0)).getData())
                .extracting(TopicTreeEntityDto::getId).containsExactly("int-1");
        assertThat(s.entities(s.find("sensors"), TopicTreeEntityDto.Kind.SHARED, new PageLink(50, 0)).getData())
                .extracting(TopicTreeEntityDto::getId).containsExactly("g1");
    }

    @Test
    void topByClientsHonoursDepthAndLimit() {
        assertThat(s.top("sensors", 3, true, 1)).extracting(TopicTreeNode::path).containsExactly("sensors/+/temperature");
        assertThat(s.top("sensors", null, false, 10).get(0).path()).isEqualTo("sensors");
    }
}
