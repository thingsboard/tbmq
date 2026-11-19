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
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionService;
import org.thingsboard.mqtt.broker.dao.service.DefaultTopicValidationService;
import org.thingsboard.mqtt.broker.dto.TopicResolveDto;
import org.thingsboard.mqtt.broker.exception.DataValidationException;
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
        TopicResolveDto r = new TopicResolveServiceImpl(ss, new DefaultTopicValidationService()).resolve("s/r1/t");
        assertThat(r.getSubscribersTotal()).isEqualTo(4);
        assertThat(r.getClientsTotal()).isEqualTo(2);
        assertThat(r.getMatches().get(0).getTopicFilter()).isEqualTo("s/+/t");
        assertThat(r.getMatches().get(0).getSharedGroups()).isEqualTo(1);
        assertThat(r.getMatches().get(0).getQos()).containsEntry(1, 2);
        assertThat(r.getMatches()).filteredOn(m -> m.getTopicFilter().equals("s/#")).first()
                .extracting(TopicResolveDto.Match::getIntegrations).isEqualTo(1);
    }

    @Test
    void rejectsTopicsThatCannotBePublished() {
        TopicResolveServiceImpl svc = new TopicResolveServiceImpl(mock(SubscriptionService.class), new DefaultTopicValidationService());
        assertThatThrownBy(() -> svc.resolve("a/#")).isInstanceOf(DataValidationException.class);
        assertThatThrownBy(() -> svc.resolve("a/+/b")).isInstanceOf(DataValidationException.class);
        assertThatThrownBy(() -> svc.resolve("")).isInstanceOf(DataValidationException.class);
        assertThatThrownBy(() -> svc.resolve(null)).isInstanceOf(DataValidationException.class);
        assertThatThrownBy(() -> svc.resolve("a\u0000b")).isInstanceOf(DataValidationException.class);
        assertThatThrownBy(() -> svc.resolve("$SYS/a")).isInstanceOf(DataValidationException.class);
    }
}
