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
package org.thingsboard.mqtt.broker.common.data.queue;

import lombok.Data;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.common.data.util.ComparableUtil;

import java.util.Comparator;

import static org.thingsboard.mqtt.broker.common.data.util.ComparableUtil.getComparatorBy;

@Data
public class KafkaTopic {

    private String name;
    private int partitions;
    private int replicationFactor;
    private long size; // in bytes

    public static Comparator<? super KafkaTopic> sorted(PageLink pageLink) {
        return ComparableUtil.sorted(pageLink, KafkaTopic::getComparator);
    }

    public static Comparator<KafkaTopic> getComparator(SortOrder sortOrder) {
        return switch (sortOrder.getProperty()) {
            case "name" -> getComparatorBy(sortOrder, KafkaTopic::getName);
            case "partitions" -> getComparatorBy(sortOrder, KafkaTopic::getPartitions);
            case "replicationFactor" -> getComparatorBy(sortOrder, KafkaTopic::getReplicationFactor);
            case "size" -> getComparatorBy(sortOrder, KafkaTopic::getSize);
            default -> null;
        };
    }

}
