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
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.ConnectionState;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;

import java.util.Comparator;
import java.util.UUID;
import java.util.function.Function;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ShortClientSessionInfoDto {

    private String id;
    private String clientId;
    private ConnectionState connectionState;
    private ClientType clientType;
    private String nodeId;
    private UUID sessionId;
    private int subscriptionsCount;
    private long connectedAt;
    private long disconnectedAt;
    private String clientIpAdr;
    private boolean cleanStart;

    public static Comparator<ShortClientSessionInfoDto> getComparator(SortOrder sortOrder) {
        return switch (sortOrder.getProperty()) {
            case "id", "clientId" -> getStrComparator(sortOrder.getDirection(), ShortClientSessionInfoDto::getClientId);
            case "connectionState" ->
                    getStrComparator(sortOrder.getDirection(), csi -> csi.getConnectionState().name());
            case "clientType" -> getStrComparator(sortOrder.getDirection(), csi -> csi.getClientType().name());
            case "nodeId" -> getStrComparator(sortOrder.getDirection(), ShortClientSessionInfoDto::getNodeId);
            case "subscriptionsCount" ->
                    getIntComparator(sortOrder.getDirection(), ShortClientSessionInfoDto::getSubscriptionsCount);
            case "connectedAt" ->
                    getLongComparator(sortOrder.getDirection(), ShortClientSessionInfoDto::getConnectedAt);
            case "disconnectedAt" ->
                    getLongComparator(sortOrder.getDirection(), ShortClientSessionInfoDto::getDisconnectedAt);
            case "clientIpAdr" -> getStrComparator(sortOrder.getDirection(), ShortClientSessionInfoDto::getClientIpAdr);
            case "cleanStart" -> getBoolComparator(sortOrder.getDirection(), ShortClientSessionInfoDto::isCleanStart);
            default -> null;
        };
    }

    private static Comparator<ShortClientSessionInfoDto> getStrComparator(SortOrder.Direction direction,
                                                                          Function<ShortClientSessionInfoDto, String> func) {
        if (direction == SortOrder.Direction.DESC) {
            return Comparator.comparing(func, Comparator.reverseOrder());
        } else {
            return Comparator.comparing(func);
        }
    }

    private static Comparator<ShortClientSessionInfoDto> getIntComparator(SortOrder.Direction direction,
                                                                          Function<ShortClientSessionInfoDto, Integer> func) {
        if (direction == SortOrder.Direction.DESC) {
            return Comparator.comparing(func, Comparator.reverseOrder());
        } else {
            return Comparator.comparing(func);
        }
    }

    private static Comparator<ShortClientSessionInfoDto> getLongComparator(SortOrder.Direction direction,
                                                                           Function<ShortClientSessionInfoDto, Long> func) {
        if (direction == SortOrder.Direction.DESC) {
            return Comparator.comparing(func, Comparator.reverseOrder());
        } else {
            return Comparator.comparing(func);
        }
    }

    private static Comparator<ShortClientSessionInfoDto> getBoolComparator(SortOrder.Direction direction,
                                                                           Function<ShortClientSessionInfoDto, Boolean> func) {
        if (direction == SortOrder.Direction.DESC) {
            return Comparator.comparing(func, Comparator.reverseOrder());
        } else {
            return Comparator.comparing(func);
        }
    }
}
