/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
import java.util.function.Function;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientSubscriptionInfoDto {

    private String clientId;
    private SubscriptionInfoDto subscription;

    public static Comparator<ClientSubscriptionInfoDto> getComparator(SortOrder sortOrder) {
        return switch (sortOrder.getProperty()) {
            case "clientId" -> getStrComparator(sortOrder.getDirection(), ClientSubscriptionInfoDto::getClientId);
            case "topicFilter" ->
                    getStrComparator(sortOrder.getDirection(), csi -> csi.getSubscription().getTopicFilter());
            case "qos" -> getIntComparator(sortOrder.getDirection(), csi -> csi.getSubscription().getQos().value());
            case "noLocal" ->
                    getBoolComparator(sortOrder.getDirection(), csi -> csi.getSubscription().getOptions().isNoLocal());
            case "retainAsPublish" ->
                    getBoolComparator(sortOrder.getDirection(), csi -> csi.getSubscription().getOptions().isRetainAsPublish());
            case "retainHandling" ->
                    getIntComparator(sortOrder.getDirection(), csi -> csi.getSubscription().getOptions().getRetainHandling());
            case "subscriptionId" ->
                    getIntComparator(sortOrder.getDirection(), csi -> csi.getSubscription().getSubscriptionId());
            default -> null;
        };
    }

    private static Comparator<ClientSubscriptionInfoDto> getStrComparator(SortOrder.Direction direction,
                                                                          Function<ClientSubscriptionInfoDto, String> func) {
        if (direction == SortOrder.Direction.DESC) {
            return Comparator.comparing(func, Comparator.reverseOrder());
        } else {
            return Comparator.comparing(func);
        }
    }

    private static Comparator<ClientSubscriptionInfoDto> getIntComparator(SortOrder.Direction direction,
                                                                          Function<ClientSubscriptionInfoDto, Integer> func) {
        if (direction == SortOrder.Direction.DESC) {
            return Comparator.comparing(func, Comparator.reverseOrder());
        } else {
            return Comparator.comparing(func);
        }
    }

    private static Comparator<ClientSubscriptionInfoDto> getBoolComparator(SortOrder.Direction direction,
                                                                           Function<ClientSubscriptionInfoDto, Boolean> func) {
        if (direction == SortOrder.Direction.DESC) {
            return Comparator.comparing(func, Comparator.reverseOrder());
        } else {
            return Comparator.comparing(func);
        }
    }
}
