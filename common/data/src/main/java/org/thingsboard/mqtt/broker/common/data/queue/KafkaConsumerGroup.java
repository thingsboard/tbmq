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
package org.thingsboard.mqtt.broker.common.data.queue;

import lombok.Data;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.common.data.util.ComparableUtil;

import java.util.Comparator;

import static org.thingsboard.mqtt.broker.common.data.util.ComparableUtil.getComparatorBy;

@Data
public class KafkaConsumerGroup {

    private KafkaConsumerGroupState state;
    private String groupId;
    private int members;
    private long lag;

    public static Comparator<? super KafkaConsumerGroup> sorted(PageLink pageLink) {
        return ComparableUtil.sorted(pageLink, KafkaConsumerGroup::getComparator);
    }

    public static Comparator<KafkaConsumerGroup> getComparator(SortOrder sortOrder) {
        return switch (sortOrder.getProperty()) {
            case "state" -> getComparatorBy(sortOrder, kafkaConsumerGroup -> kafkaConsumerGroup.getState().getName());
            case "groupId" -> getComparatorBy(sortOrder, KafkaConsumerGroup::getGroupId);
            case "members" -> getComparatorBy(sortOrder, KafkaConsumerGroup::getMembers);
            case "lag" -> getComparatorBy(sortOrder, KafkaConsumerGroup::getLag);
            default -> null;
        };
    }

}
