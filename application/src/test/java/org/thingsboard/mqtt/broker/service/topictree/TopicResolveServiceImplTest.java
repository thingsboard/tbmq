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
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionService;
import org.thingsboard.mqtt.broker.dto.TopicResolveDto;
import org.thingsboard.mqtt.broker.service.subscription.EntitySubscription;
import org.thingsboard.mqtt.broker.service.subscription.ValueWithTopicFilter;
import org.thingsboard.mqtt.broker.service.subscription.data.EntitySubscriptionType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TopicResolveServiceImplTest {

    static EntitySubscription es(String clientId, int qos, EntitySubscriptionType type, String share) {
        EntitySubscription e = mock(EntitySubscription.class);
        when(e.getClientId()).thenReturn(clientId);
        when(e.getQos()).thenReturn(qos);
        when(e.getType()).thenReturn(type);
        when(e.getShareName()).thenReturn(share);
        return e;
    }

    @Test
    void groupsByFilter() {
        SubscriptionService ss = mock(SubscriptionService.class);
        List<ValueWithTopicFilter<EntitySubscription>> found = List.of(
                new ValueWithTopicFilter<>(es("c1", 1, EntitySubscriptionType.DEFAULT, null), "s/+/t"),
                new ValueWithTopicFilter<>(es("c2", 1, EntitySubscriptionType.DEFAULT, "g"), "s/+/t"),
                new ValueWithTopicFilter<>(es("i1", 0, EntitySubscriptionType.INTEGRATION, null), "s/#"),
                new ValueWithTopicFilter<>(es("c1", 0, EntitySubscriptionType.DEFAULT, null), "#"));
        when(ss.getSubscriptions("s/r1/t")).thenReturn(found);
        TopicResolveDto r = new TopicResolveServiceImpl(ss).resolve("s/r1/t");
        assertThat(r.getSubscribersTotal()).isEqualTo(4);
        assertThat(r.getClientsTotal()).isEqualTo(2);
        assertThat(r.getMatches().get(0).getTopicFilter()).isEqualTo("s/+/t");
        assertThat(r.getMatches().get(0).getSharedGroups()).isEqualTo(1);
        assertThat(r.getMatches().get(0).getQos()).containsEntry(1, 2);
        assertThat(r.getMatches()).filteredOn(m -> m.getTopicFilter().equals("s/#")).first()
                .extracting(TopicResolveDto.Match::getIntegrations).isEqualTo(1);
    }

    @Test
    void rejectsWildcardsAndEmpty() {
        TopicResolveServiceImpl svc = new TopicResolveServiceImpl(mock(SubscriptionService.class));
        assertThatThrownBy(() -> svc.resolve("a/#")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.resolve("a/+/b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.resolve("")).isInstanceOf(IllegalArgumentException.class);
    }
}
