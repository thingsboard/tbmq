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
package org.thingsboard.mqtt.broker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;

import java.util.Comparator;

import static org.thingsboard.mqtt.broker.common.data.util.ComparableUtil.getComparatorBy;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientSubscriptionInfoDto {

    private String clientId;
    private SubscriptionInfoDto subscription;

    public static Comparator<ClientSubscriptionInfoDto> getComparator(SortOrder sortOrder) {
        return switch (sortOrder.getProperty()) {
            case "clientId" -> getComparatorBy(sortOrder, ClientSubscriptionInfoDto::getClientId);
            case "topicFilter" -> getComparatorBy(sortOrder, csi -> csi.getSubscription().getTopicFilter());
            case "qos" -> getComparatorBy(sortOrder, csi -> csi.getSubscription().getQos().value());
            case "noLocal" -> getComparatorBy(sortOrder, csi -> csi.getSubscription().isNoLocal());
            case "retainAsPublish" -> getComparatorBy(sortOrder, csi -> csi.getSubscription().isRetainAsPublish());
            case "retainHandling" -> getComparatorBy(sortOrder, csi -> csi.getSubscription().getRetainHandling());
            case "subscriptionId" -> getComparatorBy(sortOrder, csi -> csi.getSubscription().getSubscriptionId());
            default -> null;
        };
    }

}
