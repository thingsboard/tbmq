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
package org.thingsboard.mqtt.broker.common.data.queue;

import lombok.Data;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;

import java.util.Comparator;
import java.util.function.Function;

@Data
public class KafkaConsumerGroup {

    private KafkaConsumerGroupState state;
    private String groupId;
    private int members;
    private long lag;

    public static Comparator<? super KafkaConsumerGroup> sorted(PageLink pageLink) {
        return pageLink.getSortOrder() == null ? (o1, o2) -> 0 :
                Comparator.nullsLast(getComparator(pageLink.getSortOrder()));
    }

    public static Comparator<KafkaConsumerGroup> getComparator(SortOrder sortOrder) {
        switch (sortOrder.getProperty()) {
            case "state":
                return getStrComparator(sortOrder.getDirection(), kafkaConsumerGroup -> kafkaConsumerGroup.getState().getName());
            case "groupId":
                return getStrComparator(sortOrder.getDirection(), KafkaConsumerGroup::getGroupId);
            case "members":
                return getIntComparator(sortOrder.getDirection(), KafkaConsumerGroup::getMembers);
            case "lag":
                return getLongComparator(sortOrder.getDirection(), KafkaConsumerGroup::getLag);
            default:
                return null;
        }
    }

    private static Comparator<KafkaConsumerGroup> getStrComparator(SortOrder.Direction direction,
                                                                   Function<KafkaConsumerGroup, String> func) {
        if (direction == SortOrder.Direction.DESC) {
            return Comparator.comparing(func, Comparator.reverseOrder());
        } else {
            return Comparator.comparing(func);
        }
    }

    private static Comparator<KafkaConsumerGroup> getIntComparator(SortOrder.Direction direction,
                                                                   Function<KafkaConsumerGroup, Integer> func) {
        if (direction == SortOrder.Direction.DESC) {
            return Comparator.comparing(func, Comparator.reverseOrder());
        } else {
            return Comparator.comparing(func);
        }
    }

    private static Comparator<KafkaConsumerGroup> getLongComparator(SortOrder.Direction direction,
                                                                    Function<KafkaConsumerGroup, Long> func) {
        if (direction == SortOrder.Direction.DESC) {
            return Comparator.comparing(func, Comparator.reverseOrder());
        } else {
            return Comparator.comparing(func);
        }
    }
}
