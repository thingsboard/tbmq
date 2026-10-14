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
