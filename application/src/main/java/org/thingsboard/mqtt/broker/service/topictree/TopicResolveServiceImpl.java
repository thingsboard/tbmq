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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionService;
import org.thingsboard.mqtt.broker.common.data.util.TopicValidator;
import org.thingsboard.mqtt.broker.dto.TopicResolveDto;
import org.thingsboard.mqtt.broker.service.subscription.EntitySubscription;
import org.thingsboard.mqtt.broker.service.subscription.ValueWithTopicFilter;
import org.thingsboard.mqtt.broker.service.subscription.data.EntitySubscriptionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class TopicResolveServiceImpl implements TopicResolveService {

    private final SubscriptionService subscriptionService;

    @Override
    public TopicResolveDto resolve(String topic) {
        TopicValidator.validatePublishTopic(topic);
        List<ValueWithTopicFilter<EntitySubscription>> found = subscriptionService.getSubscriptions(topic);
        Map<String, List<EntitySubscription>> byFilter = new LinkedHashMap<>();
        for (ValueWithTopicFilter<EntitySubscription> v : found) {
            byFilter.computeIfAbsent(v.getTopicFilter(), f -> new ArrayList<>()).add(v.getValue());
        }
        Set<String> allClients = new HashSet<>();
        List<TopicResolveDto.Match> matches = new ArrayList<>(byFilter.size());
        byFilter.forEach((filter, subs) -> {
            Set<String> clients = new HashSet<>();
            Set<String> groups = new HashSet<>();
            Map<Integer, Integer> qos = new TreeMap<>();
            int integrations = 0;
            for (EntitySubscription s : subs) {
                qos.merge(s.getQos(), 1, Integer::sum);
                if (s.getType() == EntitySubscriptionType.INTEGRATION) {
                    integrations++;
                } else {
                    clients.add(s.getClientId());
                }
                if (s.getShareName() != null) {
                    groups.add(s.getShareName());
                }
            }
            allClients.addAll(clients);
            matches.add(new TopicResolveDto.Match(filter, subs.size(), clients.size(), qos, integrations, groups.size()));
        });
        matches.sort(Comparator.comparingInt(TopicResolveDto.Match::getSubscriptions).reversed());
        return new TopicResolveDto(topic, found.size(), allClients.size(), matches);
    }
}
