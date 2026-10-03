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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionService;
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
        if (topic == null || topic.isEmpty() || topic.indexOf('+') >= 0 || topic.indexOf('#') >= 0) {
            throw new IllegalArgumentException("Topic must be a non-empty concrete topic without wildcards");
        }
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
