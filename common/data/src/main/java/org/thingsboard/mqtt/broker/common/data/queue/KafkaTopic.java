/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.common.data.queue;

import lombok.Data;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;

import java.util.Comparator;
import java.util.function.Function;

@Data
public class KafkaTopic {

    private String name;
    private int partitions;
    private int replicationFactor;
    private long size; // in bytes

    public static Comparator<? super KafkaTopic> sorted(PageLink pageLink) {
        return pageLink.getSortOrder() == null ? (o1, o2) -> 0 :
                Comparator.nullsLast(getComparator(pageLink.getSortOrder()));
    }

    public static Comparator<KafkaTopic> getComparator(SortOrder sortOrder) {
        switch (sortOrder.getProperty()) {
            case "name":
                return getStrComparator(sortOrder.getDirection(), KafkaTopic::getName);
            case "partitions":
                return getIntComparator(sortOrder.getDirection(), KafkaTopic::getPartitions);
            case "replicationFactor":
                return getIntComparator(sortOrder.getDirection(), KafkaTopic::getReplicationFactor);
            case "size":
                return getLongComparator(sortOrder.getDirection(), KafkaTopic::getSize);
            default:
                return null;
        }
    }

    private static Comparator<KafkaTopic> getStrComparator(SortOrder.Direction direction,
                                                           Function<KafkaTopic, String> func) {
        if (direction == SortOrder.Direction.DESC) {
            return Comparator.comparing(func, Comparator.reverseOrder());
        } else {
            return Comparator.comparing(func);
        }
    }

    private static Comparator<KafkaTopic> getIntComparator(SortOrder.Direction direction,
                                                           Function<KafkaTopic, Integer> func) {
        if (direction == SortOrder.Direction.DESC) {
            return Comparator.comparing(func, Comparator.reverseOrder());
        } else {
            return Comparator.comparing(func);
        }
    }

    private static Comparator<KafkaTopic> getLongComparator(SortOrder.Direction direction,
                                                            Function<KafkaTopic, Long> func) {
        if (direction == SortOrder.Direction.DESC) {
            return Comparator.comparing(func, Comparator.reverseOrder());
        } else {
            return Comparator.comparing(func);
        }
    }
}
