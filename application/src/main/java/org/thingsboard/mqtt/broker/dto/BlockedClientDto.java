/**
 * Copyright © 2016-2025 The Thingsboard Authors
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

import lombok.Data;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClientType;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.RegexMatchTarget;

import java.util.Comparator;
import java.util.concurrent.TimeUnit;

import static org.thingsboard.mqtt.broker.common.data.util.ComparableUtil.getComparatorBy;

@Data
public class BlockedClientDto {

    private final BlockedClientType type;
    private final long expirationTime;
    private final BlockedClientStatus status;
    private final String description;
    private final String value;
    private final RegexMatchTarget regexMatchTarget;

    public static BlockedClientDto newInstance(BlockedClient blockedClient, int cleanupTtlMinutes) {
        return new BlockedClientDto(
                blockedClient.getType(),
                blockedClient.getExpirationTime(),
                getStatus(blockedClient, cleanupTtlMinutes),
                blockedClient.getDescription(),
                blockedClient.getValue(),
                blockedClient.getRegexMatchTarget()
        );
    }

    private static BlockedClientStatus getStatus(BlockedClient blockedClient, int cleanupTtlMinutes) {
        long expirationTime = blockedClient.getExpirationTime();
        long currentTime = System.currentTimeMillis();
        long ttlMillis = TimeUnit.MINUTES.toMillis(cleanupTtlMinutes);

        if (blockedClient.isExpired()) {
            if ((currentTime - expirationTime) >= ttlMillis / 2) {
                return BlockedClientStatus.DELETING_SOON;
            } else {
                return BlockedClientStatus.EXPIRED;
            }
        } else {
            return BlockedClientStatus.ACTIVE;
        }
    }

    public static Comparator<BlockedClientDto> getComparator(SortOrder sortOrder) {
        return switch (sortOrder.getProperty()) {
            case "type" -> getComparatorBy(sortOrder, BlockedClientDto::getType);
            case "expirationTime" -> getComparatorBy(sortOrder, BlockedClientDto::getExpirationTime);
            case "status" -> getComparatorBy(sortOrder, BlockedClientDto::getStatus);
            case "description" -> getComparatorBy(sortOrder, BlockedClientDto::getDescription);
            case "value" -> getComparatorBy(sortOrder, BlockedClientDto::getValue);
            case "regexMatchTarget" -> getComparatorBy(sortOrder, BlockedClientDto::getRegexMatchTarget);
            default -> null;
        };
    }

}
