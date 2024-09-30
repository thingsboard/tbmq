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
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.ConnectionState;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;

import java.util.Comparator;
import java.util.UUID;

import static org.thingsboard.mqtt.broker.common.data.util.ComparableUtil.getComparatorBy;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
            case "id", "clientId" -> getComparatorBy(sortOrder, ShortClientSessionInfoDto::getClientId);
            case "connectionState" -> getComparatorBy(sortOrder, csi -> csi.getConnectionState().name());
            case "clientType" -> getComparatorBy(sortOrder, csi -> csi.getClientType().name());
            case "nodeId" -> getComparatorBy(sortOrder, ShortClientSessionInfoDto::getNodeId);
            case "subscriptionsCount" -> getComparatorBy(sortOrder, ShortClientSessionInfoDto::getSubscriptionsCount);
            case "connectedAt" -> getComparatorBy(sortOrder, ShortClientSessionInfoDto::getConnectedAt);
            case "disconnectedAt" -> getComparatorBy(sortOrder, ShortClientSessionInfoDto::getDisconnectedAt);
            case "clientIpAdr" -> getComparatorBy(sortOrder, ShortClientSessionInfoDto::getClientIpAdr);
            case "cleanStart" -> getComparatorBy(sortOrder, ShortClientSessionInfoDto::isCleanStart);
            default -> null;
        };
    }

}
