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

import static org.assertj.core.api.Assertions.assertThat;

class TopicTreeSnapshotBuilderTest {

    TopicTreeSnapshot s = TopicTreeFixtures.fixture();

    @Test
    void countsExactAndTotal() {
        TopicTreeNode temp = s.find("sensors/+/temperature");
        assertThat(temp.subscriptionsExact()).isEqualTo(3);
        assertThat(temp.subscriptionsTotal()).isEqualTo(3);
        TopicTreeNode sensors = s.find("sensors");
        assertThat(sensors.subscriptionsExact()).isZero();
        assertThat(sensors.subscriptionsTotal()).isEqualTo(5);
        assertThat(s.find(null).subscriptionsTotal()).isEqualTo(7);
    }

    @Test
    void sharedGroupCountedOncePerFilterAndSharedSubscriptionsRolledUp() {
        assertThat(s.find("sensors/+/temperature").sharedGroupsExact()).isEqualTo(1);
        assertThat(s.find("sensors").sharedGroupsTotal()).isEqualTo(1);
        assertThat(s.find("sensors/+/temperature").sharedSubscriptionsTotal()).isEqualTo(2);
        assertThat(s.find("sensors").sharedSubscriptionsTotal()).isEqualTo(2);
        assertThat(s.find("sensors/room1/#").sharedSubscriptionsTotal()).isZero();
        assertThat(s.getRoot().sharedSubscriptionsTotal()).isEqualTo(2);
    }

    @Test
    void integrationsAreSeparateFromClients() {
        TopicTreeNode hash = s.find("sensors/#");
        assertThat(hash.integrationsExact()).isEqualTo(1);
        assertThat(hash.isWildcard()).isTrue();
        assertThat(s.clientCounts(hash).total()).isZero();
        assertThat(s.find("sensors").integrationsTotal()).isEqualTo(1);
    }

    @Test
    void distinctClientsWithOnlineSplit() {
        TopicTreeSnapshot.ClientCounts cc = s.clientCounts(s.find("sensors/+/temperature"));
        assertThat(cc.total()).isEqualTo(3);
        assertThat(cc.online()).isEqualTo(2);
        assertThat(cc.offline()).isEqualTo(1);
        assertThat(s.clientCounts(s.find("sensors"))).isSameAs(s.clientCounts(s.find("sensors")));
    }

    @Test
    void retainedCountedOnConcreteTopics() {
        assertThat(s.find("sensors/room1/temperature").retainedExact()).isEqualTo(1);
        assertThat(s.find("sensors").retainedTotal()).isEqualTo(2);
        assertThat(s.find("other").subscriptionsTotal()).isZero();
    }

    @Test
    void emptySegmentsAreAddressable() {
        TopicTreeNode empty = s.find("");
        assertThat(empty).isNotNull();
        assertThat(empty.segment()).isEmpty();
        assertThat(s.find("/leading").subscriptionsExact()).isEqualTo(1);
        assertThat(s.find("a//b").subscriptionsExact()).isEqualTo(1);
        assertThat(s.find("a/").segment()).isEmpty();
        assertThat(s.find(null).path()).isNull();
    }

    @Test
    void missingPathReturnsNull() {
        assertThat(s.find("nope/x")).isNull();
    }
}
