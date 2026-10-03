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
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.subscription.ClientTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.IntegrationTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TopicTreeSnapshotBuilderTest {

    static ClientTopicSubscription sub(String filter, int qos) {
        return new ClientTopicSubscription(filter, qos);
    }

    static ClientTopicSubscription shared(String filter, String group) {
        return new ClientTopicSubscription(filter, 1, group);
    }

    static ClientSessionInfo session(String clientId, boolean connected) {
        return ClientSessionInfo.builder().clientId(clientId).connected(connected).build();
    }

    TopicTreeSnapshot fixture() {
        Map<String, Set<TopicSubscription>> subs = new HashMap<>();
        subs.put("c1", Set.of(sub("sensors/+/temperature", 1), sub("sensors/room1/#", 0)));
        subs.put("c2", Set.of(shared("sensors/+/temperature", "g1")));
        subs.put("c3", Set.of(shared("sensors/+/temperature", "g1"), sub("/leading", 0), sub("a//b", 0)));
        subs.put("int-1", Set.of(new IntegrationTopicSubscription("sensors/#")));
        Map<String, ClientSessionInfo> sessions = Map.of(
                "c1", session("c1", true), "c2", session("c2", false), "c3", session("c3", true));
        return TopicTreeSnapshotBuilder.build(subs, List.of("sensors/room1/temperature", "sensors/room2/temperature", "other"), sessions, 1L);
    }

    @Test
    void countsExactAndTotal() {
        TopicTreeSnapshot s = fixture();
        TopicTreeNode temp = s.find("sensors/+/temperature");
        assertThat(temp.subscriptionsExact()).isEqualTo(3);
        assertThat(temp.subscriptionsTotal()).isEqualTo(3);
        TopicTreeNode sensors = s.find("sensors");
        assertThat(sensors.subscriptionsExact()).isZero();
        assertThat(sensors.subscriptionsTotal()).isEqualTo(5);
        assertThat(s.find(null).subscriptionsTotal()).isEqualTo(7);
    }

    @Test
    void sharedGroupCountedOncePerFilter() {
        TopicTreeSnapshot s = fixture();
        assertThat(s.find("sensors/+/temperature").sharedGroupsExact()).isEqualTo(1);
        assertThat(s.find("sensors").sharedGroupsTotal()).isEqualTo(1);
    }

    @Test
    void integrationsAreSeparateFromClients() {
        TopicTreeSnapshot s = fixture();
        TopicTreeNode hash = s.find("sensors/#");
        assertThat(hash.integrationsExact()).isEqualTo(1);
        assertThat(hash.isWildcard()).isTrue();
        assertThat(s.clientCounts(hash).total()).isZero();
        assertThat(s.find("sensors").integrationsTotal()).isEqualTo(1);
    }

    @Test
    void distinctClientsWithOnlineSplit() {
        TopicTreeSnapshot s = fixture();
        TopicTreeSnapshot.ClientCounts cc = s.clientCounts(s.find("sensors/+/temperature"));
        assertThat(cc.total()).isEqualTo(3);
        assertThat(cc.online()).isEqualTo(2);
        assertThat(cc.offline()).isEqualTo(1);
        assertThat(s.clientCounts(s.find("sensors"))).isSameAs(s.clientCounts(s.find("sensors")));
    }

    @Test
    void retainedCountedOnConcreteTopics() {
        TopicTreeSnapshot s = fixture();
        assertThat(s.find("sensors/room1/temperature").retainedExact()).isEqualTo(1);
        assertThat(s.find("sensors").retainedTotal()).isEqualTo(2);
        assertThat(s.find("other").subscriptionsTotal()).isZero();
    }

    @Test
    void emptySegmentsAreAddressable() {
        TopicTreeSnapshot s = fixture();
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
        assertThat(fixture().find("nope/x")).isNull();
    }
}
